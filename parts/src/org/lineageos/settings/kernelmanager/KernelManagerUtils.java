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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/** State-safe CPU governor/frequency helpers for the three SM8250 cpufreq policies. */
public class KernelManagerUtils {

    public static final int EFFICIENCY_CLUSTER = 0;   // cpu0-3
    public static final int PERFORMANCE_CLUSTER = 4;  // cpu4-6
    public static final int PRIME_CLUSTER = 7;        // cpu7

    public static final String PREF_GOVERNOR = "cpu_governor";
    public static final String PREF_EFFICIENCY_MIN = "efficiency_min_freq";
    public static final String PREF_EFFICIENCY_MAX = "efficiency_max_freq";
    public static final String PREF_PERFORMANCE_MIN = "performance_min_freq";
    public static final String PREF_PERFORMANCE_MAX = "performance_max_freq";
    public static final String PREF_APPLY_ON_BOOT = "kernel_apply_on_boot";

    private static final int[] POLICIES = {
            EFFICIENCY_CLUSTER, PERFORMANCE_CLUSTER, PRIME_CLUSTER
    };
    private static final String DEFAULT_GOVERNOR = "schedutil";
    private static final String CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq/policy";
    private static final String SCALING_GOVERNOR = "/scaling_governor";
    private static final String SCALING_MIN_FREQ = "/scaling_min_freq";
    private static final String SCALING_MAX_FREQ = "/scaling_max_freq";
    private static final String SCALING_AVAILABLE_GOVERNORS = "/scaling_available_governors";
    private static final String SCALING_AVAILABLE_FREQUENCIES = "/scaling_available_frequencies";

    /*
     * KProfiles table supplied for this device family.
     * Columns: littleMin, littleMax, bigMin, bigMax, all in MHz.
     * Values are mapped to the kernel's exact kHz OPPs at runtime
     * (for example 1804 MHz -> 1804800 kHz when that is the exported OPP)
     * so rounded profile labels never cause cpufreq to reject a write.
     */
    private static final int[][] PROFILE_LEVELS_MHZ = {
            {300, 1804, 300, 3196},
            {300, 1804, 300, 3072},
            {300, 1804, 300, 2956},
            {300, 1804, 300, 2840},
            {300, 1804, 300, 2727},
            {300, 1728, 300, 2611},
            {300, 1728, 300, 2496},
            {300, 1728, 300, 2419},
            {300, 1728, 300, 2302},
            {300, 1728, 300, 2227},
            {300, 1497, 300, 2131},
            {300, 1497, 300, 2016},
            {300, 1497, 300, 1900},
            {300, 1248, 300, 1785},
            {300, 1248, 300, 1728},
            {300, 1248, 300, 1612},
            {300, 1017, 300, 1497},
            {300, 1017, 300, 1382},
            {300, 1017, 300, 1267},
            {300,  768, 300, 1152},
            {300,  768, 300, 1056},
            {300,  768, 300,  940},
            {300,  576, 300,  806},
            {300,  576, 300,  729},
            {300,  576, 300,  652},
            {300,  300, 300,  478},
            {300,  300, 300,  300}
    };


    public boolean isSupported() {
        return hasPolicy(EFFICIENCY_CLUSTER)
                && hasPolicy(PERFORMANCE_CLUSTER)
                && hasPolicy(PRIME_CLUSTER);
    }

    public String[] getAvailableGovernors() {
        String governors = readOrDefault(path(EFFICIENCY_CLUSTER, SCALING_AVAILABLE_GOVERNORS), "").trim();
        return governors.isEmpty()
                ? new String[]{"schedutil", "performance", "powersave"}
                : governors.split("\\s+");
    }

    /** Raw exact kHz OPPs exported by a specific cpufreq policy. */
    public String[] getAvailableFrequencies(int policy) {
        String frequencies = readOrDefault(path(policy, SCALING_AVAILABLE_FREQUENCIES), "").trim();
        if (frequencies.isEmpty()) return null;
        String[] split = frequencies.split("\\s+");
        Arrays.sort(split, (a, b) -> Long.compare(parsePositive(a), parsePositive(b)));
        return split;
    }

    /** UI list based on the supplied KProfiles little-cluster caps, mapped to exact OPPs. */
    public String[] getEfficiencyUiFrequencies() {
        return buildProfileFrequencyList(EFFICIENCY_CLUSTER, true,
                getCurrentMinFrequency(EFFICIENCY_CLUSTER),
                getCurrentMaxFrequency(EFFICIENCY_CLUSTER));
    }

    /** UI list based on the supplied KProfiles big/prime caps, mapped to exact OPPs. */
    public String[] getPerformanceUiFrequencies() {
        return buildProfileFrequencyList(PRIME_CLUSTER, false,
                getPerformanceCurrentMinFrequency(), getPerformanceCurrentMaxFrequency());
    }

    public String getCurrentGovernor(int policy) {
        return readOrDefault(path(policy, SCALING_GOVERNOR), DEFAULT_GOVERNOR).trim();
    }

    public String getCurrentMinFrequency(int policy) {
        String value = readOrDefault(path(policy, SCALING_MIN_FREQ), "").trim();
        if (!value.isEmpty()) return value;
        String[] frequencies = getAvailableFrequencies(policy);
        return frequencies != null && frequencies.length > 0 ? frequencies[0] : "0";
    }

    public String getCurrentMaxFrequency(int policy) {
        String value = readOrDefault(path(policy, SCALING_MAX_FREQ), "").trim();
        if (!value.isEmpty()) return value;
        String[] frequencies = getAvailableFrequencies(policy);
        return frequencies != null && frequencies.length > 0
                ? frequencies[frequencies.length - 1] : "0";
    }

    public String getPerformanceCurrentMinFrequency() {
        return getCurrentMinFrequency(PRIME_CLUSTER);
    }

    public String getPerformanceCurrentMaxFrequency() {
        return getCurrentMaxFrequency(PRIME_CLUSTER);
    }

    /** Apply one governor consistently to policy0, policy4 and policy7. */
    public boolean setGovernor(String governor) {
        if (governor == null || governor.trim().isEmpty()) return false;
        String requested = governor.trim();
        String[] old = new String[POLICIES.length];
        for (int i = 0; i < POLICIES.length; i++) old[i] = getCurrentGovernor(POLICIES[i]);

        for (int i = 0; i < POLICIES.length; i++) {
            int policy = POLICIES[i];
            if (!writeAndVerify(path(policy, SCALING_GOVERNOR), requested)) {
                for (int j = 0; j < i; j++) {
                    writeSilently(path(POLICIES[j], SCALING_GOVERNOR), old[j]);
                }
                return false;
            }
        }
        return true;
    }

    public boolean setFrequencyRange(int policy, String minFreq, String maxFreq) {
        if (!hasPolicy(policy)) return false;
        long requestedMin = parsePositive(minFreq);
        long requestedMax = parsePositive(maxFreq);
        if (requestedMin <= 0 || requestedMax <= 0 || requestedMin > requestedMax) return false;

        String normalizedMin = normalizeMinToPolicy(policy, requestedMin);
        String normalizedMax = normalizeMaxToPolicy(policy, requestedMax);
        long min = parsePositive(normalizedMin);
        long max = parsePositive(normalizedMax);
        if (min <= 0 || max <= 0 || min > max) return false;

        String minPath = path(policy, SCALING_MIN_FREQ);
        String maxPath = path(policy, SCALING_MAX_FREQ);
        String oldMin = getCurrentMinFrequency(policy);
        String oldMax = getCurrentMaxFrequency(policy);
        long currentMin = parsePositive(oldMin);
        long currentMax = parsePositive(oldMax);

        boolean first;
        boolean second;
        if (currentMax > 0 && min > currentMax) {
            first = writeAndVerify(maxPath, normalizedMax);
            second = first && writeAndVerify(minPath, normalizedMin);
        } else if (currentMin > 0 && max < currentMin) {
            first = writeAndVerify(minPath, normalizedMin);
            second = first && writeAndVerify(maxPath, normalizedMax);
        } else {
            first = writeAndVerify(minPath, normalizedMin);
            second = first && writeAndVerify(maxPath, normalizedMax);
        }
        if (!second) {
            restoreRange(policy, oldMin, oldMax);
            return false;
        }
        return true;
    }

    public boolean setEfficiencyClusterFrequency(String minFreq, String maxFreq) {
        return setFrequencyRange(EFFICIENCY_CLUSTER, minFreq, maxFreq);
    }

    /**
     * The UI exposes cpu4-7 as one Performance Cluster. Apply the selected cap
     * to both policy4 (Gold) and policy7 (Prime), mapping each to its own OPP table.
     */
    public boolean setPerformanceClusterFrequency(String minFreq, String maxFreq) {
        String old4Min = getCurrentMinFrequency(PERFORMANCE_CLUSTER);
        String old4Max = getCurrentMaxFrequency(PERFORMANCE_CLUSTER);
        String old7Min = getCurrentMinFrequency(PRIME_CLUSTER);
        String old7Max = getCurrentMaxFrequency(PRIME_CLUSTER);

        if (!setFrequencyRange(PERFORMANCE_CLUSTER, minFreq, maxFreq)) return false;
        if (!setFrequencyRange(PRIME_CLUSTER, minFreq, maxFreq)) {
            restoreRange(PERFORMANCE_CLUSTER, old4Min, old4Max);
            restoreRange(PRIME_CLUSTER, old7Min, old7Max);
            return false;
        }
        return true;
    }

    public boolean resetToDefaults() {
        boolean ok = setGovernor(DEFAULT_GOVERNOR);
        for (int policy : POLICIES) {
            String[] frequencies = getAvailableFrequencies(policy);
            if (frequencies != null && frequencies.length > 0) {
                ok &= setFrequencyRange(policy, frequencies[0], frequencies[frequencies.length - 1]);
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
        if (!utils.isSupported()) return;

        String governor = prefs.getString(PREF_GOVERNOR,
                utils.getCurrentGovernor(EFFICIENCY_CLUSTER));
        String effMin = prefs.getString(PREF_EFFICIENCY_MIN,
                utils.getCurrentMinFrequency(EFFICIENCY_CLUSTER));
        String effMax = prefs.getString(PREF_EFFICIENCY_MAX,
                utils.getCurrentMaxFrequency(EFFICIENCY_CLUSTER));
        String perfMin = prefs.getString(PREF_PERFORMANCE_MIN,
                utils.getPerformanceCurrentMinFrequency());
        String perfMax = prefs.getString(PREF_PERFORMANCE_MAX,
                utils.getPerformanceCurrentMaxFrequency());

        utils.setGovernor(governor);
        utils.setEfficiencyClusterFrequency(effMin, effMax);
        utils.setPerformanceClusterFrequency(perfMin, perfMax);
    }

    private String[] buildProfileFrequencyList(int policy, boolean little,
            String currentMin, String currentMax) {
        TreeSet<Long> values = new TreeSet<>();
        String[] available = getAvailableFrequencies(policy);
        long[] opps = available != null && available.length > 0
                ? toLongArray(available) : new long[0];
        int minColumn = little ? 0 : 2;
        int maxColumn = little ? 1 : 3;
        for (int[] level : PROFILE_LEVELS_MHZ) {
            addProfileValue(values, opps, level[minColumn]);
            addProfileValue(values, opps, level[maxColumn]);
        }
        long min = parsePositive(currentMin);
        long max = parsePositive(currentMax);
        if (min > 0) values.add(min);
        if (max > 0) values.add(max);

        String[] result = new String[values.size()];
        int i = 0;
        for (Long value : values) result[i++] = Long.toString(value);
        return result;
    }

    private static void addProfileValue(TreeSet<Long> values, long[] opps, int mhz) {
        long target = mhz * 1000L;
        long mapped = opps.length > 0 ? closest(opps, target) : target;
        if (mapped > 0) values.add(mapped);
    }

    private String normalizeMinToPolicy(int policy, long requested) {
        String[] available = getAvailableFrequencies(policy);
        if (available == null || available.length == 0) return Long.toString(requested);
        long[] opps = toLongArray(available);
        for (long opp : opps) if (opp >= requested) return Long.toString(opp);
        return Long.toString(opps[opps.length - 1]);
    }

    private String normalizeMaxToPolicy(int policy, long requested) {
        String[] available = getAvailableFrequencies(policy);
        if (available == null || available.length == 0) return Long.toString(requested);
        long[] opps = toLongArray(available);
        for (int i = opps.length - 1; i >= 0; i--) {
            if (opps[i] <= requested) return Long.toString(opps[i]);
        }
        return Long.toString(opps[0]);
    }

    private void restoreRange(int policy, String oldMin, String oldMax) {
        long min = parsePositive(oldMin);
        long max = parsePositive(oldMax);
        if (min <= 0 || max <= 0 || min > max) return;
        writeSilently(path(policy, SCALING_MAX_FREQ), oldMax);
        writeSilently(path(policy, SCALING_MIN_FREQ), oldMin);
    }

    private static long[] toLongArray(String[] values) {
        List<Long> parsed = new ArrayList<>();
        for (String value : values) {
            long v = parsePositive(value);
            if (v > 0) parsed.add(v);
        }
        long[] result = new long[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) result[i] = parsed.get(i);
        Arrays.sort(result);
        return result;
    }

    private static long closest(long[] values, long target) {
        if (values.length == 0) return -1;
        long best = values[0];
        long bestDelta = Math.abs(best - target);
        for (long value : values) {
            long delta = Math.abs(value - target);
            if (delta < bestDelta) {
                best = value;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static boolean hasPolicy(int policy) {
        return new File(CPU_BASE_PATH + policy).isDirectory();
    }

    private static String path(int policy, String suffix) {
        return CPU_BASE_PATH + policy + suffix;
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
