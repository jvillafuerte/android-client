package io.netbird.client.controltest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sends a remote control command without needing this app's UI on screen.
 *
 * <p>The shell cannot hold the custom dangerous permission, so it cannot send
 * the command itself. It broadcasts to this receiver instead (this app has
 * been granted the permission), and this receiver relays the command as its
 * own uid:
 *
 * <pre>
 * adb shell am broadcast -n io.netbird.client.controltest/.CommandReceiver \
 *     -a io.netbird.client.controltest.SEND \
 *     --es action io.netbird.client.action.CONNECT [--es profile default]
 * </pre>
 *
 * <p>That is the closest analogue to a launcher sending the command while it
 * is not in front: no activity of this app is started for it.
 */
public class CommandReceiver extends BroadcastReceiver {
    static final String ACTION_SEND = "io.netbird.client.controltest.SEND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_SEND.equals(intent.getAction())) {
            return;
        }

        String action = intent.getStringExtra("action");
        if (action == null) {
            Log.w(StateReceiver.TAG, "SEND without an action");
            return;
        }

        Intent command = new Intent(action);
        command.setPackage("io.netbird.client");
        command.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        String profile = intent.getStringExtra("profile");
        if (profile != null) {
            command.putExtra("profile", profile);
        }
        context.sendBroadcast(command);
        Log.i(StateReceiver.TAG, "SENT " + action
                + (profile != null ? " profile=" + profile : ""));
    }
}
