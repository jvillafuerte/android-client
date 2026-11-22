package io.netbird.client.controltest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Logs the STATE_CHANGED replies. Keeping this in the manifest means the
 * replies are visible even when the test activity is not on screen, which is
 * what the cold-start cases need.
 */
public class StateReceiver extends BroadcastReceiver {
    static final String TAG = "NetBirdRemoteTest";

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra("state");
        String message = intent.getStringExtra("message");
        String line = "STATE_CHANGED state=" + state
                + (message != null ? " message=" + message : "");
        Log.i(TAG, line);
        MainActivity.append(line);
    }
}
