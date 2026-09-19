/* SPDX-License-Identifier: GPL-2.0-or-later */
package org.lineageos.settings.hbm;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class HBMActivity extends CollapsingToolbarBaseActivity {
    private static final String TAG_HBM = "hbm";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new HBMFragment(), TAG_HBM)
                    .commit();
        }
    }
}
