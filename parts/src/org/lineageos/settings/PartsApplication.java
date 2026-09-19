/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings;

import android.app.Application;
import android.util.Log;

import org.lineageos.settings.dirac.DiracUtils;

/**
 * Persistent process owner for device features that need a stable process lifetime.
 * MiSound in particular uses a session-0 AudioEffect and must be recreated when the
 * XiaomiParts process is restarted independently of the boot receiver.
 */
public class PartsApplication extends Application {
    private static final String TAG = "XiaomiParts";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            DiracUtils.getInstance(this);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot initialize MiSound", e);
        }
    }
}
