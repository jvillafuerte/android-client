package io.netbird.client.control;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The mapping remote apps see must not depend on how the engine reached a
 * state, only on the four signals the VPN service itself has.
 */
public class RemoteStateMapperTest {

    @Test
    public void missingConsentOutranksEverythingElse() {
        assertEquals(RemoteState.NEEDS_CONSENT,
                RemoteStateMapper.map(true, true, "Connected", false));
        assertEquals(RemoteState.NEEDS_CONSENT,
                RemoteStateMapper.map(true, false, null, true));
    }

    @Test
    public void loginOutranksTheRunLoop() {
        assertEquals(RemoteState.NEEDS_LOGIN,
                RemoteStateMapper.map(false, true, "Connected", true));
        assertEquals(RemoteState.NEEDS_LOGIN,
                RemoteStateMapper.map(false, false, null, true));
    }

    @Test
    public void loginStatusLabelsMeanNeedsLogin() {
        for (String status : new String[]{"NeedsLogin", "SessionExpired", "LoginFailed"}) {
            assertEquals(status, RemoteState.NEEDS_LOGIN,
                    RemoteStateMapper.map(false, true, status, false));
        }
    }

    @Test
    public void stoppedEngineIsDisconnected() {
        assertEquals(RemoteState.DISCONNECTED,
                RemoteStateMapper.map(false, false, null, false));
        assertEquals(RemoteState.DISCONNECTED,
                RemoteStateMapper.map(false, false, "Idle", false));
    }

    @Test
    public void runningEngineReportsItsPhase() {
        assertEquals(RemoteState.CONNECTED,
                RemoteStateMapper.map(false, true, "Connected", false));
        assertEquals(RemoteState.CONNECTING,
                RemoteStateMapper.map(false, true, "Connecting", false));
    }

    @Test
    public void runningEngineWithoutAStatusYetIsConnecting() {
        assertEquals(RemoteState.CONNECTING,
                RemoteStateMapper.map(false, true, null, false));
        assertEquals(RemoteState.CONNECTING,
                RemoteStateMapper.map(false, true, "", false));
    }

    @Test
    public void unknownFutureStatusIsNotReportedAsDisconnected() {
        assertEquals(RemoteState.CONNECTING,
                RemoteStateMapper.map(false, true, "SomethingNew", false));
    }
}
