/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.charge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public class ChargeUtils {
    private static final String TAG = "ChargeUtils";

    public static final String BYPASS_CHARGE_NODE = "/sys/class/power_supply/battery/input_suspend";
    private static final String BATTERY_TEMP_NODE = "/sys/class/power_supply/battery/temp";
    private static final String BATTERY_CAPACITY_NODE = "/sys/class/power_supply/battery/capacity";

    private static final int MAX_BATTERY_TEMP = 450; // 45.0 C in tenths
    private static final int MIN_BATTERY_CAPACITY = 20;
    private static final String PREF_BYPASS_CHARGE = "bypass_charge";

    public static final int BYPASS_DISABLED = 0;
    public static final int BYPASS_ENABLED = 1;

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;

    public ChargeUtils(Context context) {
        mContext = context.getApplicationContext();
        Context storage = mContext.createDeviceProtectedStorageContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(storage);
    }

    public boolean isBypassChargeEnabled() {
        return "1".equals(FileUtils.readOneLine(BYPASS_CHARGE_NODE));
    }

    public boolean setBypassChargeEnabled(boolean enable) {
        if (!isBypassChargeSupported()) return false;
        if (enable) {
            SafetyCheckResult safetyCheck = performSafetyChecks();
            if (!safetyCheck.isSafe()) {
                Log.w(TAG, "Safety check failed: " + safetyCheck.getReason());
                return false;
            }
        }
        if (!FileUtils.writeLine(BYPASS_CHARGE_NODE, enable ? "1" : "0")) {
            Log.e(TAG, "Failed to write bypass charge status");
            return false;
        }
        boolean applied = isBypassChargeEnabled() == enable;
        if (applied) {
            mSharedPrefs.edit().putBoolean(PREF_BYPASS_CHARGE, enable).apply();
        }
        return applied;
    }

    /** Compatibility wrapper for older callers. */
    public void enableBypassCharge(boolean enable) {
        setBypassChargeEnabled(enable);
    }

    public boolean isBypassChargeSupported() {
        return FileUtils.isFileReadable(BYPASS_CHARGE_NODE)
                && FileUtils.isFileWritable(BYPASS_CHARGE_NODE);
    }

    public SafetyCheckResult performSafetyChecks() {
        if (!isBypassChargeSupported()) {
            return new SafetyCheckResult(false, "Bypass charging not supported on this device");
        }
        if (!isWiredChargerConnected()) {
            return new SafetyCheckResult(false, "Wired charger not connected");
        }
        int batteryTemp = getBatteryTemperature();
        if (batteryTemp >= MAX_BATTERY_TEMP) {
            return new SafetyCheckResult(false,
                    String.format("Battery temperature too high (%.1f°C)", batteryTemp / 10.0f));
        }
        int batteryLevel = getBatteryCapacity();
        if (batteryLevel >= 0 && batteryLevel < MIN_BATTERY_CAPACITY) {
            return new SafetyCheckResult(false,
                    String.format("Battery level too low (%d%%)", batteryLevel));
        }
        return new SafetyCheckResult(true, "All safety checks passed");
    }

    private boolean isWiredChargerConnected() {
        Intent batteryStatus = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return false;
        int plug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return (plug & (BatteryManager.BATTERY_PLUGGED_AC
                | BatteryManager.BATTERY_PLUGGED_USB
                | BatteryManager.BATTERY_PLUGGED_DOCK)) != 0;
    }

    private int getBatteryTemperature() {
        String tempStr = FileUtils.readOneLine(BATTERY_TEMP_NODE);
        if (tempStr != null) {
            try {
                return Integer.parseInt(tempStr.trim());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse battery temperature from sysfs", e);
            }
        }
        Intent status = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return status == null ? 0 : status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
    }

    private int getBatteryCapacity() {
        String capacityStr = FileUtils.readOneLine(BATTERY_CAPACITY_NODE);
        if (capacityStr != null) {
            try {
                return Integer.parseInt(capacityStr.trim());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse battery capacity from sysfs", e);
            }
        }
        BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    public static class SafetyCheckResult {
        private final boolean mSafe;
        private final String mReason;

        public SafetyCheckResult(boolean safe, String reason) {
            mSafe = safe;
            mReason = reason;
        }

        public boolean isSafe() { return mSafe; }
        public String getReason() { return mReason; }
    }
}
