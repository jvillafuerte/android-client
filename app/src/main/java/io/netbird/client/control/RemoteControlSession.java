package io.netbird.client.control;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import io.netbird.client.tool.Profile;
import io.netbird.client.tool.ProfileManagerWrapper;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;

/**
 * Executes remote control commands against the VPN service.
 *
 * One instance per process. A command binds to the service, which is also what
 * creates it on a cold start, then runs through the same binder API the quick
 * settings tile uses: {@code runEngineWithoutAuth()} to connect and
 * {@code stopEngine()} to disconnect. Nothing here starts an activity, a
 * dialog or a login flow.
 *
 * While the engine runs, the binding is kept so every engine state change is
 * relayed to remote apps. Once the engine stops, the binding is dropped and
 * the service is allowed to stop itself.
 */
final class RemoteControlSession implements ServiceConnection {
    private static final String TAG = "NetBirdRemoteControl";
    private static final long BIND_TIMEOUT_MS = 5_000;

    private static RemoteControlSession instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object stateLock = new Object();
    private final ArrayDeque<Command> queue = new ArrayDeque<>();

    private VPNService.MyLocalBinder binder;
    private boolean bound;
    private boolean binding;
    private boolean listenersRegistered;
    private String pendingProfileSwitch;
    private RemoteState lastState;
    private String lastMessage;
    // The engine emits a Connecting event while tearing the tunnel down; a
    // caller that asked to disconnect must not see DISCONNECTING, CONNECTING,
    // DISCONNECTED.
    private volatile boolean disconnecting;
    // Last non-login engine failure. A failed run also reports Disconnected on
    // its way out, and that edge must not erase the error before onStopped.
    @Nullable
    private String lastError;
    // True once this session has seen the engine start. Registering a service
    // state listener replays the current state, and that initial "stopped" is
    // not a stop: handling it would report DISCONNECTED on a GET_STATE or on
    // a command that never started anything.
    private volatile boolean sawStart;
    // A CONNECT is on its way up. The service reports Disconnected once while
    // it is being created and again as a run tears down; neither is a real
    // answer to the command until the run succeeds or fails.
    private volatile boolean connecting;
    private static final long CONNECT_TIMEOUT_MS = 20_000;

    private final Runnable bindTimeout = () -> {
        if (binding) {
            binding = false;
            fail("timed out binding to the VPN service");
        }
    };

    private final Runnable connectTimeout = () -> {
        if (!connecting) {
            return;
        }
        if (binder != null && binder.isRunning()) {
            return;
        }
        connecting = false;
        publish(RemoteState.ERROR,
                "connection attempt ended before the tunnel came up", true);
    };

    static synchronized RemoteControlSession get(Context context) {
        if (instance == null) {
            instance = new RemoteControlSession(context.getApplicationContext());
        }
        return instance;
    }

    private RemoteControlSession(Context context) {
        this.context = context;
    }

    /**
     * Hands a command to the service, keeping its broadcast alive until the
     * command has reached the service. Runs on the main thread.
     */
    void dispatch(@Nullable String action, @Nullable String profile,
                  BroadcastReceiver.PendingResult result) {
        if (!RemoteControlSettings.isEnabled(context)) {
            // Answer before finishing the broadcast's PendingResult: the
            // sender may be a short-lived receiver that would be gone once
            // the result is finished.
            publish(RemoteState.DISABLED, null, true);
            result.finish();
            return;
        }
        if (!RemoteControl.isCommand(action)) {
            result.finish();
            return;
        }

        synchronized (queue) {
            queue.add(new Command(action, profile, result));
        }

        if (binder != null) {
            executeQueued();
        } else {
            bind();
        }
    }

    private void bind() {
        if (bound || binding) {
            return;
        }
        binding = true;
        boolean started;
        try {
            started = context.bindService(
                    new Intent(context, VPNService.class), this, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            Log.w(TAG, "bindService failed", e);
            started = false;
        }
        if (!started) {
            binding = false;
            fail("could not bind to the VPN service");
            return;
        }
        // Hold the binding from here, not from onServiceConnected: if the
        // connection never arrives, the timeout path still has to release it.
        bound = true;
        mainHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "service connected");
        binder = (VPNService.MyLocalBinder) service;
        bound = true;
        binding = false;
        mainHandler.removeCallbacks(bindTimeout);

        binder.addServiceStateListener(serviceStateListener);
        binder.addConnectionObserver(connectionObserver);
        listenersRegistered = true;

        executeQueued();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "service disconnected");
        listenerCleanup();
        binder = null;
        bound = false;
        binding = false;
    }

    private void executeQueued() {
        while (true) {
            Command command;
            synchronized (queue) {
                command = queue.poll();
            }
            if (command == null) {
                return;
            }
            try {
                execute(command);
            } catch (RuntimeException e) {
                Log.e(TAG, "command failed: " + command.action, e);
                publish(RemoteState.ERROR, "command failed: " + e.getMessage(), true);
            }
            command.finish();
        }
    }

    private void execute(Command command) {
        if (binder == null) {
            publish(RemoteState.ERROR, "VPN service unavailable", true);
            return;
        }
        switch (command.action) {
            case RemoteControl.ACTION_GET_STATE:
                publish(currentState(), null, true);
                unbindIfIdle();
                break;
            case RemoteControl.ACTION_CONNECT:
                connect(command.profile);
                break;
            case RemoteControl.ACTION_DISCONNECT:
                disconnect();
                break;
            case RemoteControl.ACTION_TOGGLE:
                if (binder.isRunning()) {
                    disconnect();
                } else {
                    connect(command.profile);
                }
                break;
            default:
                break;
        }
    }

    private void connect(@Nullable String profileName) {
        disconnecting = false;
        lastError = null;
        // Consent is the user's, not the caller's: refuse to raise a system
        // dialog and let the launcher open NetBird instead.
        if (VpnService.prepare(context) != null) {
            publish(RemoteState.NEEDS_CONSENT, null, true);
            unbindIfIdle();
            return;
        }

        // The extra is optional, so an empty string is an absent profile, not
        // a request for a profile literally named "".
        String wanted = profileName == null || profileName.trim().isEmpty()
                ? null : profileName.trim();
        Profile target = findProfile(wanted);
        if (wanted != null && target == null) {
            publish(RemoteState.ERROR, "profile not found: " + wanted, true);
            unbindIfIdle();
            return;
        }

        if (target != null && !target.isActive()) {
            if (binder.isRunning()) {
                // Switching profiles reads files the engine is using, so stop
                // first and continue from onEngineStopped.
                pendingProfileSwitch = target.getID();
                publish(RemoteState.CONNECTING, null, true);
                beginConnect();
                binder.stopEngine();
                return;
            }
            if (!switchProfile(target)) {
                return;
            }
        }

        if (binder.isRunning()) {
            publish(currentState(), null, true);
            unbindIfIdle();
            return;
        }

        beginConnect();
        startEngine();
    }

    private void beginConnect() {
        connecting = true;
        mainHandler.removeCallbacks(connectTimeout);
        mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
    }

    private void endConnect() {
        connecting = false;
        mainHandler.removeCallbacks(connectTimeout);
    }

    private boolean switchProfile(Profile target) {
        try {
            // The engine is down here: either it never ran, or it was stopped
            // and this resumed from onEngineStopped. The stopping variant of
            // this call broadcasts a stop that would arrive after the new run
            // started and kill it.
            new ProfileManagerWrapper(context).switchProfileWhileStopped(target.getID());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "could not switch profile", e);
            publish(RemoteState.ERROR, "could not switch profile: " + e.getMessage(), true);
            unbindIfIdle();
            return false;
        }
    }

    private void startEngine() {
        publish(RemoteState.CONNECTING, null, true);
        Intent start = new Intent(context, VPNService.class)
                .setAction(VPNService.INTENT_ACTION_START);
        try {
            context.startForegroundService(start);
            binder.runEngineWithoutAuth();
        } catch (RuntimeException e) {
            // Android 12+ refuses a foreground-service start from the
            // background unless an exemption applies. A VPN app the user has
            // consented to holds OP_ACTIVATE_VPN, which the platform counts as
            // one; reaching here means this install is not exempt (consent
            // revoked, or an OEM blocking it), so report that rather than
            // pretending the tunnel is coming up.
            Log.e(TAG, "foreground service start refused", e);
            publish(RemoteState.ERROR, startRefusedMessage(e), true);
            unbindIfIdle();
        }
    }

    private static String startRefusedMessage(RuntimeException e) {
        if (isForegroundServiceStartNotAllowed(e)) {
            return "Android refused the background start "
                    + "(ForegroundServiceStartNotAllowedException); allow NetBird "
                    + "unrestricted battery use or grant VPN consent";
        }
        return "could not start the VPN service: " + e.getMessage();
    }

    private static boolean isForegroundServiceStartNotAllowed(Throwable e) {
        // Compared by name because the exception class only exists on API 31+,
        // and this app runs on 26+.
        return "android.app.ForegroundServiceStartNotAllowedException".equals(e.getClass().getName());
    }

    private void disconnect() {
        endConnect();
        // Emit the transition even when the engine is already down: callers
        // are promised DISCONNECTING followed by DISCONNECTED, including for
        // the cold case where the process (and the tunnel) was gone.
        publish(RemoteState.DISCONNECTING, null, true);
        if (!binder.isRunning()) {
            publish(RemoteState.DISCONNECTED, null, true);
            unbindIfIdle();
            return;
        }
        disconnecting = true;
        binder.stopEngine();
    }

    @Nullable
    private Profile findProfile(@Nullable String nameOrId) {
        if (nameOrId == null || nameOrId.trim().isEmpty()) {
            return null;
        }
        String wanted = nameOrId.trim();
        List<Profile> profiles = new ProfileManagerWrapper(context).listProfiles();
        for (Profile profile : profiles) {
            if (wanted.equals(profile.getID())) {
                return profile;
            }
        }
        for (Profile profile : profiles) {
            if (wanted.equalsIgnoreCase(profile.getName())) {
                return profile;
            }
        }
        return null;
    }

    private RemoteState currentState() {
        boolean consentRequired = VpnService.prepare(context) != null;
        boolean running = binder != null && binder.isRunning();
        boolean loginRequired = binder != null && binder.isLoginRequired();
        String status = running ? binder.status() : null;
        return RemoteStateMapper.map(consentRequired, running, status, loginRequired);
    }

    /**
     * Drops the binding once there is nothing left to watch. Called from the
     * main thread and from engine callbacks.
     */
    private void unbindIfIdle() {
        if (binder != null && binder.isRunning()) {
            return;
        }
        if (pendingProfileSwitch != null || binding || !bound) {
            return;
        }
        Log.d(TAG, "unbinding; engine is idle");
        listenerCleanup();
        try {
            context.unbindService(this);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "service was not bound", e);
        }
        bound = false;
        binder = null;
    }

    private void listenerCleanup() {
        if (!listenersRegistered || binder == null) {
            return;
        }
        binder.removeServiceStateListener(serviceStateListener);
        binder.removeConnectionObserver(connectionObserver);
        listenersRegistered = false;
    }

    private void fail(String message) {
        publish(RemoteState.ERROR, message, true);
        finishQueued();
        unbindIfIdle();
    }

    private void finishQueued() {
        while (true) {
            Command command;
            synchronized (queue) {
                command = queue.poll();
            }
            if (command == null) {
                return;
            }
            command.finish();
        }
    }

    /**
     * Sends STATE_CHANGED to every app holding the control permission. Engine
     * callbacks arrive on Go threads, so the dedupe and the state fields are
     * guarded; sendBroadcast itself is thread-safe.
     */
    private void publish(RemoteState state, @Nullable String message, boolean force) {
        // A connect in flight owns the answer until it succeeds or fails; the
        // service emits a Disconnected edge while it is being created and the
        // old run tears down, and neither means the command was refused.
        if (state == RemoteState.DISCONNECTED && connecting) {
            return;
        }
        synchronized (stateLock) {
            if (!force && state == lastState && Objects.equals(message, lastMessage)) {
                return;
            }
            lastState = state;
            lastMessage = message;
        }
        Intent intent = new Intent(RemoteControl.ACTION_STATE_CHANGED);
        intent.putExtra(RemoteControl.EXTRA_STATE, state.wireName());
        if (message != null) {
            intent.putExtra(RemoteControl.EXTRA_MESSAGE, message);
        }
        context.sendBroadcast(intent, RemoteControl.permission(context));
        Log.i(TAG, "state=" + state.wireName()
                + (message != null ? " message=" + message : ""));
    }

    private final ServiceStateListener serviceStateListener = new ServiceStateListener() {
        @Override
        public void onStarted() {
            sawStart = true;
            mainHandler.post(() -> {
                lastError = null;
                if (!disconnecting) {
                    publish(currentState(), null, false);
                }
            });
        }

        @Override
        public void onStopped() {
            boolean wasRunning = sawStart;
            sawStart = false;
            mainHandler.post(() -> onEngineStopped(wasRunning));
        }

        @Override
        public void onError(String msg) {
            // A run loop that gave up because the peer has to sign in is not
            // a generic engine failure: report what the caller can act on,
            // and leave the browser to the user opening NetBird themselves.
            mainHandler.post(() -> {
                endConnect();
                if (currentState() == RemoteState.NEEDS_LOGIN) {
                    lastError = null;
                    publish(RemoteState.NEEDS_LOGIN, null, true);
                } else {
                    lastError = msg;
                    publish(RemoteState.ERROR, msg, true);
                }
            });
        }
    };

    private void onEngineStopped(boolean wasRunning) {
        boolean userAskedToStop = disconnecting;
        disconnecting = false;
        if (!wasRunning) {
            // The replay from registering the listener, not a stop. A command
            // that is still binding or answering must not be overwritten by
            // the state the service happened to be in before it arrived.
            unbindIfIdle();
            return;
        }
        String switchTo = pendingProfileSwitch;
        if (switchTo != null && binder != null) {
            pendingProfileSwitch = null;
            Profile target = findProfile(switchTo);
            if (target == null) {
                publish(RemoteState.ERROR, "profile disappeared: " + switchTo, true);
                unbindIfIdle();
            } else if (switchProfile(target)) {
                startEngine();
            }
            return;
        }
        publishEngineOutcome(userAskedToStop);
        unbindIfIdle();
    }

    /**
     * Reports how a run ended. A run the caller asked to stop is DISCONNECTED
     * whatever the profile's login or consent state: those belong to the next
     * attempt, not to this one. A run that ended on its own is NEEDS_LOGIN when
     * the session expired, keeps its ERROR when the engine failed, and is
     * DISCONNECTED otherwise.
     */
    private void publishEngineOutcome(boolean userAskedToStop) {
        if (userAskedToStop) {
            endConnect();
            publish(RemoteState.DISCONNECTED, null, false);
            return;
        }
        if (binder != null && binder.isLoginRequired()) {
            endConnect();
            lastError = null;
            publish(RemoteState.NEEDS_LOGIN, null, false);
        } else if (lastError != null) {
            endConnect();
            publish(RemoteState.ERROR, lastError, false);
        } else if (!connecting) {
            publish(RemoteState.DISCONNECTED, null, false);
        }
        // While a connect is in flight, an engine stop without an error is one
        // of the transient edges from startup; the run's own outcome (or the
        // connect timeout) reports the end.
    }

    private final ConnectionListener connectionObserver = new ConnectionListener() {
        @Override
        public void onStateChanged(long state) {
            if (state == Android.ClientStateNoNetwork) {
                publish(RemoteState.CONNECTING, "no network", false);
            }
        }

        @Override
        public void onConnecting() {
            if (disconnecting) {
                return;
            }
            publish(RemoteState.CONNECTING, null, false);
        }

        @Override
        public void onConnected() {
            disconnecting = false;
            endConnect();
            publish(RemoteState.CONNECTED, null, false);
        }

        @Override
        public void onDisconnecting() {
            publish(RemoteState.DISCONNECTING, null, false);
        }

        @Override
        public void onDisconnected() {
            disconnecting = false;
            // Not reported straight away: a failed run also emits Disconnected
            // before onError/onStopped explain why it ended. Let those land
            // first so the caller sees NEEDS_LOGIN or ERROR, not a bare
            // DISCONNECTED that is true for only a few milliseconds.
            mainHandler.postDelayed(() -> {
                // onEngineStopped may have published the outcome and dropped
                // the binding already; nothing is left to correct then.
                if (!bound || disconnecting) {
                    return;
                }
                publishEngineOutcome(false);
            }, 250);
        }

        @Override
        public void onAddressChanged(String fqdn, String ip) {
        }

        @Override
        public void onPeersListChanged(long count) {
        }
    };

    private static final class Command {
        final String action;
        @Nullable
        final String profile;
        private final BroadcastReceiver.PendingResult result;
        private boolean finished;

        Command(String action, @Nullable String profile, BroadcastReceiver.PendingResult result) {
            this.action = action;
            this.profile = profile;
            this.result = result;
        }

        void finish() {
            if (finished) {
                return;
            }
            finished = true;
            try {
                result.finish();
            } catch (RuntimeException e) {
                Log.w(TAG, "broadcast already finished", e);
            }
        }
    }
}
