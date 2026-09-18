/*
 * Copyright (C) 2018 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class DisplaySettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private SwitchPreferenceCompat mDcDimmingPreference;
    private SwitchPreferenceCompat mHbmPreference;
    private SwitchPreferenceCompat mAutoHbmPreference;
    private SeekBarPreference mAutoHbmThresholdPreference;
    private SeekBarPreference mHbmDisableTimePreference;
    private SharedPreferences mPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.display_settings);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mDcDimmingPreference = findPreference(DisplayNodes.getDcDimmingEnableKey());
        mHbmPreference = findPreference(DisplayNodes.getHbmEnableKey());
        mAutoHbmPreference = findPreference(DisplayNodes.getAutoHbmEnableKey());
        mAutoHbmThresholdPreference = findPreference(DisplayNodes.getAutoHbmThresholdKey());
        mHbmDisableTimePreference = findPreference(DisplayNodes.getAutoHbmDisableTimeKey());

        mDcDimmingPreference.setPersistent(false);
        mHbmPreference.setPersistent(false);
        mAutoHbmPreference.setPersistent(false);

        mDcDimmingPreference.setOnPreferenceChangeListener(this);
        mHbmPreference.setOnPreferenceChangeListener(this);
        mAutoHbmPreference.setOnPreferenceChangeListener(this);

        if (!DisplayUtils.isAutoHbmSupported(requireContext())
                && mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)) {
            mPrefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
            AutoHBMService.stop(requireContext());
        }
        refreshState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        boolean dcSupported = DisplayUtils.isDcDimmingSupported();
        boolean dcEnabled = dcSupported && DisplayUtils.isDcDimmingEnabled();
        mDcDimmingPreference.setEnabled(dcSupported);
        mDcDimmingPreference.setChecked(dcEnabled);
        mDcDimmingPreference.setSummary(dcSupported
                ? R.string.dc_dimming_enable_summary
                : R.string.dc_dimming_enable_summary_not_supported);

        boolean hbmSupported = DisplayUtils.isHbmSupported();
        boolean autoSupported = DisplayUtils.isAutoHbmSupported(requireContext());
        boolean autoEnabled = autoSupported
                && mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false);

        mAutoHbmPreference.setEnabled(autoSupported);
        mAutoHbmPreference.setChecked(autoEnabled);
        mAutoHbmPreference.setSummary(autoSupported
                ? R.string.auto_hbm_summary
                : R.string.auto_hbm_summary_not_supported);

        // Manual HBM and DC dimming should not fight each other or Auto HBM.
        mHbmPreference.setEnabled(hbmSupported && !autoEnabled && !dcEnabled);
        mHbmPreference.setChecked(!autoEnabled && hbmSupported && DisplayUtils.isHbmEnabled());
        mHbmPreference.setSummary(hbmSupported
                ? R.string.hbm_mode_summary
                : R.string.hbm_enable_summary_not_supported);

        mAutoHbmThresholdPreference.setEnabled(autoSupported && autoEnabled);
        mHbmDisableTimePreference.setEnabled(autoSupported && autoEnabled);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        boolean enabled = (Boolean) newValue;
        boolean applied;

        if (DisplayNodes.getDcDimmingEnableKey().equals(key)) {
            applied = DisplayUtils.setDcDimming(requireContext(), enabled);
        } else if (DisplayNodes.getHbmEnableKey().equals(key)) {
            if (mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)
                    || DisplayUtils.isDcDimmingEnabled()) {
                return false;
            }
            applied = DisplayUtils.setHbm(requireContext(), enabled);
        } else if (DisplayNodes.getAutoHbmEnableKey().equals(key)) {
            if (!DisplayUtils.isAutoHbmSupported(requireContext())) return false;

            if (enabled) {
                if (!DisplayUtils.setHbm(requireContext(), false)) return false;
                mPrefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), true).apply();
                if (!AutoHBMService.start(requireContext())) {
                    mPrefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
                    Toast.makeText(requireContext(), R.string.parts_apply_failed,
                            Toast.LENGTH_SHORT).show();
                    refreshState();
                    return false;
                }
            } else {
                mPrefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
                AutoHBMService.stop(requireContext());
                HBMController.disable(requireContext(), mPrefs);
            }
            applied = true;
        } else {
            return false;
        }

        if (!applied) {
            Toast.makeText(requireContext(), R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        refreshState();
        return applied;
    }
}
