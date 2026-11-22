package io.netbird.client.tool;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;

/**
 * Reports remote-control state changes from the VPN service itself.
 *
 * <p>A remote command's reply is sent by the receiver, but the service also
 * broadcasts every engine change, so a connected launcher keeps up when the
 * tunnel was started by NetBird's UI, the quick settings tile or always-on
 * rather than by a remote command.
 *
 * <p>The action, permission, extra names and state values are the public
 * contract (see {@code io.netbird.client.control.RemoteControl}). They are
 * repeated here because this module cannot depend on the app module.
 */
public class RemoteStateBroadcast implements ServiceStateListener, ConnectionListener {
    private static final String TAG = "NetBirdRemoteState";

    public static final String ACTION_STATE_CHANGED = "io.netbird.client.action.STATE_CHANGED";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MESSAGE = "message";

    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_CONNECTED = "CONNECTED";
    public static final String STATE_DISCONNECTING = "DISCONNECTING";
    public static final String STATE_DISCONNECTED = "DISCONNECTED";
    public static final String STATE_NEEDS_LOGIN = "NEEDS_LOGIN";
    public static final String STATE_ERROR = "ERROR";

    // Status labels from internal.StatusType on the Go side.
    private static final String STATUS_CONNECTED = "Connected";
    private static final String STATUS_CONNECTING = "Connecting";
    private static final String STATUS_NEEDS_LOGIN = "NeedsLogin";
    private static final String STATUS_SESSION_EXPIRED = "SessionExpired";
    private static final String STATUS_LOGIN_FAILED = "LoginFailed";

    private static final Object LOCK = new Object();
    private static String lastState;
    private static String lastMessage;
    // A remote CONNECT is on its way up. The engine emits Disconnected while
    // the service is created and while an old run tears down; those edges are
    // not an answer to the command.
    private static volatile boolean connecting;
    // The engine reports Connecting once while a tunnel is being torn down.
    // A stop initiated through the binder marks this so that edge is not
    // relayed as the start of a connection.
    private static volatile boolean stopping;

    private final Context context;
    private final BooleanSupplier running;
    private final Supplier<String> status;
    private final BooleanSupplier loginRequired;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Registering a service state listener replays the current state; an
    // initial "stopped" is not a stop.
    private volatile boolean sawStart;
    @Nullable
    private volatile String lastError;

    public RemoteStateBroadcast(Context context,
                                BooleanSupplier running,
                                Supplier<String> status,
                                BooleanSupplier loginRequired) {
        this.context = context.getApplicationContext();
        this.running = running;
        this.status = status;
        this.loginRequired = loginRequired;
    }

    /** Marks a remote connect in flight, so its startup edges are not reported. */
    public static void beginConnect() {
        stopping = false;
        connecting = true;
    }

    public static void endConnect() {
        connecting = false;
    }

    /** Marks a stop initiated through the binder, hiding its tear-down edge. */
    public static void beginStop() {
        stopping = true;
    }

    /**
     * Sends STATE_CHANGED to every app holding the control permission.
     *
     * <p>The dedupe is shared between the service and the receiver's replies:
     * both report the same engine through the same process.
     */
    public static void publish(Context context, String state, @Nullable String message, boolean force) {
        if (STATE_DISCONNECTED.equals(state) && connecting) {
            return;
        }
        if (STATE_CONNECTING.equals(state) && stopping) {
            return;
        }
        synchronized (LOCK) {
            if (!force && state.equals(lastState) && Objects.equals(message, lastMessage)) {
                return;
            }
            lastState = state;
            lastMessage = message;
        }
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, state);
        if (message != null) {
            intent.putExtra(EXTRA_MESSAGE, message);
        }
        context.sendBroadcast(intent, permission(context));
        Log.i(TAG, "state=" + state + (message != null ? " message=" + message : ""));
    }

    /** The permission guarding both the receiver and the state broadcast. */
    public static String permission(Context context) {
        return context.getPackageName() + ".permission.CONTROL_VPN";
    }

    @Override
    public void onStarted() {
        sawStart = true;
        stopping = false;
        lastError = null;
        publish(context, currentState(), null, false);
    }

    @Override
    public void onStopped() {
        if (!sawStart) {
            return;
        }
        sawStart = false;
        stopping = false;
        publishOutcome();
    }

    @Override
    public void onError(String msg) {
        if (loginRequired.getAsBoolean()) {
            lastError = null;
            publish(context, STATE_NEEDS_LOGIN, null, true);
        } else {
            lastError = msg;
            publish(context, STATE_ERROR, msg, true);
        }
    }

    @Override
    public void onStateChanged(long state) {
        // NoNetwork is a display detail; the phase callbacks below carry the
        // state the contract names.
    }

    @Override
    public void onConnecting() {
        if (stopping) {
            return;
        }
        publish(context, STATE_CONNECTING, null, false);
    }

    @Override
    public void onConnected() {
        stopping = false;
        endConnect();
        publish(context, STATE_CONNECTED, null, false);
    }

    @Override
    public void onDisconnecting() {
        publish(context, STATE_DISCONNECTING, null, false);
    }

    @Override
    public void onDisconnected() {
        // Let onError/onStopped explain a failed run before reporting a bare
        // disconnect; onStopped usually lands first and the delayed call is a
        // deduped no-op.
        mainHandler.postDelayed(this::publishEngineOutcome, 250);
    }

    @Override
    public void onAddressChanged(String fqdn, String ip) {
    }

    @Override
    public void onPeersListChanged(long count) {
    }

    private void publishEngineOutcome() {
        if (!sawStart) {
            return;
        }
        publishOutcome();
    }

    private void publishOutcome() {
        stopping = false;
        if (loginRequired.getAsBoolean()) {
            lastError = null;
            publish(context, STATE_NEEDS_LOGIN, null, false);
        } else if (lastError != null) {
            publish(context, STATE_ERROR, lastError, false);
        } else if (!connecting) {
            publish(context, STATE_DISCONNECTED, null, false);
        }
    }

    private String currentState() {
        if (loginRequired.getAsBoolean()) {
            return STATE_NEEDS_LOGIN;
        }
        if (!running.getAsBoolean()) {
            return STATE_DISCONNECTED;
        }
        String current = status.get();
        if (STATUS_CONNECTED.equals(current)) {
            return STATE_CONNECTED;
        }
        if (STATUS_NEEDS_LOGIN.equals(current)
                || STATUS_SESSION_EXPIRED.equals(current)
                || STATUS_LOGIN_FAILED.equals(current)) {
            return STATE_NEEDS_LOGIN;
        }
        // Includes Connecting and a status this build does not know: the run
        // loop is going, so it is not disconnected.
        return STATE_CONNECTING;
    }
}
