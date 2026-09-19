/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.charge;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import org.lineageos.settings.R;

/** Screenshot-styled direct bypass-charging control. */
public class ChargeActivity extends Activity {
    private ChargeUtils mUtils;
    private Switch mSwitch;
    private boolean mRefreshing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bypass_charge);
        findViewById(R.id.xp_back).setOnClickListener(v -> finish());
        mUtils = new ChargeUtils(this);
        mSwitch = findViewById(R.id.bypass_switch);
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mRefreshing) return;
            if (!checked) {
                boolean ok = mUtils.setBypassChargeEnabled(false);
                if (!ok) Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
                refresh();
                BypassChargeTileService.updateTile(this);
                return;
            }
            ChargeUtils.SafetyCheckResult safety = mUtils.performSafetyChecks();
            if (!safety.isSafe()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.charge_bypass_title)
                        .setMessage(getString(R.string.charge_bypass_safety_failed, safety.getReason()))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                refresh();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.charge_bypass_title)
                    .setMessage(R.string.charge_bypass_warning)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        boolean ok = mUtils.setBypassChargeEnabled(true);
                        if (!ok) Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
                        refresh();
                        BypassChargeTileService.updateTile(this);
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> refresh())
                    .setOnCancelListener(dialog -> refresh())
                    .show();
            refresh();
        });
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (mSwitch == null) return;
        mRefreshing = true;
        boolean supported = mUtils.isBypassChargeSupported();
        mSwitch.setEnabled(supported);
        mSwitch.setChecked(supported && mUtils.isBypassChargeEnabled());
        mRefreshing = false;
    }
}
