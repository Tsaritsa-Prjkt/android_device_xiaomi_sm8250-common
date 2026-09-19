/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.lineageos.settings.gpumanager;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** Low-level, state-safe helpers for the KGSL GPU controls exposed by XiaomiParts. */
public class GpuManagerUtils {

    private static final String GPU_BASE_PATH = "/sys/class/kgsl/kgsl-3d0";
    private static final String DEFAULT_GOVERNOR = "msm-adreno-tz";

    public static final String PREF_GOVERNOR = "gpu_governor";
    public static final String PREF_MIN_FREQ = "gpu_min_freq";
    public static final String PREF_MAX_FREQ = "gpu_max_freq";
    public static final String PREF_FORCE_CLK_ON = "gpu_force_clk_on";
    public static final String PREF_FORCE_BUS_ON = "gpu_force_bus_on";
    public static final String PREF_FORCE_RAIL_ON = "gpu_force_rail_on";
    public static final String PREF_FORCE_NO_NAP = "gpu_force_no_nap";
    public static final String PREF_BUS_SPLIT = "gpu_bus_split";
    public static final String PREF_APPLY_ON_BOOT = "gpu_apply_on_boot";

    private static final String GPU_MODEL = "/gpu_model";
    private static final String GPU_AVAILABLE_FREQUENCIES = "/gpu_available_frequencies";
    private static final String GPU_CURRENT_FREQ = "/gpuclk";
    private static final String GPU_MIN_FREQ = "/devfreq/min_freq";
    private static final String GPU_MAX_FREQ = "/devfreq/max_freq";
    private static final String GPU_GOVERNOR = "/devfreq/governor";
    private static final String GPU_AVAILABLE_GOVERNORS = "/devfreq/available_governors";
    private static final String GPU_BUSY_PERCENTAGE = "/gpu_busy_percentage";
    private static final String GPU_TEMPERATURE = "/temp";
    private static final String GPU_THERMAL_PWRLEVEL = "/thermal_pwrlevel";
    private static final String GPU_FORCE_CLK_ON = "/force_clk_on";
    private static final String GPU_FORCE_BUS_ON = "/force_bus_on";
    private static final String GPU_FORCE_RAIL_ON = "/force_rail_on";
    private static final String GPU_FORCE_NO_NAP = "/force_no_nap";
    private static final String GPU_BUS_SPLIT = "/bus_split";

    public String getGpuModel() {
        return readOrDefault(GPU_BASE_PATH + GPU_MODEL, "Unknown GPU");
    }

    public String[] getAvailableGovernors() {
        String governors = readOrDefault(GPU_BASE_PATH + GPU_AVAILABLE_GOVERNORS, "").trim();
        return governors.isEmpty()
                ? new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"}
                : governors.split("\\s+");
    }

    public String[] getAvailableFrequencies() {
        String frequencies = readOrDefault(GPU_BASE_PATH + GPU_AVAILABLE_FREQUENCIES, "").trim();
        return frequencies.isEmpty() ? null : frequencies.split("\\s+");
    }

    public String getCurrentGovernor() {
        return readOrDefault(GPU_BASE_PATH + GPU_GOVERNOR, DEFAULT_GOVERNOR).trim();
    }

    public String getCurrentFrequency() {
        return readOrDefault(GPU_BASE_PATH + GPU_CURRENT_FREQ, "0").trim();
    }

    public String getCurrentMinFrequency() {
        return readOrDefault(GPU_BASE_PATH + GPU_MIN_FREQ, "0").trim();
    }

    public String getCurrentMaxFrequency() {
        return readOrDefault(GPU_BASE_PATH + GPU_MAX_FREQ, "0").trim();
    }

    public String getGpuBusyPercentage() {
        return readOrDefault(GPU_BASE_PATH + GPU_BUSY_PERCENTAGE, "0").trim();
    }

    public String getGpuTemperature() {
        try {
            long raw = Long.parseLong(readOrDefault(GPU_BASE_PATH + GPU_TEMPERATURE, "0").trim());
            // Most KGSL kernels expose millidegrees, but tolerate kernels exposing degrees.
            double celsius = raw > 1000 ? raw / 1000.0 : raw;
            return String.format(java.util.Locale.US, "%.1f", celsius);
        } catch (RuntimeException e) {
            return "0";
        }
    }

    public String getThermalPowerLevel() {
        return readOrDefault(GPU_BASE_PATH + GPU_THERMAL_PWRLEVEL, "0").trim();
    }

    public boolean getForceClkOn() { return readBoolean(GPU_FORCE_CLK_ON); }
    public boolean getForceBusOn() { return readBoolean(GPU_FORCE_BUS_ON); }
    public boolean getForceRailOn() { return readBoolean(GPU_FORCE_RAIL_ON); }
    public boolean getForceNoNap() { return readBoolean(GPU_FORCE_NO_NAP); }
    public boolean getBusSplit() { return readBoolean(GPU_BUS_SPLIT); }

    public boolean setGovernor(String governor) {
        if (governor == null || governor.trim().isEmpty()) return false;
        String old = getCurrentGovernor();
        if (!writeAndVerify(GPU_BASE_PATH + GPU_GOVERNOR, governor.trim())) return false;
        if (!governor.trim().equals(getCurrentGovernor())) {
            writeSilently(GPU_BASE_PATH + GPU_GOVERNOR, old);
            return false;
        }
        return true;
    }

    /** Applies a valid min/max pair while avoiding transient min > max failures. */
    public boolean setFrequencyRange(String minFreq, String maxFreq) {
        long min = parsePositive(minFreq);
        long max = parsePositive(maxFreq);
        if (min <= 0 || max <= 0 || min > max) return false;

        String oldMin = getCurrentMinFrequency();
        String oldMax = getCurrentMaxFrequency();
        long currentMin = parsePositive(oldMin);
        long currentMax = parsePositive(oldMax);

        boolean firstOk;
        boolean secondOk;
        if (currentMax > 0 && min > currentMax) {
            firstOk = writeAndVerify(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq);
            secondOk = firstOk && writeAndVerify(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq);
        } else if (currentMin > 0 && max < currentMin) {
            firstOk = writeAndVerify(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq);
            secondOk = firstOk && writeAndVerify(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq);
        } else {
            firstOk = writeAndVerify(GPU_BASE_PATH + GPU_MIN_FREQ, minFreq);
            secondOk = firstOk && writeAndVerify(GPU_BASE_PATH + GPU_MAX_FREQ, maxFreq);
        }
        if (!secondOk) {
            restoreFrequencyRange(oldMin, oldMax);
            return false;
        }
        return true;
    }

    public boolean setForceClkOn(boolean enabled) { return setBoolean(GPU_FORCE_CLK_ON, enabled); }
    public boolean setForceBusOn(boolean enabled) { return setBoolean(GPU_FORCE_BUS_ON, enabled); }
    public boolean setForceRailOn(boolean enabled) { return setBoolean(GPU_FORCE_RAIL_ON, enabled); }
    public boolean setForceNoNap(boolean enabled) { return setBoolean(GPU_FORCE_NO_NAP, enabled); }
    public boolean setBusSplit(boolean enabled) { return setBoolean(GPU_BUS_SPLIT, enabled); }

    public boolean resetToDefaults() {
        boolean ok = setGovernor(DEFAULT_GOVERNOR);
        String[] frequencies = getAvailableFrequencies();
        if (frequencies != null && frequencies.length > 0) {
            ok &= setFrequencyRange(frequencies[0], frequencies[frequencies.length - 1]);
        }
        ok &= setForceClkOn(false);
        ok &= setForceBusOn(false);
        ok &= setForceRailOn(false);
        ok &= setForceNoNap(false);
        ok &= setBusSplit(false);
        return ok;
    }

    /** Re-applies the user's selected profile after LOCKED_BOOT_COMPLETED. */
    public static void restoreOnBoot(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        if (!prefs.getBoolean(PREF_APPLY_ON_BOOT, false)) return;

        GpuManagerUtils utils = new GpuManagerUtils();
        String governor = prefs.getString(PREF_GOVERNOR, utils.getCurrentGovernor());
        String min = prefs.getString(PREF_MIN_FREQ, utils.getCurrentMinFrequency());
        String max = prefs.getString(PREF_MAX_FREQ, utils.getCurrentMaxFrequency());

        utils.setGovernor(governor);
        utils.setFrequencyRange(min, max);
        utils.setForceClkOn(prefs.getBoolean(PREF_FORCE_CLK_ON, false));
        utils.setForceBusOn(prefs.getBoolean(PREF_FORCE_BUS_ON, false));
        utils.setForceRailOn(prefs.getBoolean(PREF_FORCE_RAIL_ON, false));
        utils.setForceNoNap(prefs.getBoolean(PREF_FORCE_NO_NAP, false));
        utils.setBusSplit(prefs.getBoolean(PREF_BUS_SPLIT, false));
    }

    private boolean readBoolean(String relativePath) {
        return "1".equals(readOrDefault(GPU_BASE_PATH + relativePath, "0").trim());
    }

    private boolean setBoolean(String relativePath, boolean enabled) {
        String expected = enabled ? "1" : "0";
        return writeAndVerify(GPU_BASE_PATH + relativePath, expected);
    }

    private void restoreFrequencyRange(String oldMin, String oldMax) {
        long min = parsePositive(oldMin);
        long max = parsePositive(oldMax);
        if (min <= 0 || max <= 0 || min > max) return;
        writeSilently(GPU_BASE_PATH + GPU_MAX_FREQ, oldMax);
        writeSilently(GPU_BASE_PATH + GPU_MIN_FREQ, oldMin);
    }

    private static long parsePositive(String value) {
        try { return Long.parseLong(value); } catch (RuntimeException e) { return -1; }
    }

    private String readOrDefault(String path, String fallback) {
        try {
            String value = readFile(path);
            return value == null ? fallback : value;
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    private boolean writeAndVerify(String path, String value) {
        try {
            writeFile(path, value);
            String readBack = readFile(path);
            return readBack != null && value.trim().equals(readBack.trim());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private void writeSilently(String path, String value) {
        try { writeFile(path, value); } catch (IOException | RuntimeException ignored) { }
    }

    private String readFile(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        }
    }

    private void writeFile(String path, String value) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(value);
            writer.flush();
        }
    }
}
