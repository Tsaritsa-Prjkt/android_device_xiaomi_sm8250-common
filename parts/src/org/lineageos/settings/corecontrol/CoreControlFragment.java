/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.corecontrol;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

/** Preference implementation kept in sync with the dashboard Core Control activity. */
public class CoreControlFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private final CoreControlUtils mUtils = new CoreControlUtils();
    private final SwitchPreferenceCompat[] mCorePrefs =
            new SwitchPreferenceCompat[CoreControlUtils.NUM_CORES];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.core_control_settings, rootKey);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < CoreControlUtils.NUM_CORES; i++) {
            String key = "core_" + i;
            mCorePrefs[i] = findPreference(key);
            if (mCorePrefs[i] == null) continue;
            mCorePrefs[i].setOnPreferenceChangeListener(this);
            mCorePrefs[i].setChecked(mUtils.isCoreOnline(i));
            mCorePrefs[i].setEnabled(mUtils.isCoreControllable(i));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean requestedState = (Boolean) newValue;
        for (int i = 0; i < mCorePrefs.length; i++) {
            if (preference != mCorePrefs[i]) continue;
            if (!requestedState && !mUtils.canOffline(i)) {
                Toast.makeText(requireContext(), R.string.core_control_minimum_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            boolean ok = mUtils.setCoreOnline(i, requestedState);
            if (!ok) {
                Toast.makeText(requireContext(), R.string.parts_apply_failed,
                        Toast.LENGTH_SHORT).show();
            }
            return ok;
        }
        return false;
    }
}
