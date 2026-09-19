/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class DcDimmingSettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String KEY_DC_DIMMING = "dc_dimming_enable";
    private SwitchPreferenceCompat mPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dcdimming_settings, rootKey);
        mPreference = findPreference(KEY_DC_DIMMING);
        mPreference.setPersistent(false);
        mPreference.setOnPreferenceChangeListener(this);
        refreshState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        if (mPreference == null) return;
        boolean supported = DisplayUtils.isDcDimmingSupported();
        mPreference.setEnabled(supported);
        mPreference.setChecked(supported && DisplayUtils.isDcDimmingEnabled());
        if (!supported) {
            mPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
        } else {
            mPreference.setSummary(R.string.dc_dimming_enable_summary);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!KEY_DC_DIMMING.equals(preference.getKey())) return false;
        boolean applied = DisplayUtils.setDcDimming(requireContext(), (Boolean) newValue);
        if (!applied) {
            Toast.makeText(requireContext(), R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        refreshState();
        return applied;
    }
}
