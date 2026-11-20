package io.netbird.client.control;

import androidx.annotation.Nullable;

/**
 * Translates the engine's run state into the state remote apps see.
 *
 * Kept free of Android and engine types so the mapping can be unit tested. The
 * inputs are the same signals the VPN service's notification uses: whether the
 * run loop is going, its status label, and whether an interactive login is
 * needed.
 */
final class RemoteStateMapper {
    // Status labels from internal.StatusType on the Go side.
    static final String STATUS_CONNECTING = "Connecting";
    static final String STATUS_CONNECTED = "Connected";
    static final String STATUS_NEEDS_LOGIN = "NeedsLogin";
    static final String STATUS_SESSION_EXPIRED = "SessionExpired";
    static final String STATUS_LOGIN_FAILED = "LoginFailed";

    /**
     * Consent and login are reported ahead of the run state: both mean the
     * caller has to open NetBird to resolve them, and neither is a transient
     * phase of a connection that is under way.
     *
     * A running engine with no status yet is reported as CONNECTING rather
     * than DISCONNECTED: the run loop has been told to go, the tunnel just
     * has not announced a phase.
     */
    static RemoteState map(boolean consentRequired, boolean engineRunning,
                           @Nullable String status, boolean loginRequired) {
        if (consentRequired) {
            return RemoteState.NEEDS_CONSENT;
        }
        if (loginRequired || isLoginStatus(status)) {
            return RemoteState.NEEDS_LOGIN;
        }
        if (!engineRunning) {
            return RemoteState.DISCONNECTED;
        }
        if (STATUS_CONNECTED.equals(status)) {
            return RemoteState.CONNECTED;
        }
        if (STATUS_CONNECTING.equals(status) || status == null || status.isEmpty()) {
            return RemoteState.CONNECTING;
        }
        // A status this app does not know (a newer engine label) still means the
        // engine is running, so do not report DISCONNECTED.
        return RemoteState.CONNECTING;
    }

    private static boolean isLoginStatus(@Nullable String status) {
        return STATUS_NEEDS_LOGIN.equals(status)
                || STATUS_SESSION_EXPIRED.equals(status)
                || STATUS_LOGIN_FAILED.equals(status);
    }

    private RemoteStateMapper() {
    }
}
