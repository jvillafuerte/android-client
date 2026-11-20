package io.netbird.client.control;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * The remote control contract: the actions a permission holder sends, the
 * extras it reads, and the broadcast it can observe.
 *
 * Modelled on the WireGuard Android client, which exposes a
 * CONTROL_TUNNELS-protected receiver plus an in-app opt-in. The names here are
 * part of the public surface: launchers code against them, so renaming any of
 * them breaks callers.
 */
public final class RemoteControl {
    /** Suffix of the dangerous permission a remote app must be granted. */
    static final String PERMISSION_SUFFIX = ".permission.CONTROL_VPN";

    public static final String ACTION_CONNECT = "io.netbird.client.action.CONNECT";
    public static final String ACTION_DISCONNECT = "io.netbird.client.action.DISCONNECT";
    public static final String ACTION_TOGGLE = "io.netbird.client.action.TOGGLE";
    public static final String ACTION_GET_STATE = "io.netbird.client.action.GET_STATE";

    /** Sent to permission holders after every command and engine state change. */
    public static final String ACTION_STATE_CHANGED = "io.netbird.client.action.STATE_CHANGED";

    /** String extra on CONNECT naming the profile to switch to before connecting. */
    public static final String EXTRA_PROFILE = "profile";
    /** String extra on STATE_CHANGED carrying a {@link RemoteState} name. */
    public static final String EXTRA_STATE = "state";
    /** Optional string extra on STATE_CHANGED with a human-readable ERROR detail. */
    public static final String EXTRA_MESSAGE = "message";

    /**
     * The permission guarding the receiver and the state broadcast. Built from
     * the package name, so a private build with a different applicationId
     * exposes its own permission rather than shares one with the store build.
     */
    public static String permission(Context context) {
        return context.getPackageName() + PERMISSION_SUFFIX;
    }

    static boolean isCommand(@Nullable String action) {
        return ACTION_CONNECT.equals(action)
                || ACTION_DISCONNECT.equals(action)
                || ACTION_TOGGLE.equals(action)
                || ACTION_GET_STATE.equals(action);
    }

    private RemoteControl() {
    }
}
