/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.hbm;

import android.content.Context;
import android.widget.Toast;

import androidx.preference.Preference;

import org.lineageos.settings.R;
import org.lineageos.settings.display.DisplayUtils;

public class HBMModeSwitch implements Preference.OnPreferenceChangeListener {
    private final Context mContext;

    public HBMModeSwitch(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean applied = DisplayUtils.setHbm(mContext, (Boolean) newValue);
        if (!applied) {
            Toast.makeText(mContext, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        return applied;
    }
}
