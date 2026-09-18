/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.saturation;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;

public final class SaturationUtils {
    private static final String TAG = "SaturationUtils";
    private static final int SURFACE_FLINGER_TRANSACTION_SET_SATURATION = 1022;
    private static final int DEFAULT_SATURATION = 100;

    private SaturationUtils() {}

    public static boolean setSaturation(int value) {
        int clamped = Math.max(0, Math.min(200, value));
        float saturation = clamped == 100 ? 1.001f : clamped / 100.0f;
        IBinder surfaceFlinger = ServiceManager.getService("SurfaceFlinger");
        if (surfaceFlinger == null) {
            Log.w(TAG, "SurfaceFlinger service is unavailable");
            return false;
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeFloat(saturation);
            return surfaceFlinger.transact(
                    SURFACE_FLINGER_TRANSACTION_SET_SATURATION, data, null, 0);
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to apply display saturation", e);
            return false;
        } finally {
            data.recycle();
        }
    }

    public static boolean restore(Context context) {
        int value = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(Constants.KEY_SATURATION, DEFAULT_SATURATION);
        return setSaturation(value);
    }
}
