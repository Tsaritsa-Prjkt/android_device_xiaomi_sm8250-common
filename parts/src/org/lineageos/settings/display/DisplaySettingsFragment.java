/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class DisplaySettingsFragment extends SettingsBasePreferenceFragment implements
        OnPreferenceChangeListener {

    private SwitchPreferenceCompat mDcDimmingPreference;
    private SwitchPreferenceCompat mHBMPreference;
    private SwitchPreferenceCompat mAutoHBMPreference;
    private SeekBarPreference mAutoHBMThresholdPreference;
    private SeekBarPreference mHBMDisableTimePreference;

    private String mDcDimmingEnableKey;
    private String mDcDimmingNode;
    private String mHbmEnableKey;
    private String mAutoHbmEnableKey;
    private String mHbmNode;
    private String mBacklightNode;

    private SharedPreferences mPrefs;
    private boolean mHbmSupported;
    private boolean mAutoHbmSupported;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mDcDimmingEnableKey = DisplayNodes.getDcDimmingEnableKey();
        mDcDimmingNode = DisplayNodes.getDcDimmingNode();
        mHbmEnableKey = DisplayNodes.getHbmEnableKey();
        mAutoHbmEnableKey = DisplayNodes.getAutoHbmEnableKey();
        mHbmNode = DisplayNodes.getHbmNode();
        mBacklightNode = DisplayNodes.getBacklight();

        addPreferencesFromResource(R.xml.display_settings);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        mDcDimmingPreference = findPreference(mDcDimmingEnableKey);
        mHBMPreference = findPreference(mHbmEnableKey);
        mAutoHBMPreference = findPreference(mAutoHbmEnableKey);
        mAutoHBMThresholdPreference = findPreference(DisplayNodes.getAutoHbmThresholdKey());
        mHBMDisableTimePreference = findPreference(DisplayNodes.getAutoHbmDisableTimeKey());

        if (FileUtils.fileExists(mDcDimmingNode)) {
            mDcDimmingPreference.setEnabled(true);
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            mDcDimmingPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
            mDcDimmingPreference.setEnabled(false);
        }

        mHbmSupported = FileUtils.fileExists(mHbmNode);
        mAutoHbmSupported = mHbmSupported && hasLightSensor(requireContext());

        if (mHbmSupported) {
            mHBMPreference.setOnPreferenceChangeListener(this);
        } else {
            mHBMPreference.setSummary(R.string.hbm_enable_summary_not_supported);
        }

        mAutoHBMPreference.setPersistent(false);
        mAutoHBMPreference.setChecked(mPrefs.getBoolean(mAutoHbmEnableKey, false));
        mAutoHBMPreference.setOnPreferenceChangeListener(this);
        if (!mAutoHbmSupported) {
            mAutoHBMPreference.setChecked(false);
            mAutoHBMPreference.setSummary(R.string.auto_hbm_summary_not_supported);
            mPrefs.edit().putBoolean(mAutoHbmEnableKey, false).apply();
            AutoHBMService.stop(requireContext());
        }

        updateHbmPreferenceState();
    }

    private static boolean hasLightSensor(Context context) {
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        return sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    private void updateHbmPreferenceState() {
        boolean autoEnabled = mAutoHBMPreference.isChecked() && mAutoHbmSupported;
        mHBMPreference.setEnabled(mHbmSupported && !autoEnabled);
        mAutoHBMPreference.setEnabled(mAutoHbmSupported);
        mAutoHBMThresholdPreference.setEnabled(mAutoHbmSupported && autoEnabled);
        mHBMDisableTimePreference.setEnabled(mAutoHbmSupported && autoEnabled);
    }

    private void disableManualHbm() {
        FileUtils.writeLine(mHbmNode, "0");
        mPrefs.edit().putBoolean(mHbmEnableKey, false).apply();
        mHBMPreference.setChecked(false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mDcDimmingEnableKey.equals(preference.getKey())) {
            FileUtils.writeLine(mDcDimmingNode, (Boolean) newValue ? "1" : "0");
            return true;
        }

        if (mHbmEnableKey.equals(preference.getKey())) {
            if (mPrefs.getBoolean(mAutoHbmEnableKey, false)) {
                return false;
            }

            boolean enabled = (Boolean) newValue;
            FileUtils.writeLine(mHbmNode, enabled ? "1" : "0");
            if (enabled) {
                FileUtils.writeLine(mBacklightNode, "2047");
                Settings.System.putInt(requireContext().getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 255);
            }
            return true;
        }

        if (mAutoHbmEnableKey.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            if (!mAutoHbmSupported) {
                return false;
            }

            mPrefs.edit().putBoolean(mAutoHbmEnableKey, enabled).apply();
            mAutoHBMPreference.setChecked(enabled);

            if (enabled) {
                disableManualHbm();
                AutoHBMService.start(requireContext());
            } else {
                AutoHBMService.stop(requireContext());
            }

            updateHbmPreferenceState();
            return true;
        }

        return false;
    }
}
