/*
 * Copyright (C) 2024-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

final class SaturationController {

    private static final String TAG = "SaturationController";
    private static final String SURFACE_COMPOSER_TOKEN = "android.ui.ISurfaceComposer";
    private static final int TRANSACTION_SET_SATURATION = 1022;

    private IBinder mSurfaceFlinger;

    SaturationController() {
        mSurfaceFlinger = ServiceManager.getService("SurfaceFlinger");
    }

    void apply(int seekBarValue) {
        if (mSurfaceFlinger == null) {
            mSurfaceFlinger = ServiceManager.getService("SurfaceFlinger");
            if (mSurfaceFlinger == null) {
                Log.e(TAG, "SurfaceFlinger service is unavailable");
                return;
            }
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(SURFACE_COMPOSER_TOKEN);
            data.writeFloat(SaturationControllerCore.toSurfaceFlingerValue(seekBarValue));
            mSurfaceFlinger.transact(TRANSACTION_SET_SATURATION, data, null, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to update display saturation", e);
            mSurfaceFlinger = null;
            return;
        } finally {
            data.recycle();
        }
    }
}
