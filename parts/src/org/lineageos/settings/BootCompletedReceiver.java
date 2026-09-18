/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.Display.HdrCapabilities;

import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.display.DisplayUtils;
import org.lineageos.settings.refreshrate.RefreshUtils;
import org.lineageos.settings.saturation.SaturationUtils;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "XiaomiParts";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        try {
            DiracUtils.getInstance(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot initialize MiSound", e);
        }

        ThermalUtils.startService(context);
        RefreshUtils.startService(context);
        TouchSamplingUtils.restoreSamplingValue(context);
        if (!SaturationUtils.restore(context)) {
            Log.w(TAG, "Cannot restore display saturation");
        }
        overrideHdrTypes(context);
    }

    private static void overrideHdrTypes(Context context) {

        // Override HDR types to enable Dolby Vision
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        dm.overrideHdrTypes(Display.DEFAULT_DISPLAY, new int[]{
                HdrCapabilities.HDR_TYPE_DOLBY_VISION, HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG, HdrCapabilities.HDR_TYPE_HDR10_PLUS});
        DisplayUtils.restore(context);
    }
}
