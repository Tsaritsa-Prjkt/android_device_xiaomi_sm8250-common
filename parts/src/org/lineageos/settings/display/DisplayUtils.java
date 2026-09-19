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
    private static final String PREF_HBM_RUNTIME_STATE = "hbm_runtime_state";

    private DisplayUtils() {}

    private static SharedPreferences prefs(Context context) {
        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(storage);
    }

    /**
     * A number of display sysfs controls are write-only. Writable is therefore the
     * capability contract; requiring readability can incorrectly disable a valid control.
     */
    public static boolean isDcDimmingSupported() {
        return FileUtils.isFileWritable(DisplayNodes.getDcDimmingNode());
    }

    /**
     * The HBM control node is the actual capability. Direct backlight writes are an
     * optional brightness boost: Android's framework brightness path is still used when
     * the raw backlight node is not writable on a newer kernel/SELinux policy.
     */
    public static boolean isHbmSupported() {
        return FileUtils.isFileWritable(DisplayNodes.getHbmNode());
    }

    public static boolean isDcDimmingEnabled(Context context) {
        Boolean kernelState = parseBinaryState(FileUtils.readOneLine(DisplayNodes.getDcDimmingNode()));
        if (kernelState != null) return kernelState;
        return prefs(context).getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
    }

    /**
     * Reads HBM from a direct state node when available. The Xiaomi disp_param ABI is a
     * command endpoint rather than a reliable state endpoint, so use the last successful
     * hardware request for that ABI (and for write-only direct nodes).
     */
    public static boolean isHbmEnabled(Context context) {
        if (!DisplayNodes.usesHbmCommandAbi()) {
            Boolean kernelState = parseBinaryState(FileUtils.readOneLine(DisplayNodes.getHbmNode()));
            if (kernelState != null) return kernelState;
        }
        return prefs(context).getBoolean(PREF_HBM_RUNTIME_STATE,
                prefs(context).getBoolean(DisplayNodes.getHbmEnableKey(), false));
    }

    /** Manual HBM request persisted by the user. Auto-HBM must never override it. */
    public static boolean isHbmManuallyRequested(Context context) {
        return prefs(context).getBoolean(DisplayNodes.getHbmEnableKey(), false);
    }

    public static boolean setDcDimming(Context context, boolean enabled) {
        if (!isDcDimmingSupported()) {
            Log.w(TAG, "DC dimming unavailable: " + DisplayNodes.getDcDimmingNode());
            return false;
        }

        // HBM and DC dimming are mutually exclusive on this panel stack.
        if (enabled && isHbmSupported() && isHbmEnabled(context)) {
            if (!setHbmInternal(context, false, true)) return false;
        }

        String node = DisplayNodes.getDcDimmingNode();
        if (!FileUtils.writeLine(node, enabled ? "1" : "0")) {
            Log.w(TAG, "Failed to write DC dimming node: " + node);
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

    /**
     * Used by Auto HBM without changing the user's manual HBM boot request. A temporary
     * OFF request is ignored while manual HBM is requested so Auto HBM can never switch
     * off a mode the user explicitly enabled.
     */
    public static boolean setHbmTemporary(Context context, boolean enabled) {
        if (!enabled && isHbmManuallyRequested(context)) return true;
        return setHbmInternal(context, enabled, false);
    }

    private static boolean setHbmInternal(Context context, boolean enabled, boolean persistRequest) {
        if (!isHbmSupported()) {
            Log.w(TAG, "HBM unavailable: " + DisplayNodes.getHbmNode());
            return false;
        }

        SharedPreferences sharedPrefs = prefs(context);

        if (enabled) {
            if (isDcDimmingSupported() && isDcDimmingEnabled(context)) {
                String dcNode = DisplayNodes.getDcDimmingNode();
                if (!FileUtils.writeLine(dcNode, "0")) {
                    Log.w(TAG, "Cannot disable DC dimming before HBM: " + dcNode);
                    return false;
                }
                sharedPrefs.edit()
                        .putBoolean(DisplayNodes.getDcDimmingEnableKey(), false)
                        .apply();
            }

            boolean savedPreviousBrightnessNow = false;
            if (!sharedPrefs.contains(PREF_HBM_PREVIOUS_BRIGHTNESS)) {
                int previousBrightness = Settings.System.getInt(
                        context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
                sharedPrefs.edit()
                        .putInt(PREF_HBM_PREVIOUS_BRIGHTNESS, previousBrightness)
                        .apply();
                savedPreviousBrightnessNow = true;
            }

            if (!writeHbmControl(true)) {
                if (savedPreviousBrightnessNow) {
                    sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
                }
                return false;
            }
            applyMaximumBrightnessBestEffort(context);
        } else {
            if (!writeHbmControl(false)) return false;
            restorePreviousBrightnessBestEffort(context, sharedPrefs);
        }

        SharedPreferences.Editor editor = sharedPrefs.edit()
                .putBoolean(PREF_HBM_RUNTIME_STATE, enabled);
        if (persistRequest) {
            editor.putBoolean(DisplayNodes.getHbmEnableKey(), enabled);
        }
        editor.apply();
        return true;
    }

    private static boolean writeHbmControl(boolean enabled) {
        String node = DisplayNodes.getHbmNode();
        String value = enabled
                ? DisplayNodes.getHbmEnableValue()
                : DisplayNodes.getHbmDisableValue();
        if (FileUtils.writeLine(node, value)) return true;
        Log.w(TAG, "Failed to write HBM node " + node + " value=" + value);
        return false;
    }

    private static void applyMaximumBrightnessBestEffort(Context context) {
        String max = FileUtils.readOneLine(DisplayNodes.getBacklightMax());
        if (max != null) max = max.trim();

        if (max != null && !max.isEmpty() && FileUtils.isFileWritable(DisplayNodes.getBacklight())) {
            if (!FileUtils.writeLine(DisplayNodes.getBacklight(), max)) {
                Log.w(TAG, "HBM enabled, but direct backlight boost failed");
            }
        }

        if (!Settings.System.putInt(
                context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255)) {
            Log.w(TAG, "HBM enabled, but framework brightness boost failed");
        }
    }

    private static void restorePreviousBrightnessBestEffort(
            Context context, SharedPreferences sharedPrefs) {
        if (!sharedPrefs.contains(PREF_HBM_PREVIOUS_BRIGHTNESS)) return;

        int previous = sharedPrefs.getInt(PREF_HBM_PREVIOUS_BRIGHTNESS, 255);
        if (Settings.System.putInt(
                context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, previous)) {
            sharedPrefs.edit().remove(PREF_HBM_PREVIOUS_BRIGHTNESS).apply();
        } else {
            Log.w(TAG, "HBM disabled, but previous framework brightness could not be restored");
        }
    }

    private static Boolean parseBinaryState(String value) {
        if (value == null) return null;
        value = value.trim();
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
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
            } else if (writeHbmControl(false)) {
                sharedPrefs.edit().putBoolean(PREF_HBM_RUNTIME_STATE, false).apply();
            } else {
                Log.w(TAG, "Cannot restore HBM off state");
            }
        }
    }
}
