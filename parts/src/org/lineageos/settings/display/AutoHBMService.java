/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class AutoHBMService extends Service implements
        SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "AutoHBMService";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mPrefs;
    private boolean mSensorRegistered;
    private boolean mInitialized;
    private boolean mAutoHbmActive;
    private boolean mDisablePending;
    private float mLastLux = -1.0f;

    private final Runnable mDisableHbmRunnable = () -> {
        mDisablePending = false;
        if (AutoHBMPolicy.shouldDisableNow(
                mLastLux, getThreshold(), mAutoHbmActive)) {
            setHbmEnabled(false);
        }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (isAutoEnabled()) registerLightSensor();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                unregisterLightSensor();
                cancelPendingDisable();
                setHbmEnabled(false);
                mLastLux = -1.0f;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                evaluateLux();
            }
        }
    };

    public static boolean start(Context context) {
        try {
            return context.startServiceAsUser(
                    new Intent(context, AutoHBMService.class), UserHandle.CURRENT) != null;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to start Auto HBM service", e);
            return false;
        }
    }

    public static void stop(Context context) {
        try {
            context.stopServiceAsUser(new Intent(context, AutoHBMService.class), UserHandle.CURRENT);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to stop Auto HBM service", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mSensorManager = getSystemService(SensorManager.class);
        if (mSensorManager != null) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isAutoEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (mLightSensor == null || !HBMController.isSupported()) {
            Log.w(TAG, "Auto HBM unavailable: missing light sensor or HBM support");
            mPrefs.edit().putBoolean(DisplayNodes.getAutoHbmEnableKey(), false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!mInitialized) {
            // Recover cleanly after process death and restore any saved pre-HBM brightness.
            HBMController.disable(this, mPrefs);
            mAutoHbmActive = false;
            mInitialized = true;
        }

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isInteractive()) registerLightSensor();
        return START_STICKY;
    }

    private boolean isAutoEnabled() {
        return mPrefs != null && mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false);
    }

    private void registerLightSensor() {
        if (mSensorRegistered || mSensorManager == null || mLightSensor == null) return;
        mSensorRegistered = mSensorManager.registerListener(
                this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void unregisterLightSensor() {
        if (!mSensorRegistered || mSensorManager == null) return;
        mSensorManager.unregisterListener(this);
        mSensorRegistered = false;
    }

    private int getThreshold() {
        return mPrefs.getInt(DisplayNodes.getAutoHbmThresholdKey(),
                DisplayNodes.AUTO_HBM_THRESHOLD_DEFAULT);
    }

    private long getDisableDelayMillis() {
        int seconds = mPrefs.getInt(DisplayNodes.getAutoHbmDisableTimeKey(),
                DisplayNodes.AUTO_HBM_DISABLE_TIME_DEFAULT);
        return Math.max(1, seconds) * 1000L;
    }

    private boolean isBlocked() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        boolean keyguardLocked = km != null && km.isKeyguardLocked();
        return keyguardLocked || DisplayUtils.isDcDimmingEnabled();
    }

    private void cancelPendingDisable() {
        mHandler.removeCallbacks(mDisableHbmRunnable);
        mDisablePending = false;
    }

    private void scheduleDisable() {
        if (mDisablePending) return;
        mDisablePending = true;
        mHandler.postDelayed(mDisableHbmRunnable, getDisableDelayMillis());
    }

    private void rescheduleDisableIfNeeded() {
        if (!mDisablePending) return;
        cancelPendingDisable();
        scheduleDisable();
    }

    private void setHbmEnabled(boolean enabled) {
        if (mAutoHbmActive == enabled) return;
        boolean ok = enabled
                ? HBMController.enable(this, mPrefs)
                : HBMController.disable(this, mPrefs);
        if (!ok) {
            Log.w(TAG, "Failed to " + (enabled ? "enable" : "disable") + " HBM");
            return;
        }
        mAutoHbmActive = enabled;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT || event.values.length == 0) return;
        if (!isAutoEnabled()) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }
        mLastLux = event.values[0];
        evaluateLux();
    }

    private void evaluateLux() {
        if (!isAutoEnabled()) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }
        if (mLastLux < 0.0f) return;

        int threshold = getThreshold();
        boolean blocked = isBlocked();
        if (blocked) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }

        if (AutoHBMPolicy.shouldEnable(mLastLux, threshold, mAutoHbmActive, false)) {
            cancelPendingDisable();
            setHbmEnabled(true);
        } else if (AutoHBMPolicy.shouldScheduleDisable(mLastLux, threshold, mAutoHbmActive)) {
            scheduleDisable();
        } else if (mLastLux >= threshold) {
            cancelPendingDisable();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DisplayNodes.getAutoHbmEnableKey().equals(key)) {
            if (!sharedPreferences.getBoolean(key, false)) {
                cancelPendingDisable();
                setHbmEnabled(false);
                stopSelf();
            }
        } else if (DisplayNodes.getDcDimmingEnableKey().equals(key)) {
            if (sharedPreferences.getBoolean(key, false)) {
                cancelPendingDisable();
                setHbmEnabled(false);
            } else {
                evaluateLux();
            }
        } else if (DisplayNodes.getAutoHbmThresholdKey().equals(key)) {
            evaluateLux();
        } else if (DisplayNodes.getAutoHbmDisableTimeKey().equals(key)) {
            rescheduleDisableIfNeeded();
        }
    }

    @Override
    public void onDestroy() {
        cancelPendingDisable();
        unregisterLightSensor();
        setHbmEnabled(false);
        try {
            unregisterReceiver(mScreenStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (mPrefs != null) mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
