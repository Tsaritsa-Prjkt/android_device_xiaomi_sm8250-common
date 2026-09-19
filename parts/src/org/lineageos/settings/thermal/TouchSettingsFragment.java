/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.thermal;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class TouchSettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    private SharedPreferences mSharedPrefs;
    private SeekBarPreference mTouchSensitivity;
    private SeekBarPreference mTouchResponse;
    private SeekBarPreference mTouchResistant;
    private MainSwitchPreference mGameMode;
    private String mPackageName = "";
    private boolean mUpdating;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.touch_settings);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(
                requireContext().createDeviceProtectedStorageContext());

        Bundle args = getArguments();
        if (args != null) mPackageName = args.getString("packageName", "");
        requireActivity().setTitle(R.string.touch_control_title);

        mGameMode = findPreference(Constants.PREF_TOUCH_GAME_MODE);
        mTouchResistant = findPreference(Constants.PREF_TOUCH_RESISTANT);
        mTouchResponse = findPreference(Constants.PREF_TOUCH_RESPONSE);
        mTouchSensitivity = findPreference(Constants.PREF_TOUCH_SENSITIVITY);

        mGameMode.setPersistent(false);
        mTouchResistant.setPersistent(false);
        mTouchResponse.setPersistent(false);
        mTouchSensitivity.setPersistent(false);

        mGameMode.addOnSwitchChangeListener(this);
        mTouchResistant.setOnPreferenceChangeListener(this);
        mTouchResponse.setOnPreferenceChangeListener(this);
        mTouchSensitivity.setOnPreferenceChangeListener(this);
        updateUi(readTuple());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int[] tuple = readTuple();
        int value = (Integer) newValue;
        if (preference == mTouchResponse) tuple[Constants.TOUCH_RESPONSE] = value;
        else if (preference == mTouchSensitivity) tuple[Constants.TOUCH_SENSITIVITY] = value;
        else if (preference == mTouchResistant) tuple[Constants.TOUCH_RESISTANT] = value;
        else return false;
        writeTuple(tuple);
        ThermalUtils.startService(requireContext());
        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mUpdating) return;
        int[] tuple = readTuple();
        tuple[Constants.TOUCH_GAME_MODE] = isChecked ? 1 : 0;
        writeTuple(tuple);
        setSlidersEnabled(isChecked);
        ThermalUtils.startService(requireContext());
    }

    private int[] readTuple() {
        String raw = mSharedPrefs.getString(mPackageName, "0,0,0,0");
        String[] parts = raw == null ? new String[0] : raw.split(",", -1);
        int[] out = new int[] {0, 0, 0, 0};
        if (parts.length == 4) {
            try {
                for (int i = 0; i < 4; i++) out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out = new int[] {0, 0, 0, 0};
            }
        }
        return out;
    }

    private void writeTuple(int[] tuple) {
        String value = tuple[0] + "," + tuple[1] + "," + tuple[2] + "," + tuple[3];
        mSharedPrefs.edit().putString(mPackageName, value).apply();
    }

    private void updateUi(int[] tuple) {
        boolean enabled = tuple[Constants.TOUCH_GAME_MODE] == 1;
        mUpdating = true;
        mGameMode.setChecked(enabled);
        mUpdating = false;
        mTouchResponse.setValue(tuple[Constants.TOUCH_RESPONSE]);
        mTouchSensitivity.setValue(tuple[Constants.TOUCH_SENSITIVITY]);
        mTouchResistant.setValue(tuple[Constants.TOUCH_RESISTANT]);
        setSlidersEnabled(enabled);
    }

    private void setSlidersEnabled(boolean enabled) {
        mTouchResponse.setEnabled(enabled);
        mTouchSensitivity.setEnabled(enabled);
        mTouchResistant.setEnabled(enabled);
    }
}
