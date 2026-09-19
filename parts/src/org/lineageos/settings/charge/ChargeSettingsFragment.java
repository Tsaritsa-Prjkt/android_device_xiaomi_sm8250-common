/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.charge;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class ChargeSettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_BYPASS_CHARGE = "bypass_charge";

    private ChargeUtils mChargeUtils;
    private SwitchPreferenceCompat mPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_settings, rootKey);
        mChargeUtils = new ChargeUtils(requireContext());
        mPreference = findPreference(KEY_BYPASS_CHARGE);
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
        boolean supported = mChargeUtils.isBypassChargeSupported();
        mPreference.setEnabled(supported);
        mPreference.setChecked(supported && mChargeUtils.isBypassChargeEnabled());
        mPreference.setSummary(supported ? R.string.charge_bypass_summary : R.string.charge_bypass_unavailable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!KEY_BYPASS_CHARGE.equals(preference.getKey())) return false;
        boolean enable = (Boolean) newValue;
        if (!enable) {
            boolean applied = mChargeUtils.setBypassChargeEnabled(false);
            if (!applied) Toast.makeText(requireContext(), R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
            BypassChargeTileService.updateTile(requireContext());
            return applied;
        }

        ChargeUtils.SafetyCheckResult safety = mChargeUtils.performSafetyChecks();
        if (!safety.isSafe()) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.charge_bypass_title)
                    .setMessage(getString(R.string.charge_bypass_safety_failed, safety.getReason()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return false;
        }

        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.charge_bypass_title)
                .setMessage(R.string.charge_bypass_warning)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    boolean applied = mChargeUtils.setBypassChargeEnabled(true);
                    refreshState();
                    BypassChargeTileService.updateTile(requireContext());
                    if (!applied) {
                        Toast.makeText(requireContext(), R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return false;
    }
}
