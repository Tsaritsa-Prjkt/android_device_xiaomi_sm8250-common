/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

/** State-safe owner for DC dimming and HBM. */
public final class DisplayUtils {
    private static final String TAG = "DisplayUtils";
    private static final String PREF_HBM_PREVIOUS_BRIGHTNESS = "hbm_previous_brightness";

    private DisplayUtils() {}

    private static SharedPreferences prefs(Context context) {
        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(storage);
    }

    public static boolean isDcDimmingSupported() {
        String node = DisplayNodes.getDcDimmingNode();
        return FileUtils.isFileReadable(node) && FileUtils.isFileWritable(node);
    }

    public static boolean isHbmSupported() {
        return FileUtils.isFileWritable(DisplayNodes.getHbmNode())
                && FileUtils.isFileWritable(DisplayNodes.getBacklight())
                && FileUtils.isFileReadable(DisplayNodes.getBacklightMax());
    }

    public static boolean isDcDimmingEnabled() {
        String value = FileUtils.readOneLine(DisplayNodes.getDcDimmingNode());
        if (value == null) return false;
        value = value.trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * Reads HBM from a direct state node when available. On the Xiaomi disp_param command ABI,
     * the node is not a reliable state source, so fall back to the last successful request.
     */
    public static boolean isHbmEnabled(Context context) {
        if (!DisplayNodes.usesHbmCommandAbi()) {
            String value = FileUtils.readOneLine(DisplayNodes.getHbmNode());
            if (value != null) {
                value = value.trim();
                if ("1".equals(value) || "true".equalsIgnoreCase(value)) return true;
                if ("0".equals(value) || "false".equalsIgnoreCase(value)) return false;
            }
        }
        return prefs(context).getBoolean(DisplayNodes.getHbmEnableKey(), false);
    }

    public static boolean setDcDimming(Context context, boolean enabled) {
        if (!isDcDimmingSupported()) return false;

        // HBM and DC dimming are mutually exclusive on this panel stack.
        if (enabled && isHbmSupported() && isHbmEnabled(context)) {
            if (!setHbmInternal(context, false, true)) return false;
        }

        if (!FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), enabled ? "1" : "0")) {
            return false;
        }
        prefs(context).edit()
                .putBoolean(DisplayNodes.getDcDimmingEnableKey(), enabled)
                .apply();
        return true;
    }

    public static boolean setHbm(Context context, boolean enabled) {
        return setHbmInternal(context, enabled, true);
    }

    /** Used by Auto HBM without changing the user's manual HBM boot request. */
    public static boolean setHbmTemporary(Context context, boolean enabled) {
        return setHbmInternal(context, enabled, false);
    }

    private static boolean setHbmInternal(Context context, boolean enabled, boolean persistRequest) {
        if (!isHbmSupported()) return false;
        SharedPreferences sharedPrefs = prefs(context);

        if (enabled) {
            if (isDcDimmingSupported() && isDcDimmingEnabled()) {
                if (!FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), "0")) return false;
                sharedPrefs.edit().putBoolean(DisplayNodes.getDcDimmingEnableKey(), false).apply();
            }

            boolean hadPreviousBrightness = sharedPrefs.contains(PREF_HBM_PREVIOUS_BRIGHTNESS);
            if (!hadPreviousBrightness) {
                int previousBrightness = Settings.System.getInt(
                        context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
                sharedPrefs.edit().putInt(PREF_HBM_PREVIOUS_BRIGHTNESS, previousBrightness).apply();
            }

            String max = FileUtils.readOneLine(DisplayNodes.getBacklightMax());
            if (max == null || max.trim().isEmpty()) {
                if (!hadPreviousBrightness) {
                    sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
                }
                return false;
            }

            if (!FileUtils.writeLine(DisplayNodes.getHbmNode(), DisplayNodes.getHbmEnableValue())) {
                if (!hadPreviousBrightness) {
                    sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
                }
                return false;
            }

            if (!FileUtils.writeLine(DisplayNodes.getBacklight(), max.trim())) {
                FileUtils.writeLine(DisplayNodes.getHbmNode(), DisplayNodes.getHbmDisableValue());
                if (!hadPreviousBrightness) {
                    sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
                }
                return false;
            }

            if (!Settings.System.putInt(
                    context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255)) {
                FileUtils.writeLine(DisplayNodes.getHbmNode(), DisplayNodes.getHbmDisableValue());
                if (!hadPreviousBrightness) {
                    sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
                }
                return false;
            }
        } else {
            if (!FileUtils.writeLine(DisplayNodes.getHbmNode(), DisplayNodes.getHbmDisableValue())) {
                return false;
            }
            if (sharedPrefs.contains(PREF_HBM_PREVIOUS_BRIGHTNESS)) {
                int previous = sharedPrefs.getInt(PREF_HBM_PREVIOUS_BRIGHTNESS, 255);
                Settings.System.putInt(
                        context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, previous);
                sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
            }
        }

        if (persistRequest) {
            sharedPrefs.edit().putBoolean(DisplayNodes.getHbmEnableKey(), enabled).apply();
        }
        return true;
    }

    public static void restore(Context context) {
        SharedPreferences sharedPrefs = prefs(context);

        if (isDcDimmingSupported()) {
            boolean enabled = sharedPrefs.getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
            if (!FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), enabled ? "1" : "0")) {
                Log.w(TAG, "Cannot restore DC dimming");
            }
        }

        if (isHbmSupported()) {
            boolean enabled = sharedPrefs.getBoolean(DisplayNodes.getHbmEnableKey(), false);
            if (enabled) {
                if (!setHbmInternal(context, true, true)) {
                    Log.w(TAG, "Cannot restore HBM");
                }
            } else if (!FileUtils.writeLine(
                    DisplayNodes.getHbmNode(), DisplayNodes.getHbmDisableValue())) {
                Log.w(TAG, "Cannot restore HBM off state");
            }
        }
    }
}
