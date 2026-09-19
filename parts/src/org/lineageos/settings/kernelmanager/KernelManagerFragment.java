/*
 * Copyright (C) 2025 KamiKaonashi
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.lineageos.settings.kernelmanager;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class KernelManagerFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_APPLY_SETTINGS = "apply_settings";
    private static final String KEY_RESET_SETTINGS = "reset_settings";

    private KernelManagerUtils mKernelUtils;
    private ListPreference mGovernorPreference;
    private ListPreference mEfficiencyMinFreq;
    private ListPreference mEfficiencyMaxFreq;
    private ListPreference mPerformanceMinFreq;
    private ListPreference mPerformanceMaxFreq;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.kernel_manager_settings, rootKey);
        mKernelUtils = new KernelManagerUtils();
        initializePreferences();
        loadCurrentSettings();
    }

    private void initializePreferences() {
        mGovernorPreference = findPreference(KernelManagerUtils.PREF_GOVERNOR);
        mEfficiencyMinFreq = findPreference(KernelManagerUtils.PREF_EFFICIENCY_MIN);
        mEfficiencyMaxFreq = findPreference(KernelManagerUtils.PREF_EFFICIENCY_MAX);
        mPerformanceMinFreq = findPreference(KernelManagerUtils.PREF_PERFORMANCE_MIN);
        mPerformanceMaxFreq = findPreference(KernelManagerUtils.PREF_PERFORMANCE_MAX);

        setListener(mGovernorPreference);
        setListener(mEfficiencyMinFreq);
        setListener(mEfficiencyMaxFreq);
        setListener(mPerformanceMinFreq);
        setListener(mPerformanceMaxFreq);

        Preference applyPref = findPreference(KEY_APPLY_SETTINGS);
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                applySettings();
                return true;
            });
        }

        Preference resetPref = findPreference(KEY_RESET_SETTINGS);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                resetSettings();
                return true;
            });
        }
    }

    private void setListener(Preference preference) {
        if (preference != null) preference.setOnPreferenceChangeListener(this);
    }

    private void loadCurrentSettings() {
        String[] governors = mKernelUtils.getAvailableGovernors();
        if (governors != null && mGovernorPreference != null) {
            mGovernorPreference.setEntries(governors);
            mGovernorPreference.setEntryValues(governors);
            String current = mKernelUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER);
            mGovernorPreference.setValue(current);
            mGovernorPreference.setSummary(getString(R.string.cpu_governor_summary, current));
        }

        loadFrequenciesForCluster(KernelManagerUtils.EFFICIENCY_CLUSTER,
                mEfficiencyMinFreq, mEfficiencyMaxFreq);
        loadFrequenciesForCluster(KernelManagerUtils.PERFORMANCE_CLUSTER,
                mPerformanceMinFreq, mPerformanceMaxFreq);
    }

    private void loadFrequenciesForCluster(int cluster, ListPreference minPref, ListPreference maxPref) {
        String[] frequencies = mKernelUtils.getAvailableFrequencies(cluster);
        if (frequencies == null || frequencies.length == 0) return;

        String[] labels = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            labels[i] = toCpuMhz(frequencies[i]) + " MHz";
        }

        if (minPref != null) {
            minPref.setEntries(labels);
            minPref.setEntryValues(frequencies);
            String current = mKernelUtils.getCurrentMinFrequency(cluster);
            minPref.setValue(current);
            minPref.setSummary(toCpuMhz(current) + " MHz");
        }
        if (maxPref != null) {
            maxPref.setEntries(labels);
            maxPref.setEntryValues(frequencies);
            String current = mKernelUtils.getCurrentMaxFrequency(cluster);
            maxPref.setValue(current);
            maxPref.setSummary(toCpuMhz(current) + " MHz");
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String value = String.valueOf(newValue);
        if (KernelManagerUtils.PREF_GOVERNOR.equals(key)) {
            preference.setSummary(getString(R.string.cpu_governor_summary, value));
            return true;
        }
        if (key != null && key.contains("freq")) {
            preference.setSummary(toCpuMhz(value) + " MHz");
            return true;
        }
        return true;
    }

    private void applySettings() {
        boolean ok = true;
        if (mGovernorPreference != null) {
            ok &= mKernelUtils.setGovernor(mGovernorPreference.getValue());
        }
        if (mEfficiencyMinFreq != null && mEfficiencyMaxFreq != null) {
            ok &= mKernelUtils.setEfficiencyClusterFrequency(
                    mEfficiencyMinFreq.getValue(), mEfficiencyMaxFreq.getValue());
        }
        if (mPerformanceMinFreq != null && mPerformanceMaxFreq != null) {
            ok &= mKernelUtils.setPerformanceClusterFrequency(
                    mPerformanceMinFreq.getValue(), mPerformanceMaxFreq.getValue());
        }
        Toast.makeText(requireContext(), ok ? R.string.settings_applied : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
        if (!ok) loadCurrentSettings();
    }

    private void resetSettings() {
        boolean ok = mKernelUtils.resetToDefaults();
        loadCurrentSettings();
        Toast.makeText(requireContext(), ok ? R.string.settings_reset : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

    private static long toCpuMhz(String khz) {
        try { return Long.parseLong(khz) / 1000L; } catch (RuntimeException e) { return 0; }
    }
}
