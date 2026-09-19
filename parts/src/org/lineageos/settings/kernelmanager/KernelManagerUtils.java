/*
 * Copyright (C) 2025 KamiKaonashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.lineageos.settings.kernelmanager;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** State-safe CPU governor/frequency helpers for XiaomiParts. */
public class KernelManagerUtils {

    public static final int EFFICIENCY_CLUSTER = 0;
    public static final int PERFORMANCE_CLUSTER = 6;

    public static final String PREF_GOVERNOR = "cpu_governor";
    public static final String PREF_EFFICIENCY_MIN = "efficiency_min_freq";
    public static final String PREF_EFFICIENCY_MAX = "efficiency_max_freq";
    public static final String PREF_PERFORMANCE_MIN = "performance_min_freq";
    public static final String PREF_PERFORMANCE_MAX = "performance_max_freq";
    public static final String PREF_APPLY_ON_BOOT = "kernel_apply_on_boot";

    private static final int[] POLICIES = {EFFICIENCY_CLUSTER, PERFORMANCE_CLUSTER};
    private static final String DEFAULT_GOVERNOR = "schedutil";
    private static final String CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq/policy";
    private static final String SCALING_GOVERNOR = "/scaling_governor";
    private static final String SCALING_MIN_FREQ = "/scaling_min_freq";
    private static final String SCALING_MAX_FREQ = "/scaling_max_freq";
    private static final String SCALING_AVAILABLE_GOVERNORS_REAL = "/scaling_available_governors";
    private static final String SCALING_AVAILABLE_FREQUENCIES = "/scaling_available_frequencies";

    public String[] getAvailableGovernors() {
        String governors = readOrDefault(path(EFFICIENCY_CLUSTER, SCALING_AVAILABLE_GOVERNORS_REAL), "").trim();
        return governors.isEmpty()
                ? new String[]{"schedutil", "performance", "powersave", "ondemand", "conservative"}
                : governors.split("\\s+");
    }

    public String[] getAvailableFrequencies(int cluster) {
        String frequencies = readOrDefault(path(cluster, SCALING_AVAILABLE_FREQUENCIES), "").trim();
        return frequencies.isEmpty() ? null : frequencies.split("\\s+");
    }

    public String getCurrentGovernor(int cluster) {
        return readOrDefault(path(cluster, SCALING_GOVERNOR), DEFAULT_GOVERNOR).trim();
    }

    public String getCurrentMinFrequency(int cluster) {
        String value = readOrDefault(path(cluster, SCALING_MIN_FREQ), "").trim();
        if (!value.isEmpty()) return value;
        String[] frequencies = getAvailableFrequencies(cluster);
        return frequencies != null && frequencies.length > 0 ? frequencies[0] : "0";
    }

    public String getCurrentMaxFrequency(int cluster) {
        String value = readOrDefault(path(cluster, SCALING_MAX_FREQ), "").trim();
        if (!value.isEmpty()) return value;
        String[] frequencies = getAvailableFrequencies(cluster);
        return frequencies != null && frequencies.length > 0
                ? frequencies[frequencies.length - 1] : "0";
    }

    public boolean setGovernor(String governor) {
        if (governor == null || governor.trim().isEmpty()) return false;
        boolean ok = true;
        for (int cluster : POLICIES) {
            String old = getCurrentGovernor(cluster);
            boolean written = writeAndVerify(path(cluster, SCALING_GOVERNOR), governor.trim());
            if (!written) {
                writeSilently(path(cluster, SCALING_GOVERNOR), old);
                ok = false;
            }
        }
        return ok;
    }

    public boolean setFrequencyRange(int cluster, String minFreq, String maxFreq) {
        long min = parsePositive(minFreq);
        long max = parsePositive(maxFreq);
        if (min <= 0 || max <= 0 || min > max) return false;

        String minPath = path(cluster, SCALING_MIN_FREQ);
        String maxPath = path(cluster, SCALING_MAX_FREQ);
        String oldMin = getCurrentMinFrequency(cluster);
        String oldMax = getCurrentMaxFrequency(cluster);
        long currentMin = parsePositive(oldMin);
        long currentMax = parsePositive(oldMax);

        boolean first;
        boolean second;
        if (currentMax > 0 && min > currentMax) {
            first = writeAndVerify(maxPath, maxFreq);
            second = first && writeAndVerify(minPath, minFreq);
        } else if (currentMin > 0 && max < currentMin) {
            first = writeAndVerify(minPath, minFreq);
            second = first && writeAndVerify(maxPath, maxFreq);
        } else {
            first = writeAndVerify(minPath, minFreq);
            second = first && writeAndVerify(maxPath, maxFreq);
        }
        if (!second) {
            // Best-effort rollback to the complete previous range.
            writeSilently(maxPath, oldMax);
            writeSilently(minPath, oldMin);
            return false;
        }
        return true;
    }

    public boolean setEfficiencyClusterFrequency(String minFreq, String maxFreq) {
        return setFrequencyRange(EFFICIENCY_CLUSTER, minFreq, maxFreq);
    }

    public boolean setPerformanceClusterFrequency(String minFreq, String maxFreq) {
        return setFrequencyRange(PERFORMANCE_CLUSTER, minFreq, maxFreq);
    }

    public boolean resetToDefaults() {
        boolean ok = setGovernor(DEFAULT_GOVERNOR);
        for (int cluster : POLICIES) {
            String[] frequencies = getAvailableFrequencies(cluster);
            if (frequencies != null && frequencies.length > 0) {
                ok &= setFrequencyRange(cluster, frequencies[0], frequencies[frequencies.length - 1]);
            }
        }
        return ok;
    }

    /** Re-applies the selected CPU profile after direct boot. */
    public static void restoreOnBoot(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        if (!prefs.getBoolean(PREF_APPLY_ON_BOOT, false)) return;

        KernelManagerUtils utils = new KernelManagerUtils();
        String governor = prefs.getString(PREF_GOVERNOR,
                utils.getCurrentGovernor(EFFICIENCY_CLUSTER));
        String effMin = prefs.getString(PREF_EFFICIENCY_MIN,
                utils.getCurrentMinFrequency(EFFICIENCY_CLUSTER));
        String effMax = prefs.getString(PREF_EFFICIENCY_MAX,
                utils.getCurrentMaxFrequency(EFFICIENCY_CLUSTER));
        String perfMin = prefs.getString(PREF_PERFORMANCE_MIN,
                utils.getCurrentMinFrequency(PERFORMANCE_CLUSTER));
        String perfMax = prefs.getString(PREF_PERFORMANCE_MAX,
                utils.getCurrentMaxFrequency(PERFORMANCE_CLUSTER));

        utils.setGovernor(governor);
        utils.setEfficiencyClusterFrequency(effMin, effMax);
        utils.setPerformanceClusterFrequency(perfMin, perfMax);
    }

    private static String path(int cluster, String suffix) {
        return CPU_BASE_PATH + cluster + suffix;
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
