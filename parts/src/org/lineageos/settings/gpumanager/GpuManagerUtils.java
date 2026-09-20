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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Low-level, state-safe helpers for the KGSL GPU controls exposed by XiaomiParts. */
public class GpuManagerUtils {

    private static final String GPU_BASE_PATH = "/sys/class/kgsl/kgsl-3d0";
    private static final String GPU_DEVFREQ_PATH = GPU_BASE_PATH + "/devfreq";
    private static final String DEFAULT_GOVERNOR = "msm-adreno-tz";

    /*
     * Device-tree GPU table for sm8250-common.
     * Keep this explicit rather than depending on gpu_available_frequencies,
     * which is not exported by every downstream KGSL kernel.
     */
    private static final String[] GPU_FREQUENCIES_HZ = {
            "305000000",
            "400000000",
            "441600000",
            "490000000",
            "525000000",
            "587000000",
            "670000000"
    };

    public static final String PREF_GOVERNOR = "gpu_governor";
    public static final String PREF_MIN_FREQ = "gpu_min_freq";
    public static final String PREF_MAX_FREQ = "gpu_max_freq";
    public static final String PREF_FORCE_CLK_ON = "gpu_force_clk_on";
    public static final String PREF_FORCE_BUS_ON = "gpu_force_bus_on";
    public static final String PREF_FORCE_RAIL_ON = "gpu_force_rail_on";
    public static final String PREF_FORCE_NO_NAP = "gpu_force_no_nap";
    public static final String PREF_BUS_SPLIT = "gpu_bus_split";
    public static final String PREF_APPLY_ON_BOOT = "gpu_apply_on_boot";

    private static final String GPU_MODEL = GPU_BASE_PATH + "/gpu_model";
    private static final String GPU_CURRENT_FREQ = GPU_BASE_PATH + "/gpuclk";
    private static final String GPU_MIN_FREQ = GPU_DEVFREQ_PATH + "/min_freq";
    private static final String GPU_MAX_FREQ = GPU_DEVFREQ_PATH + "/max_freq";
    private static final String GPU_GOVERNOR = GPU_DEVFREQ_PATH + "/governor";
    private static final String GPU_AVAILABLE_GOVERNORS = GPU_DEVFREQ_PATH + "/available_governors";
    private static final String GPU_BUSY_PERCENTAGE = GPU_BASE_PATH + "/gpu_busy_percentage";
    private static final String GPU_TEMPERATURE = GPU_BASE_PATH + "/temp";
    private static final String GPU_THERMAL_PWRLEVEL = GPU_BASE_PATH + "/thermal_pwrlevel";
    private static final String GPU_FORCE_CLK_ON = GPU_BASE_PATH + "/force_clk_on";
    private static final String GPU_FORCE_BUS_ON = GPU_BASE_PATH + "/force_bus_on";
    private static final String GPU_FORCE_RAIL_ON = GPU_BASE_PATH + "/force_rail_on";
    private static final String GPU_FORCE_NO_NAP = GPU_BASE_PATH + "/force_no_nap";
    private static final String GPU_BUS_SPLIT = GPU_BASE_PATH + "/bus_split";

    public String getGpuModel() {
        return readOrDefault(GPU_MODEL, "Adreno GPU");
    }

    public String[] getAvailableGovernors() {
        String governors = readOrDefault(GPU_AVAILABLE_GOVERNORS, "").trim();
        return governors.isEmpty()
                ? new String[]{"msm-adreno-tz", "performance", "powersave", "simple_ondemand"}
                : governors.split("\\s+");
    }

    /** Returns the sm8250-common frequency table requested by the device configuration. */
    public String[] getAvailableFrequencies() {
        String[] values = GPU_FREQUENCIES_HZ.clone();
        Arrays.sort(values, Comparator.comparingLong(GpuManagerUtils::parsePositive));
        return values;
    }

    public String getCurrentGovernor() {
        return readOrDefault(GPU_GOVERNOR, DEFAULT_GOVERNOR).trim();
    }

    public String getCurrentFrequency() {
        return readOrDefault(GPU_CURRENT_FREQ, "0").trim();
    }

    public String getCurrentMinFrequency() {
        return readOrDefault(GPU_MIN_FREQ, GPU_FREQUENCIES_HZ[0]).trim();
    }

    public String getCurrentMaxFrequency() {
        return readOrDefault(GPU_MAX_FREQ, GPU_FREQUENCIES_HZ[GPU_FREQUENCIES_HZ.length - 1]).trim();
    }

    public String getGpuBusyPercentage() {
        return readOrDefault(GPU_BUSY_PERCENTAGE, "0").trim();
    }

    public String getGpuTemperature() {
        try {
            long raw = Long.parseLong(readOrDefault(GPU_TEMPERATURE, "0").trim());
            double celsius = raw > 1000 ? raw / 1000.0 : raw;
            return String.format(java.util.Locale.US, "%.1f", celsius);
        } catch (RuntimeException e) {
            return "0";
        }
    }

    public String getThermalPowerLevel() {
        return readOrDefault(GPU_THERMAL_PWRLEVEL, "0").trim();
    }

    public boolean isGovernorSupported() { return exists(GPU_GOVERNOR); }
    public boolean isFrequencyControlSupported() { return exists(GPU_MIN_FREQ) && exists(GPU_MAX_FREQ); }
    public boolean isForceClkSupported() { return exists(GPU_FORCE_CLK_ON); }
    public boolean isForceBusSupported() { return exists(GPU_FORCE_BUS_ON); }
    public boolean isForceRailSupported() { return exists(GPU_FORCE_RAIL_ON); }
    public boolean isForceNoNapSupported() { return exists(GPU_FORCE_NO_NAP); }
    public boolean isBusSplitSupported() { return exists(GPU_BUS_SPLIT); }

    public boolean getForceClkOn() { return readBoolean(GPU_FORCE_CLK_ON); }
    public boolean getForceBusOn() { return readBoolean(GPU_FORCE_BUS_ON); }
    public boolean getForceRailOn() { return readBoolean(GPU_FORCE_RAIL_ON); }
    public boolean getForceNoNap() { return readBoolean(GPU_FORCE_NO_NAP); }
    public boolean getBusSplit() { return readBoolean(GPU_BUS_SPLIT); }

    public boolean setGovernor(String governor) {
        if (!isGovernorSupported()) return false;
        if (governor == null || governor.trim().isEmpty()) return false;
        String old = getCurrentGovernor();
        if (!writeAndVerify(GPU_GOVERNOR, governor.trim())) return false;
        if (!governor.trim().equals(getCurrentGovernor())) {
            writeSilently(GPU_GOVERNOR, old);
            return false;
        }
        return true;
    }

    /** Applies a valid min/max pair while avoiding transient min > max failures. */
    public boolean setFrequencyRange(String minFreq, String maxFreq) {
        if (!isFrequencyControlSupported()) return false;
        String normalizedMin = normalizeConfiguredFrequency(minFreq);
        String normalizedMax = normalizeConfiguredFrequency(maxFreq);
        long min = parsePositive(normalizedMin);
        long max = parsePositive(normalizedMax);
        if (min <= 0 || max <= 0 || min > max) return false;

        String oldMin = getCurrentMinFrequency();
        String oldMax = getCurrentMaxFrequency();
        long currentMin = parsePositive(oldMin);
        long currentMax = parsePositive(oldMax);

        boolean firstOk;
        boolean secondOk;
        if (currentMax > 0 && min > currentMax) {
            firstOk = writeAndVerify(GPU_MAX_FREQ, normalizedMax);
            secondOk = firstOk && writeAndVerify(GPU_MIN_FREQ, normalizedMin);
        } else if (currentMin > 0 && max < currentMin) {
            firstOk = writeAndVerify(GPU_MIN_FREQ, normalizedMin);
            secondOk = firstOk && writeAndVerify(GPU_MAX_FREQ, normalizedMax);
        } else {
            firstOk = writeAndVerify(GPU_MIN_FREQ, normalizedMin);
            secondOk = firstOk && writeAndVerify(GPU_MAX_FREQ, normalizedMax);
        }
        if (!secondOk) {
            restoreFrequencyRange(oldMin, oldMax);
            return false;
        }
        return true;
    }

    public boolean setForceClkOn(boolean enabled) { return setOptionalBoolean(GPU_FORCE_CLK_ON, enabled); }
    public boolean setForceBusOn(boolean enabled) { return setOptionalBoolean(GPU_FORCE_BUS_ON, enabled); }
    public boolean setForceRailOn(boolean enabled) { return setOptionalBoolean(GPU_FORCE_RAIL_ON, enabled); }
    public boolean setForceNoNap(boolean enabled) { return setOptionalBoolean(GPU_FORCE_NO_NAP, enabled); }
    public boolean setBusSplit(boolean enabled) { return setOptionalBoolean(GPU_BUS_SPLIT, enabled); }

    public boolean resetToDefaults() {
        boolean ok = true;
        if (isGovernorSupported()) ok &= setGovernor(DEFAULT_GOVERNOR);
        if (isFrequencyControlSupported()) {
            String[] frequencies = getAvailableFrequencies();
            ok &= setFrequencyRange(frequencies[0], frequencies[frequencies.length - 1]);
        }
        if (isForceClkSupported()) ok &= setForceClkOn(false);
        if (isForceBusSupported()) ok &= setForceBusOn(false);
        if (isForceRailSupported()) ok &= setForceRailOn(false);
        if (isForceNoNapSupported()) ok &= setForceNoNap(false);
        if (isBusSplitSupported()) ok &= setBusSplit(false);
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

        if (utils.isGovernorSupported()) utils.setGovernor(governor);
        if (utils.isFrequencyControlSupported()) utils.setFrequencyRange(min, max);
        if (utils.isForceClkSupported()) utils.setForceClkOn(prefs.getBoolean(PREF_FORCE_CLK_ON, false));
        if (utils.isForceBusSupported()) utils.setForceBusOn(prefs.getBoolean(PREF_FORCE_BUS_ON, false));
        if (utils.isForceRailSupported()) utils.setForceRailOn(prefs.getBoolean(PREF_FORCE_RAIL_ON, false));
        if (utils.isForceNoNapSupported()) utils.setForceNoNap(prefs.getBoolean(PREF_FORCE_NO_NAP, false));
        if (utils.isBusSplitSupported()) utils.setBusSplit(prefs.getBoolean(PREF_BUS_SPLIT, false));
    }

    public static String formatFrequencyMhz(String hz) {
        try {
            long value = Long.parseLong(hz);
            if (value % 1_000_000L == 0) return Long.toString(value / 1_000_000L);
            return String.format(java.util.Locale.US, "%.1f", value / 1_000_000.0);
        } catch (RuntimeException e) {
            return "0";
        }
    }

    private boolean readBoolean(String path) {
        return "1".equals(readOrDefault(path, "0").trim());
    }

    /** Missing optional advanced nodes must not make the whole GPU profile fail. */
    private boolean setOptionalBoolean(String path, boolean enabled) {
        if (!exists(path)) return true;
        return writeAndVerify(path, enabled ? "1" : "0");
    }

    private String normalizeConfiguredFrequency(String value) {
        long requested = parsePositive(value);
        if (requested <= 0) return null;
        String best = GPU_FREQUENCIES_HZ[0];
        long bestDelta = Math.abs(parsePositive(best) - requested);
        for (String frequency : GPU_FREQUENCIES_HZ) {
            long delta = Math.abs(parsePositive(frequency) - requested);
            if (delta < bestDelta) {
                best = frequency;
                bestDelta = delta;
            }
        }
        return best;
    }

    private void restoreFrequencyRange(String oldMin, String oldMax) {
        long min = parsePositive(oldMin);
        long max = parsePositive(oldMax);
        if (min <= 0 || max <= 0 || min > max) return;
        writeSilently(GPU_MAX_FREQ, oldMax);
        writeSilently(GPU_MIN_FREQ, oldMin);
    }

    private static boolean exists(String path) {
        try { return new File(path).exists(); } catch (RuntimeException e) { return false; }
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
            /* Some vendor sysfs controls are write-only. A successful write is sufficient there. */
            if (!new File(path).canRead()) return true;
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
