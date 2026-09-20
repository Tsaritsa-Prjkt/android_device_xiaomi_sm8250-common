/*
 * Copyright (C) 2025 KamiKaonashi
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.lineageos.settings.gpumanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class GpuManagerFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_GPU_CURRENT_FREQ = "gpu_current_freq";
    private static final String KEY_GPU_MODEL = "gpu_model";
    private static final String KEY_GPU_BUSY_PERCENTAGE = "gpu_busy_percentage";
    private static final String KEY_GPU_TEMPERATURE = "gpu_temperature";
    private static final String KEY_GPU_THERMAL_PWRLEVEL = "gpu_thermal_pwrlevel";
    private static final String KEY_APPLY_GPU_SETTINGS = "apply_gpu_settings";
    private static final String KEY_RESET_GPU_SETTINGS = "reset_gpu_settings";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private GpuManagerUtils mGpuUtils;
    private Runnable mUpdateRunnable;

    private ListPreference mGovernorPreference;
    private ListPreference mMinFreqPreference;
    private ListPreference mMaxFreqPreference;
    private Preference mCurrentFreqPreference;
    private Preference mGpuModelPreference;
    private Preference mGpuBusyPreference;
    private Preference mGpuTemperaturePreference;
    private Preference mThermalPowerLevelPreference;
    private SwitchPreferenceCompat mForceClkOnPreference;
    private SwitchPreferenceCompat mForceBusOnPreference;
    private SwitchPreferenceCompat mForceRailOnPreference;
    private SwitchPreferenceCompat mForceNoNapPreference;
    private SwitchPreferenceCompat mBusSplitPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gpu_manager_settings, rootKey);
        mGpuUtils = new GpuManagerUtils();
        initializePreferences();
        loadCurrentSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPeriodicUpdates();
    }

    @Override
    public void onPause() {
        stopPeriodicUpdates();
        super.onPause();
    }

    private void initializePreferences() {
        mGovernorPreference = findPreference(GpuManagerUtils.PREF_GOVERNOR);
        mMinFreqPreference = findPreference(GpuManagerUtils.PREF_MIN_FREQ);
        mMaxFreqPreference = findPreference(GpuManagerUtils.PREF_MAX_FREQ);
        mCurrentFreqPreference = findPreference(KEY_GPU_CURRENT_FREQ);
        mGpuModelPreference = findPreference(KEY_GPU_MODEL);
        mGpuBusyPreference = findPreference(KEY_GPU_BUSY_PERCENTAGE);
        mGpuTemperaturePreference = findPreference(KEY_GPU_TEMPERATURE);
        mThermalPowerLevelPreference = findPreference(KEY_GPU_THERMAL_PWRLEVEL);
        mForceClkOnPreference = findPreference(GpuManagerUtils.PREF_FORCE_CLK_ON);
        mForceBusOnPreference = findPreference(GpuManagerUtils.PREF_FORCE_BUS_ON);
        mForceRailOnPreference = findPreference(GpuManagerUtils.PREF_FORCE_RAIL_ON);
        mForceNoNapPreference = findPreference(GpuManagerUtils.PREF_FORCE_NO_NAP);
        mBusSplitPreference = findPreference(GpuManagerUtils.PREF_BUS_SPLIT);

        setListener(mGovernorPreference);
        setListener(mMinFreqPreference);
        setListener(mMaxFreqPreference);
        setListener(mForceClkOnPreference);
        setListener(mForceBusOnPreference);
        setListener(mForceRailOnPreference);
        setListener(mForceNoNapPreference);
        setListener(mBusSplitPreference);

        Preference applyPref = findPreference(KEY_APPLY_GPU_SETTINGS);
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                applySettings();
                return true;
            });
        }

        Preference resetPref = findPreference(KEY_RESET_GPU_SETTINGS);
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
        if (mGpuModelPreference != null) {
            mGpuModelPreference.setSummary(mGpuUtils.getGpuModel());
        }

        String[] governors = mGpuUtils.getAvailableGovernors();
        if (governors != null && mGovernorPreference != null) {
            mGovernorPreference.setEntries(governors);
            mGovernorPreference.setEntryValues(governors);
            String current = mGpuUtils.getCurrentGovernor();
            mGovernorPreference.setValue(current);
            mGovernorPreference.setSummary(getString(R.string.gpu_governor_summary, current));
        }

        loadFrequencies();
        loadSwitchStates();
        updateDynamicInfo();
    }

    private void loadFrequencies() {
        String[] frequencies = mGpuUtils.getAvailableFrequencies();
        if (frequencies == null || frequencies.length == 0) return;

        String[] labels = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            labels[i] = GpuManagerUtils.formatFrequencyMhz(frequencies[i]) + " MHz";
        }

        if (mMinFreqPreference != null) {
            mMinFreqPreference.setEntries(labels);
            mMinFreqPreference.setEntryValues(frequencies);
            String current = mGpuUtils.getCurrentMinFrequency();
            String selected = nearestValue(frequencies, current);
            mMinFreqPreference.setValue(selected);
            mMinFreqPreference.setSummary(GpuManagerUtils.formatFrequencyMhz(selected) + " MHz");
        }

        if (mMaxFreqPreference != null) {
            mMaxFreqPreference.setEntries(labels);
            mMaxFreqPreference.setEntryValues(frequencies);
            String current = mGpuUtils.getCurrentMaxFrequency();
            String selected = nearestValue(frequencies, current);
            mMaxFreqPreference.setValue(selected);
            mMaxFreqPreference.setSummary(GpuManagerUtils.formatFrequencyMhz(selected) + " MHz");
        }
    }

    private static String nearestValue(String[] values, String selected) {
        if (values == null || values.length == 0) return null;
        if (selected == null) return values[0];
        try {
            long target = Long.parseLong(selected);
            String best = values[0];
            long bestDelta = Math.abs(Long.parseLong(best) - target);
            for (String value : values) {
                long delta = Math.abs(Long.parseLong(value) - target);
                if (delta < bestDelta) {
                    best = value;
                    bestDelta = delta;
                }
            }
            return best;
        } catch (RuntimeException e) {
            return values[0];
        }
    }

    private void loadSwitchStates() {
        configureSwitch(mForceClkOnPreference, mGpuUtils.isForceClkSupported(), mGpuUtils.getForceClkOn());
        configureSwitch(mForceBusOnPreference, mGpuUtils.isForceBusSupported(), mGpuUtils.getForceBusOn());
        configureSwitch(mForceRailOnPreference, mGpuUtils.isForceRailSupported(), mGpuUtils.getForceRailOn());
        configureSwitch(mForceNoNapPreference, mGpuUtils.isForceNoNapSupported(), mGpuUtils.getForceNoNap());
        configureSwitch(mBusSplitPreference, mGpuUtils.isBusSplitSupported(), mGpuUtils.getBusSplit());
        if (mGovernorPreference != null) mGovernorPreference.setEnabled(mGpuUtils.isGovernorSupported());
        boolean freq = mGpuUtils.isFrequencyControlSupported();
        if (mMinFreqPreference != null) mMinFreqPreference.setEnabled(freq);
        if (mMaxFreqPreference != null) mMaxFreqPreference.setEnabled(freq);
    }

    private static void configureSwitch(SwitchPreferenceCompat pref, boolean supported, boolean checked) {
        if (pref == null) return;
        pref.setChecked(supported && checked);
        pref.setEnabled(supported);
    }

    private void updateDynamicInfo() {
        if (mCurrentFreqPreference != null) {
            String value = mGpuUtils.getCurrentFrequency();
            mCurrentFreqPreference.setSummary("0".equals(value)
                    ? getString(R.string.unknown_value) : GpuManagerUtils.formatFrequencyMhz(value) + " MHz");
        }
        if (mGpuBusyPreference != null) {
            String busy = mGpuUtils.getGpuBusyPercentage();
            mGpuBusyPreference.setSummary(busy.endsWith("%") ? busy : busy + "%");
        }
        if (mGpuTemperaturePreference != null) {
            String temp = mGpuUtils.getGpuTemperature();
            mGpuTemperaturePreference.setSummary("0".equals(temp)
                    ? getString(R.string.unknown_value) : temp + "°C");
        }
        if (mThermalPowerLevelPreference != null) {
            mThermalPowerLevelPreference.setSummary(
                    getString(R.string.gpu_thermal_level_value, mGpuUtils.getThermalPowerLevel()));
        }
    }

    private void startPeriodicUpdates() {
        stopPeriodicUpdates();
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                updateDynamicInfo();
                mHandler.postDelayed(this, 2000);
            }
        };
        mHandler.post(mUpdateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (mUpdateRunnable != null) mHandler.removeCallbacks(mUpdateRunnable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (GpuManagerUtils.PREF_GOVERNOR.equals(key)) {
            preference.setSummary(getString(R.string.gpu_governor_summary, String.valueOf(newValue)));
        } else if (GpuManagerUtils.PREF_MIN_FREQ.equals(key)
                || GpuManagerUtils.PREF_MAX_FREQ.equals(key)) {
            preference.setSummary(GpuManagerUtils.formatFrequencyMhz(String.valueOf(newValue)) + " MHz");
        }
        // Power switches are staged until Apply Settings, matching the screenshot flow.
        return true;
    }

    private void applySettings() {
        boolean ok = true;
        if (mGovernorPreference != null && mGpuUtils.isGovernorSupported()) {
            ok &= mGpuUtils.setGovernor(mGovernorPreference.getValue());
        }
        if (mMinFreqPreference != null && mMaxFreqPreference != null
                && mGpuUtils.isFrequencyControlSupported()) {
            ok &= mGpuUtils.setFrequencyRange(
                    mMinFreqPreference.getValue(), mMaxFreqPreference.getValue());
        }
        if (mForceClkOnPreference != null && mGpuUtils.isForceClkSupported()) ok &= mGpuUtils.setForceClkOn(mForceClkOnPreference.isChecked());
        if (mForceBusOnPreference != null && mGpuUtils.isForceBusSupported()) ok &= mGpuUtils.setForceBusOn(mForceBusOnPreference.isChecked());
        if (mForceRailOnPreference != null && mGpuUtils.isForceRailSupported()) ok &= mGpuUtils.setForceRailOn(mForceRailOnPreference.isChecked());
        if (mForceNoNapPreference != null && mGpuUtils.isForceNoNapSupported()) ok &= mGpuUtils.setForceNoNap(mForceNoNapPreference.isChecked());
        if (mBusSplitPreference != null && mGpuUtils.isBusSplitSupported()) ok &= mGpuUtils.setBusSplit(mBusSplitPreference.isChecked());

        Toast.makeText(requireContext(), ok ? R.string.settings_applied : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
        if (!ok) loadCurrentSettings();
    }

    private void resetSettings() {
        boolean ok = mGpuUtils.resetToDefaults();
        loadCurrentSettings();
        Toast.makeText(requireContext(), ok ? R.string.settings_reset : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

}
