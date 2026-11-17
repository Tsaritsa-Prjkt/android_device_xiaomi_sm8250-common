// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.kcal;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/** Request composition after the sysfs write, without changing any color transform. */
final class KcalDisplay {
    // SurfaceFlinger::onTransact: force composition at the next VSYNC.
    private static final int FORCE_COMPOSITE = 1004;

    private KcalDisplay() {}

    static void refresh() {
        IBinder flinger = ServiceManager.checkService("SurfaceFlinger");
        if (flinger == null) return; // During early boot, the first display commit applies KCAL.
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            if (!flinger.transact(FORCE_COMPOSITE, data, null, 0)) {
                Log.w("XiaomiParts", "KCAL refresh unsupported; waiting for next display frame");
            }
        } catch (RemoteException | SecurityException e) {
            Log.w("XiaomiParts", "KCAL refresh failed; waiting for next display frame", e);
        } finally {
            data.recycle();
        }
    }
}
