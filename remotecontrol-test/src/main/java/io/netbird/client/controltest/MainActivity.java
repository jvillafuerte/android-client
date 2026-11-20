package io.netbird.client.controltest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sends the remote control commands and shows the replies.
 *
 * Runnable from adb so the cold-start cases need no UI work:
 *   adb shell am start -n io.netbird.client.controltest/.MainActivity \
 *       --es command CONNECT --es profile default
 */
public class MainActivity extends Activity {
    private static TextView logView;

    private final StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        final EditText profile = new EditText(this);
        profile.setHint("profile (optional, id or name)");
        root.addView(profile);

        addCommand(root, "CONNECT", "io.netbird.client.action.CONNECT", profile);
        addCommand(root, "DISCONNECT", "io.netbird.client.action.DISCONNECT", profile);
        addCommand(root, "TOGGLE", "io.netbird.client.action.TOGGLE", profile);
        addCommand(root, "GET_STATE", "io.netbird.client.action.GET_STATE", profile);

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(13);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setGravity(Gravity.TOP);
        root.addView(logView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        registerReceiver(new StateReceiver(), new android.content.IntentFilter(
                "io.netbird.client.action.STATE_CHANGED"), Context.RECEIVER_EXPORTED);

        Intent intent = getIntent();
        String command = intent == null ? null : intent.getStringExtra("command");
        if (command != null) {
            String action = actionFor(command);
            if (action != null) {
                send(action, intent.getStringExtra("profile"));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String command = intent.getStringExtra("command");
        String action = actionFor(command);
        if (action != null) {
            send(action, intent.getStringExtra("profile"));
        }
    }

    static void append(String line) {
        if (logView == null) {
            return;
        }
        logView.post(() -> logView.append(line + "\n"));
    }

    private static String actionFor(String command) {
        switch (command == null ? "" : command) {
            case "CONNECT":
                return "io.netbird.client.action.CONNECT";
            case "DISCONNECT":
                return "io.netbird.client.action.DISCONNECT";
            case "TOGGLE":
                return "io.netbird.client.action.TOGGLE";
            case "GET_STATE":
                return "io.netbird.client.action.GET_STATE";
            default:
                return null;
        }
    }

    private void addCommand(LinearLayout root, String label, String action, EditText profile) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener((View v) -> {
            String value = profile.getText().toString().trim();
            send(action, value.isEmpty() ? null : value);
        });
        root.addView(button);
    }

    private void send(String action, String profile) {
        Intent intent = new Intent(action);
        // Manifest receivers only see broadcasts addressed to their app on
        // Android 8+, and a force-stopped app only wakes for a broadcast that
        // opts into stopped packages.
        intent.setPackage("io.netbird.client");
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        if (profile != null) {
            intent.putExtra("profile", profile);
        }
        sendBroadcast(intent);
        append("SENT " + action + (profile != null ? " profile=" + profile : ""));
    }
}
