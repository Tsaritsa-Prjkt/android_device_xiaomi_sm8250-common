/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.LinkedHashSet;
import java.util.Set;

/** State owner for per-app refresh-rate policy. */
public final class RefreshUtils {
    private static final String TAG = "RefreshUtils";

    private static final String REFRESH_CONTROL = "refresh_control";
    private static final String KEY_BASELINE_MIN = "refresh_baseline_min";
    private static final String KEY_BASELINE_PEAK = "refresh_baseline_peak";
    private static final String KEY_BASELINE_VALID = "refresh_baseline_valid";
    private static final String KEY_OVERRIDE_ACTIVE = "refresh_override_active";
    private static final String KEY_STATE_VERSION = "refresh_state_version";
    private static final int STATE_VERSION = 2;

    private static final String KEY_PEAK_REFRESH_RATE = Settings.System.PEAK_REFRESH_RATE;
    private static final String KEY_MIN_REFRESH_RATE = Settings.System.MIN_REFRESH_RATE;

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_EXTREME = 2;

    private static final float REFRESH_STATE_DEFAULT = 120f;
    private static final float REFRESH_STATE_STANDARD = 60f;
    private static final float REFRESH_STATE_EXTREME = 120f;

    private static final String REFRESH_STANDARD = "refresh.standard=";
    private static final String REFRESH_EXTREME = "refresh.extreme=";

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;

    protected RefreshUtils(Context context) {
        mContext = context.getApplicationContext();
        Context storage = mContext.createDeviceProtectedStorageContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(storage);
        migrateStateIfNeeded();
        sanitizeStoredProfiles();
    }

    public static void startService(Context context) {
        context.startService(new Intent(context, RefreshService.class));
    }

    /**
     * Records an explicit user-selected global refresh policy, for example from
     * the XiaomiParts QS tile. Explicit global changes are never treated as an
     * active per-app override.
     */
    public static void updateBaseline(Context context, float minRate, float peakRate) {
        Context storage = context.createDeviceProtectedStorageContext();
        PreferenceManager.getDefaultSharedPreferences(storage).edit()
                .putFloat(KEY_BASELINE_MIN, minRate)
                .putFloat(KEY_BASELINE_PEAK, peakRate)
                .putBoolean(KEY_BASELINE_VALID, true)
                .putBoolean(KEY_OVERRIDE_ACTIVE, false)
                .putInt(KEY_STATE_VERSION, STATE_VERSION)
                .apply();
    }

    /**
     * Version 1 captured a baseline when the service started and then rewrote it
     * for every app using the Default profile. That could overwrite changes made
     * in Android Settings (for example, Minimum refresh rate 60 Hz) with a stale
     * 120 Hz value. Drop that stale ownership state during upgrade.
     */
    private void migrateStateIfNeeded() {
        if (mSharedPrefs.getInt(KEY_STATE_VERSION, 0) >= STATE_VERSION) return;
        mSharedPrefs.edit()
                .remove(KEY_BASELINE_MIN)
                .remove(KEY_BASELINE_PEAK)
                .putBoolean(KEY_BASELINE_VALID, false)
                .putBoolean(KEY_OVERRIDE_ACTIVE, false)
                .putInt(KEY_STATE_VERSION, STATE_VERSION)
                .apply();
    }

    private void sanitizeStoredProfiles() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, null);
        if (!isValidProfileString(value)) writeValue(emptyProfileString());
    }

    private String emptyProfileString() {
        return REFRESH_STANDARD + ":" + REFRESH_EXTREME;
    }

    private boolean isValidProfileString(String value) {
        if (value == null || value.isEmpty()) return false;
        String[] modes = value.split(":", -1);
        return modes.length == 2
                && modes[0].startsWith(REFRESH_STANDARD)
                && modes[1].startsWith(REFRESH_EXTREME);
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(REFRESH_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, emptyProfileString());
        if (!isValidProfileString(value)) {
            value = emptyProfileString();
            writeValue(value);
        }
        return value;
    }

    protected synchronized void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty()) return;
        String[] sections = getValue().split(":", -1);
        Set<String> standard = parsePackages(sections[0], REFRESH_STANDARD);
        Set<String> extreme = parsePackages(sections[1], REFRESH_EXTREME);
        standard.remove(packageName);
        extreme.remove(packageName);

        if (mode == STATE_STANDARD) standard.add(packageName);
        else if (mode == STATE_EXTREME) extreme.add(packageName);

        writeValue(serialize(REFRESH_STANDARD, standard) + ":"
                + serialize(REFRESH_EXTREME, extreme));
        startService(mContext);
    }

    protected int getStateForPackage(String packageName) {
        String[] sections = getValue().split(":", -1);
        if (parsePackages(sections[0], REFRESH_STANDARD).contains(packageName)) return STATE_STANDARD;
        if (parsePackages(sections[1], REFRESH_EXTREME).contains(packageName)) return STATE_EXTREME;
        return STATE_DEFAULT;
    }

    /** Capture the user's current system policy immediately before an override. */
    private void beginOverrideIfNeeded() {
        if (mSharedPrefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)) return;

        float peak = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
        float min = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_MIN_REFRESH_RATE, peak);
        mSharedPrefs.edit()
                .putFloat(KEY_BASELINE_MIN, min)
                .putFloat(KEY_BASELINE_PEAK, peak)
                .putBoolean(KEY_BASELINE_VALID, true)
                .putBoolean(KEY_OVERRIDE_ACTIVE, true)
                .apply();
    }

    protected void setRefreshRate(String packageName) {
        int state = getStateForPackage(packageName);

        // Default means XiaomiParts does not own refresh-rate policy. If we are
        // leaving a profiled app, restore its captured policy once; otherwise do
        // nothing and leave Android Settings in full control.
        if (state == STATE_DEFAULT) {
            restoreBaseline();
            return;
        }

        beginOverrideIfNeeded();
        float rate = state == STATE_STANDARD ? REFRESH_STATE_STANDARD : REFRESH_STATE_EXTREME;
        float currentMin = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_MIN_REFRESH_RATE, rate);
        float min = Math.min(currentMin, rate);
        writeRatesTransactional(min, rate);
    }

    protected void restoreBaseline() {
        if (!mSharedPrefs.getBoolean(KEY_OVERRIDE_ACTIVE, false)) return;

        if (!mSharedPrefs.getBoolean(KEY_BASELINE_VALID, false)) {
            clearOverrideState();
            return;
        }

        float min = mSharedPrefs.getFloat(KEY_BASELINE_MIN, REFRESH_STATE_DEFAULT);
        float peak = mSharedPrefs.getFloat(KEY_BASELINE_PEAK, REFRESH_STATE_DEFAULT);
        if (writeRatesTransactional(min, peak)) {
            clearOverrideState();
        }
    }

    private void clearOverrideState() {
        mSharedPrefs.edit()
                .remove(KEY_BASELINE_MIN)
                .remove(KEY_BASELINE_PEAK)
                .putBoolean(KEY_BASELINE_VALID, false)
                .putBoolean(KEY_OVERRIDE_ACTIVE, false)
                .apply();
    }

    private boolean writeRatesTransactional(float min, float peak) {
        float oldMin = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_MIN_REFRESH_RATE, REFRESH_STATE_DEFAULT);
        float oldPeak = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
        boolean minOk = Settings.System.putFloat(mContext.getContentResolver(),
                KEY_MIN_REFRESH_RATE, min);
        boolean peakOk = Settings.System.putFloat(mContext.getContentResolver(),
                KEY_PEAK_REFRESH_RATE, peak);
        if (!(minOk && peakOk)) {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, oldMin);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, oldPeak);
            Log.w(TAG, "Refresh-rate write failed; restored previous values");
            return false;
        }
        return true;
    }

    private static Set<String> parsePackages(String section, String prefix) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (section == null || !section.startsWith(prefix)) return out;
        String payload = section.substring(prefix.length());
        for (String token : payload.split(",")) {
            String item = token.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    private static String serialize(String prefix, Set<String> packages) {
        StringBuilder out = new StringBuilder(prefix);
        for (String pkg : packages) out.append(pkg).append(',');
        return out.toString();
    }
}
