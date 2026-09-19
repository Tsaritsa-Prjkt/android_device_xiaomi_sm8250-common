/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;
import org.lineageos.settings.display.DisplayUtils;
import org.lineageos.settings.utils.FileUtils;

public class HBMFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String KEY_HBM_SWITCH = "hbm";
    public static final String KEY_AUTO_HBM_SWITCH = "auto_hbm";
    public static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";
    public static final String KEY_HBM_DISABLE_TIME = "hbm_disable_time";

    private TwoStatePreference mHbmSwitch;
    private TwoStatePreference mAutoHbmSwitch;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.hbm_settings);

        mHbmSwitch = findPreference(KEY_HBM_SWITCH);
        mHbmSwitch.setPersistent(false);
        mHbmSwitch.setOnPreferenceChangeListener(new HBMModeSwitch(requireContext()));

        mAutoHbmSwitch = findPreference(KEY_AUTO_HBM_SWITCH);
        mAutoHbmSwitch.setOnPreferenceChangeListener(this);
        refreshState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        if (mHbmSwitch != null) {
            boolean supported = DisplayUtils.isHbmSupported();
            mHbmSwitch.setEnabled(supported && !DisplayUtils.isDcDimmingEnabled());
            mHbmSwitch.setChecked(supported && DisplayUtils.isHbmEnabled(requireContext()));
        }
        if (mAutoHbmSwitch != null) {
            mAutoHbmSwitch.setChecked(isAUTOHBMEnabled(requireContext()));
            mAutoHbmSwitch.setEnabled(DisplayUtils.isHbmSupported());
        }
    }

    public static boolean isAUTOHBMEnabled(Context context) {
        Context storage = context.getApplicationContext().createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(storage)
                .getBoolean(KEY_AUTO_HBM_SWITCH, false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference != mAutoHbmSwitch) return false;
        Context storage = requireContext().getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(storage).edit();
        editor.putBoolean(KEY_AUTO_HBM_SWITCH, (Boolean) newValue).apply();
        FileUtils.enableService(requireContext());
        return true;
    }
}
