package io.netbird.client.control;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The setting is the gate the whole feature hangs on, so its default matters:
 * an app that has never been configured must not accept commands.
 */
public class RemoteControlSettingsTest {

    @Test
    public void aFreshInstallIsDisabled() {
        assertFalse(RemoteControlSettings.isEnabled(new FakePreferences()));
    }

    @Test
    public void onlyAnExplicitEnableTurnsItOn() {
        FakePreferences preferences = new FakePreferences();
        RemoteControlSettings.setEnabled(preferences, true);
        assertTrue(RemoteControlSettings.isEnabled(preferences));

        RemoteControlSettings.setEnabled(preferences, false);
        assertFalse(RemoteControlSettings.isEnabled(preferences));
    }

    @Test
    public void commandsAreRecognisedOnlyByTheirExactNames() {
        assertTrue(RemoteControl.isCommand(RemoteControl.ACTION_CONNECT));
        assertTrue(RemoteControl.isCommand(RemoteControl.ACTION_DISCONNECT));
        assertTrue(RemoteControl.isCommand(RemoteControl.ACTION_TOGGLE));
        assertTrue(RemoteControl.isCommand(RemoteControl.ACTION_GET_STATE));
        assertFalse(RemoteControl.isCommand(RemoteControl.ACTION_STATE_CHANGED));
        assertFalse(RemoteControl.isCommand(null));
    }

    /** In-memory SharedPreferences with the real absent-key semantics. */
    private static final class FakePreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return values;
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value == null ? defValue : (String) value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value == null ? defValues : (Set<String>) value;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value == null ? defValue : (Integer) value;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value == null ? defValue : (Long) value;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value == null ? defValue : (Float) value;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value == null ? defValue : (Boolean) value;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        private final class FakeEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                pending.put(key, values);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                pending.remove(key);
                values.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                values.putAll(pending);
            }
        }
    }
}
