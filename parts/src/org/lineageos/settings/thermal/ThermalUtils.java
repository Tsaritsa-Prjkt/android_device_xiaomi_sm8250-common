/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

public final class ThermalUtils {
    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;

    private static final String THERMAL_CONTROL = "thermal_control";
    private static final String THERMAL_STATE_DEFAULT = "0";
    private static final String THERMAL_STATE_BENCHMARK = "10";
    private static final String THERMAL_STATE_BROWSER = "11";
    private static final String THERMAL_STATE_CAMERA = "12";
    private static final String THERMAL_STATE_DIALER = "8";
    private static final String THERMAL_STATE_GAMING = "9";
    private static final String THERMAL_STATE_STREAMING = "14";

    private static final String THERMAL_BENCHMARK = "thermal.benchmark=";
    private static final String THERMAL_BROWSER = "thermal.browser=";
    private static final String THERMAL_CAMERA = "thermal.camera=";
    private static final String THERMAL_DIALER = "thermal.dialer=";
    private static final String THERMAL_GAMING = "thermal.gaming=";
    private static final String THERMAL_STREAMING = "thermal.streaming=";
    private static final String[] PREFIXES = {
            THERMAL_BENCHMARK, THERMAL_BROWSER, THERMAL_CAMERA,
            THERMAL_DIALER, THERMAL_GAMING, THERMAL_STREAMING
    };

    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private final DisplayManager mDisplayManager;
    private boolean mTouchModeChanged;
    private ITouchFeature mTouchFeature;

    protected ThermalUtils(Context context) {
        mContext = context.getApplicationContext();
        Context storage = mContext.createDeviceProtectedStorageContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(storage);
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mTouchFeature = obtainTouchFeature();
        sanitizeProfiles();
    }

    public static void startService(Context context) {
        if (FileUtils.fileExists(THERMAL_SCONFIG)) {
            context.startService(new Intent(context, ThermalService.class));
        }
    }

    private ITouchFeature obtainTouchFeature() {
        try {
            return ITouchFeature.getService();
        } catch (RemoteException | NoSuchElementException e) {
            return null;
        }
    }

    private ITouchFeature touchFeature() {
        if (mTouchFeature == null) mTouchFeature = obtainTouchFeature();
        return mTouchFeature;
    }

    private String emptyProfileString() {
        return THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":"
                + THERMAL_DIALER + ":" + THERMAL_GAMING + ":" + THERMAL_STREAMING;
    }

    private void sanitizeProfiles() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);
        if (value == null || value.isEmpty()) {
            writeValue(emptyProfileString());
            return;
        }
        String[] sections = value.split(":", -1);
        // Migrate valid old five-section data by appending the new Streaming section.
        if (sections.length == 5 && validPrefixes(sections, 5)) {
            writeValue(value + ":" + THERMAL_STREAMING);
        } else if (sections.length != 6 || !validPrefixes(sections, 6)) {
            writeValue(emptyProfileString());
        }
    }

    private static boolean validPrefixes(String[] sections, int count) {
        if (sections == null || sections.length < count) return false;
        for (int i = 0; i < count; i++) {
            if (!sections[i].startsWith(PREFIXES[i])) return false;
        }
        return true;
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private String getValue() {
        sanitizeProfiles();
        return mSharedPrefs.getString(THERMAL_CONTROL, emptyProfileString());
    }

    protected synchronized void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty()) return;
        String[] sections = getValue().split(":", -1);
        @SuppressWarnings("unchecked")
        Set<String>[] packages = new Set[6];
        for (int i = 0; i < 6; i++) packages[i] = parsePackages(sections[i], PREFIXES[i]);
        for (Set<String> set : packages) set.remove(packageName);
        if (mode >= STATE_BENCHMARK && mode <= STATE_STREAMING) {
            packages[mode - 1].add(packageName);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) out.append(':');
            out.append(serialize(PREFIXES[i], packages[i]));
        }
        writeValue(out.toString());
        // Re-evaluate the foreground package even if it did not change.
        startService(mContext);
    }

    protected int getStateForPackage(String packageName) {
        String[] sections = getValue().split(":", -1);
        for (int i = 0; i < 6; i++) {
            if (parsePackages(sections[i], PREFIXES[i]).contains(packageName)) return i + 1;
        }
        return STATE_DEFAULT;
    }

    protected void setDefaultThermalProfile() {
        FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_STATE_DEFAULT);
        resetTouchModes();
    }

    protected void setThermalProfile(String packageName) {
        int state = getStateForPackage(packageName);
        String thermalState;
        switch (state) {
            case STATE_BENCHMARK: thermalState = THERMAL_STATE_BENCHMARK; break;
            case STATE_BROWSER: thermalState = THERMAL_STATE_BROWSER; break;
            case STATE_CAMERA: thermalState = THERMAL_STATE_CAMERA; break;
            case STATE_DIALER: thermalState = THERMAL_STATE_DIALER; break;
            case STATE_GAMING: thermalState = THERMAL_STATE_GAMING; break;
            case STATE_STREAMING: thermalState = THERMAL_STATE_STREAMING; break;
            default: thermalState = THERMAL_STATE_DEFAULT; break;
        }
        FileUtils.writeLine(THERMAL_SCONFIG, thermalState);
        if (state == STATE_BENCHMARK || state == STATE_GAMING) {
            updateTouchModes(packageName);
        } else {
            resetTouchModes();
        }
    }

    private void updateTouchModes(String packageName) {
        resetTouchModes();
        String values = mSharedPrefs.getString(packageName, null);
        int[] tuple = parseTouchTuple(values);
        if (tuple == null) return;
        ITouchFeature touch = touchFeature();
        if (touch == null) return;

        int gameMode = tuple[Constants.TOUCH_GAME_MODE];
        int touchResponse = tuple[Constants.TOUCH_RESPONSE];
        int touchSensitivity = tuple[Constants.TOUCH_SENSITIVITY];
        int touchResistant = tuple[Constants.TOUCH_RESISTANT];
        int activeMode = (touchResponse != 0 && touchSensitivity != 0 && touchResistant != 0) ? 1 : 0;

        try {
            touch.setTouchMode(Constants.MODE_TOUCH_TOLERANCE, touchSensitivity);
            touch.setTouchMode(Constants.MODE_TOUCH_UP_THRESHOLD, touchResponse);
            touch.setTouchMode(Constants.MODE_TOUCH_EDGE_FILTER, touchResistant);
            touch.setTouchMode(Constants.MODE_TOUCH_GAME_MODE, gameMode);
            touch.setTouchMode(Constants.MODE_TOUCH_ACTIVE_MODE, activeMode);
            mTouchModeChanged = true;
            updateTouchRotation();
        } catch (RemoteException | RuntimeException e) {
            mTouchFeature = null;
            resetTouchModesBestEffort(touch);
            mTouchModeChanged = false;
        }
    }

    private static int[] parseTouchTuple(String values) {
        if (values == null || values.isEmpty()) return null;
        String[] parts = values.split(",", -1);
        if (parts.length != 4) return null;
        int[] out = new int[4];
        try {
            for (int i = 0; i < 4; i++) out[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    protected void resetTouchModes() {
        if (!mTouchModeChanged && mTouchFeature == null) return;
        ITouchFeature touch = touchFeature();
        if (touch != null) resetTouchModesBestEffort(touch);
        mTouchModeChanged = false;
    }

    private void resetTouchModesBestEffort(ITouchFeature touch) {
        int[] modes = {
                Constants.MODE_TOUCH_GAME_MODE, Constants.MODE_TOUCH_ACTIVE_MODE,
                Constants.MODE_TOUCH_UP_THRESHOLD, Constants.MODE_TOUCH_TOLERANCE,
                Constants.MODE_TOUCH_EDGE_FILTER, Constants.MODE_TOUCH_ROTATION
        };
        for (int mode : modes) {
            try {
                touch.resetTouchMode(mode);
            } catch (RemoteException | RuntimeException e) {
                mTouchFeature = null;
            }
        }
    }

    protected void updateTouchRotation() {
        if (!mTouchModeChanged) return;
        ITouchFeature touch = touchFeature();
        if (touch == null) return;
        Display display = mDisplayManager == null ? null : mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return;
        int rotation;
        switch (display.getRotation()) {
            case Surface.ROTATION_90: rotation = 1; break;
            case Surface.ROTATION_180: rotation = 2; break;
            case Surface.ROTATION_270: rotation = 3; break;
            case Surface.ROTATION_0:
            default: rotation = 0; break;
        }
        try {
            touch.setTouchMode(Constants.MODE_TOUCH_ROTATION, rotation);
        } catch (RemoteException | RuntimeException e) {
            mTouchFeature = null;
        }
    }

    private static Set<String> parsePackages(String section, String prefix) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (section == null || !section.startsWith(prefix)) return out;
        String payload = section.substring(prefix.length());
        for (String token : payload.split(",")) {
            String pkg = token.trim();
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    private static String serialize(String prefix, Set<String> packages) {
        StringBuilder out = new StringBuilder(prefix);
        for (String pkg : packages) out.append(pkg).append(',');
        return out.toString();
    }
}
