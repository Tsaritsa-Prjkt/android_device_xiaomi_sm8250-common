/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public final class DisplayUtils {
    private static final String TAG = "DisplayUtils";

    private DisplayUtils() {}

    public static boolean isDcDimmingSupported() {
        return FileUtils.isFileWritable(DisplayNodes.getDcDimmingNode());
    }

    public static boolean isHbmSupported() {
        return HBMController.isSupported();
    }

    public static boolean isAutoHbmSupported(Context context) {
        if (!isHbmSupported()) return false;
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        return sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    public static boolean isDcDimmingEnabled() {
        return "1".equals(FileUtils.readOneLine(DisplayNodes.getDcDimmingNode()));
    }

    public static boolean isHbmEnabled() {
        return "1".equals(FileUtils.readOneLine(DisplayNodes.getHbmNode()));
    }

    public static boolean setDcDimming(Context context, boolean enabled) {
        if (!isDcDimmingSupported()) return false;
        if (enabled && isHbmEnabled() && !setHbm(context, false)) {
            return false;
        }
        if (!FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), enabled ? "1" : "0")) {
            return false;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DisplayNodes.getDcDimmingEnableKey(), enabled).apply();
        return true;
    }

    public static boolean setHbm(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean applied = enabled
                ? HBMController.enable(context, prefs)
                : HBMController.disable(context, prefs);
        if (!applied) return false;

        prefs.edit().putBoolean(DisplayNodes.getHbmEnableKey(), enabled).apply();
        return true;
    }

    public static void restore(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (isDcDimmingSupported()) {
            boolean enabled = prefs.getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
            if (!FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), enabled ? "1" : "0")) {
                Log.w(TAG, "Cannot restore DC dimming");
            }
        }

        boolean autoEnabled = prefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false);
        if (autoEnabled && isAutoHbmSupported(context)) {
            // Auto mode owns HBM while enabled. Never restore a stale manual state.
            prefs.edit().putBoolean(DisplayNodes.getHbmEnableKey(), false).apply();
            HBMController.disable(context, prefs);
            if (AutoHBMService.start(context)) {
                return;
            }
            Log.w(TAG, "Cannot start Auto HBM; disabling the saved Auto HBM state");
            prefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
        }

        if (autoEnabled) {
            prefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
            AutoHBMService.stop(context);
        }

        if (isHbmSupported()) {
            boolean enabled = prefs.getBoolean(DisplayNodes.getHbmEnableKey(), false);
            if (!setHbm(context, enabled)) {
                Log.w(TAG, "Cannot restore HBM state");
            }
        } else {
            HBMController.disable(context, prefs);
        }
    }
}
