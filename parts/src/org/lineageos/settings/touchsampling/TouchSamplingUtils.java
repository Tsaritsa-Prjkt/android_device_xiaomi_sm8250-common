/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.touchsampling;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.lineageos.settings.utils.FileUtils;

public final class TouchSamplingUtils {
    private static final String TAG = "TouchSamplingUtils";

    public static final String HTSR_FILE =
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";
    private static final String PREF_FILE = "SHAREDHTSR";
    private static final String PREF_KEY = "SHAREDHTSR";

    private TouchSamplingUtils() {}

    /**
     * High-touch polling is controlled by a sysfs command node. Some kernels expose the
     * node as write-only, so readability must not be used to decide whether it is supported.
     */
    public static boolean isSupported() {
        return FileUtils.isFileWritable(HTSR_FILE);
    }

    public static boolean isEnabled(Context context) {
        if (isSupported()) {
            String value = FileUtils.readOneLine(HTSR_FILE);
            if (value != null) {
                value = value.trim();
                if ("1".equals(value)) return true;
                if ("0".equals(value)) return false;
            }
        }

        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        return storage.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .getInt(PREF_KEY, 0) == 1;
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!isSupported()) {
            Log.w(TAG, "High-touch polling node is not writable: " + HTSR_FILE);
            return false;
        }
        if (!FileUtils.writeLine(HTSR_FILE, enabled ? "1" : "0")) {
            Log.w(TAG, "Failed to write high-touch polling node: " + HTSR_FILE);
            return false;
        }

        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        storage.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
                .putInt(PREF_KEY, enabled ? 1 : 0)
                .apply();
        return true;
    }

    public static void restoreSamplingValue(Context context) {
        if (!isSupported()) return;
        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences prefs = storage.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (!FileUtils.writeLine(HTSR_FILE, prefs.getInt(PREF_KEY, 0) == 1 ? "1" : "0")) {
            Log.w(TAG, "Failed to restore high-touch polling state");
        }
    }
}
