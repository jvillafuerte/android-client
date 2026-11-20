package io.netbird.client.control;

/**
 * The states reported to remote control apps through
 * {@link RemoteControl#ACTION_STATE_CHANGED}.
 *
 * The enum name is the wire value, so these must not be renamed.
 */
public enum RemoteState {
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    NEEDS_LOGIN,
    NEEDS_CONSENT,
    DISABLED,
    ERROR;

    public String wireName() {
        return name();
    }
}
