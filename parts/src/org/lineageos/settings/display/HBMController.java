/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.lineageos.settings.utils.FileUtils;

public final class HBMController {
    private static final String PREF_SNAPSHOT_VALID = "hbm_brightness_snapshot_valid";
    private static final String PREF_SCREEN_BRIGHTNESS = "hbm_previous_screen_brightness";
    private static final String PREF_BACKLIGHT_BRIGHTNESS = "hbm_previous_backlight_brightness";

    private HBMController() {}

    public static boolean enable(Context context, SharedPreferences prefs) {
        if (!isSupported()) return false;
        return HBMControllerCore.enable(new AndroidBackend(context, prefs));
    }

    public static boolean disable(Context context, SharedPreferences prefs) {
        // Always run the restore path even if the HBM node disappeared temporarily.
        return HBMControllerCore.disable(new AndroidBackend(context, prefs));
    }

    public static boolean isSupported() {
        return FileUtils.isFileWritable(DisplayNodes.getHbmNode())
                && FileUtils.isFileWritable(DisplayNodes.getBacklight())
                && FileUtils.isFileReadable(DisplayNodes.getBacklightMax());
    }

    private static final class AndroidBackend implements HBMControllerCore.Backend {
        private final Context mContext;
        private final SharedPreferences mPrefs;

        AndroidBackend(Context context, SharedPreferences prefs) {
            mContext = context.getApplicationContext();
            mPrefs = prefs;
        }

        @Override
        public boolean hasSnapshot() {
            return mPrefs.getBoolean(PREF_SNAPSHOT_VALID, false);
        }

        @Override
        public int readScreenBrightness() {
            return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
        }

        @Override
        public String readBacklightBrightness() {
            return FileUtils.readOneLine(DisplayNodes.getBacklight());
        }

        @Override
        public String readMaxBacklightBrightness() {
            return FileUtils.readOneLine(DisplayNodes.getBacklightMax());
        }

        @Override
        public void saveSnapshot(int screenBrightness, String backlightBrightness) {
            SharedPreferences.Editor editor = mPrefs.edit().putBoolean(PREF_SNAPSHOT_VALID, true);
            if (screenBrightness >= 0) {
                editor.putInt(PREF_SCREEN_BRIGHTNESS, screenBrightness);
            } else {
                editor.remove(PREF_SCREEN_BRIGHTNESS);
            }
            if (backlightBrightness != null && !backlightBrightness.isEmpty()) {
                editor.putString(PREF_BACKLIGHT_BRIGHTNESS, backlightBrightness);
            } else {
                editor.remove(PREF_BACKLIGHT_BRIGHTNESS);
            }
            editor.commit();
        }

        @Override
        public int getSavedScreenBrightness() {
            return mPrefs.getInt(PREF_SCREEN_BRIGHTNESS, -1);
        }

        @Override
        public String getSavedBacklightBrightness() {
            return mPrefs.getString(PREF_BACKLIGHT_BRIGHTNESS, null);
        }

        @Override
        public void clearSnapshot() {
            mPrefs.edit()
                    .remove(PREF_SNAPSHOT_VALID)
                    .remove(PREF_SCREEN_BRIGHTNESS)
                    .remove(PREF_BACKLIGHT_BRIGHTNESS)
                    .commit();
        }

        @Override
        public boolean writeHbm(boolean enabled) {
            return FileUtils.writeLine(DisplayNodes.getHbmNode(), enabled ? "1" : "0");
        }

        @Override
        public boolean writeBacklight(String value) {
            return FileUtils.writeLine(DisplayNodes.getBacklight(), value);
        }

        @Override
        public boolean writeScreenBrightness(int value) {
            return Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value);
        }
    }
}
