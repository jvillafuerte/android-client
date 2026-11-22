package io.netbird.client.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The exported entry point for remote control apps.
 *
 * {@code android:permission} in the manifest keeps the platform from
 * delivering anything from an app without the control permission, so there is
 * no sender check to get wrong here. Commands are handled through
 * {@code goAsync()} because a cold start has to bind the VPN service before it
 * can answer, which outlives {@code onReceive}.
 */
public class RemoteControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!RemoteControl.isCommand(action)) {
            return;
        }
        PendingResult result = goAsync();
        RemoteControlSession.get(context).dispatch(
                action, intent.getStringExtra(RemoteControl.EXTRA_PROFILE), result);
    }
}
