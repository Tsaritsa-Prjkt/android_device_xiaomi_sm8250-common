/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.display.AutoHBMService;
import org.lineageos.settings.display.DisplayNodes;
import org.lineageos.settings.display.HBMController;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.refreshrate.RefreshUtils;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;
import org.lineageos.settings.utils.FileUtils;
import android.os.IBinder;
import android.content.IntentFilter;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "XiaomiParts";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (DEBUG)
            Log.d(TAG, "Received boot completed intent");
        try {
            DiracUtils.getInstance(context);
        } catch (Exception e) {
            Log.w(TAG, "Cannot initialize MiSound", e);
        }
        ThermalUtils.startService(context);
        RefreshUtils.startService(context);
        TouchSamplingUtils.restoreSamplingValue(context);
        overrideHdrTypes(context);
    }

    private static void overrideHdrTypes(Context context) {

        // Override HDR types to enable Dolby Vision
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        dm.overrideHdrTypes(Display.DEFAULT_DISPLAY, new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS});

        // DC Dimming
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean dcDimmingEnabled = sharedPrefs.getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
        FileUtils.writeLine(DisplayNodes.getDcDimmingNode(), dcDimmingEnabled ? "1" : "0");

        boolean autoHbmEnabled = sharedPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false);
        if (autoHbmEnabled) {
            sharedPrefs.edit().putBoolean(DisplayNodes.getHbmEnableKey(), false).apply();
            HBMController.disable(context, sharedPrefs);
            AutoHBMService.start(context);
        } else {
            boolean hbmEnabled = sharedPrefs.getBoolean(DisplayNodes.getHbmEnableKey(), false);
            if (hbmEnabled) {
                HBMController.enable(context, sharedPrefs);
            } else {
                HBMController.disable(context, sharedPrefs);
            }
        }
    }
}
