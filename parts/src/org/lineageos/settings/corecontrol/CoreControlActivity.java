/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.corecontrol;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import org.lineageos.settings.R;

/** Screenshot-styled per-core control page backed by real CPU online sysfs nodes. */
public class CoreControlActivity extends Activity {
    private final CoreControlUtils mUtils = new CoreControlUtils();
    private Switch[] mSwitches;
    private boolean mRefreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_core_control);
        findViewById(R.id.xp_back).setOnClickListener(v -> finish());

        int[] ids = {
                R.id.core_0_switch, R.id.core_1_switch, R.id.core_2_switch, R.id.core_3_switch,
                R.id.core_4_switch, R.id.core_5_switch, R.id.core_6_switch, R.id.core_7_switch
        };
        mSwitches = new Switch[CoreControlUtils.NUM_CORES];
        for (int i = 0; i < mSwitches.length; i++) {
            final int core = i;
            mSwitches[i] = findViewById(ids[i]);
            mSwitches[i].setOnCheckedChangeListener((button, checked) -> {
                if (mRefreshing) return;
                if (!checked && !mUtils.canOffline(core)) {
                    Toast.makeText(this, R.string.core_control_minimum_error,
                            Toast.LENGTH_SHORT).show();
                    refresh();
                    return;
                }
                if (!mUtils.setCoreOnline(core, checked)) {
                    Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
                    refresh();
                }
            });
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (mSwitches == null) return;
        mRefreshing = true;
        for (int i = 0; i < mSwitches.length; i++) {
            boolean controllable = mUtils.isCoreControllable(i);
            mSwitches[i].setChecked(mUtils.isCoreOnline(i));
            mSwitches[i].setEnabled(controllable);
            mSwitches[i].setAlpha(controllable ? 1.0f : 0.65f);
        }
        mRefreshing = false;
    }
}
