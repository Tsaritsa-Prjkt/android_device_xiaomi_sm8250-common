/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.touchsampling;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class TouchSamplingSettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String HTSR_ENABLE_KEY = "htsr_enable";
    public static final String SHAREDHTSR = "SHAREDHTSR";
    private SwitchPreferenceCompat mPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.htsr_settings);
        mPreference = findPreference(HTSR_ENABLE_KEY);
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
        boolean supported = TouchSamplingUtils.isSupported();
        mPreference.setEnabled(supported);
        mPreference.setChecked(supported && TouchSamplingUtils.isEnabled(requireContext()));
        mPreference.setSummary(supported
                ? R.string.htsr_enable_summary
                : R.string.htsr_enable_summary_not_supported);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!HTSR_ENABLE_KEY.equals(preference.getKey())) return false;
        boolean applied = TouchSamplingUtils.setEnabled(requireContext(), (Boolean) newValue);
        if (!applied) {
            Toast.makeText(requireContext(), R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        return applied;
    }
}
