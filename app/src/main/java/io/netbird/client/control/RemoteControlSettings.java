package io.netbird.client.control;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The "Allow remote control apps" switch. Off unless the user turned it on:
 * an install that has never seen the setting must not accept commands.
 */
public final class RemoteControlSettings {
    private static final String PREFERENCES = "remote_control";
    private static final String KEY_ALLOWED = "allow_remote_control";

    public static boolean isEnabled(Context context) {
        return isEnabled(preferences(context));
    }

    static boolean isEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(KEY_ALLOWED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        setEnabled(preferences(context), enabled);
    }

    static void setEnabled(SharedPreferences preferences, boolean enabled) {
        preferences.edit().putBoolean(KEY_ALLOWED, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private RemoteControlSettings() {
    }
}
