/*
 * Copyright (C) 2026 The LineageOS Project
 *
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

import org.lineageos.settings.utils.FileUtils;

public class AutoHBMService extends Service implements
        SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "AutoHBMService";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mPrefs;
    private boolean mSensorRegistered;
    private boolean mInitialized;
    private boolean mAutoHBMActive;
    private boolean mDisablePending;
    private float mLastLux = -1.0f;

    private final Runnable mDisableHbmRunnable = () -> {
        mDisablePending = false;
        int threshold = getThreshold();
        if (AutoHBMPolicy.shouldDisableNow(mLastLux, threshold, mAutoHBMActive)) {
            setHbmEnabled(false);
        }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)) {
                    registerLightSensor();
                }
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

    public static void start(Context context) {
        context.startServiceAsUser(new Intent(context, AutoHBMService.class), UserHandle.CURRENT);
    }

    public static void stop(Context context) {
        context.stopServiceAsUser(new Intent(context, AutoHBMService.class), UserHandle.CURRENT);
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
        if (!mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (mLightSensor == null || !FileUtils.fileExists(DisplayNodes.getHbmNode())) {
            Log.w(TAG, "Auto HBM unavailable: missing light sensor or HBM node");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!mInitialized) {
            // Recover from process death and restore the pre-HBM brightness snapshot, if any.
            if (!HBMController.disable(this, mPrefs)) {
                Log.w(TAG, "Failed to recover HBM state");
            }
            mAutoHBMActive = false;
            mInitialized = true;
        }

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isInteractive()) {
            registerLightSensor();
        }
        return START_STICKY;
    }

    private void registerLightSensor() {
        if (mSensorRegistered || mSensorManager == null || mLightSensor == null) {
            return;
        }
        mSensorRegistered = mSensorManager.registerListener(
                this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void unregisterLightSensor() {
        if (!mSensorRegistered || mSensorManager == null) {
            return;
        }
        mSensorManager.unregisterListener(this);
        mSensorRegistered = false;
    }

    private int getThreshold() {
        return mPrefs.getInt(
                DisplayNodes.getAutoHbmThresholdKey(), DisplayNodes.AUTO_HBM_THRESHOLD_DEFAULT);
    }

    private long getDisableDelayMillis() {
        int seconds = mPrefs.getInt(
                DisplayNodes.getAutoHbmDisableTimeKey(), DisplayNodes.AUTO_HBM_DISABLE_TIME_DEFAULT);
        return Math.max(1, seconds) * 1000L;
    }

    private boolean isBlocked() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        boolean keyguardLocked = km != null && km.isKeyguardLocked();
        boolean dcDimmingEnabled = mPrefs.getBoolean(DisplayNodes.getDcDimmingEnableKey(), false);
        return keyguardLocked || dcDimmingEnabled;
    }

    private void cancelPendingDisable() {
        mHandler.removeCallbacks(mDisableHbmRunnable);
        mDisablePending = false;
    }

    private void scheduleDisable() {
        if (mDisablePending) {
            return;
        }
        mDisablePending = true;
        mHandler.postDelayed(mDisableHbmRunnable, getDisableDelayMillis());
    }

    private void setHbmEnabled(boolean enabled) {
        if (mAutoHBMActive == enabled) {
            return;
        }

        if (enabled) {
            if (!HBMController.enable(this, mPrefs)) {
                Log.w(TAG, "Failed to enable HBM");
                return;
            }
            mAutoHBMActive = true;
        } else {
            if (HBMController.disable(this, mPrefs)) {
                mAutoHBMActive = false;
            } else {
                Log.w(TAG, "Failed to disable HBM");
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT || event.values.length == 0) {
            return;
        }

        if (!mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }

        mLastLux = event.values[0];
        evaluateLux();
    }

    private void evaluateLux() {
        if (!mPrefs.getBoolean(DisplayNodes.getAutoHbmEnableKey(), false)) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }

        if (mLastLux < 0.0f) {
            return;
        }

        int threshold = getThreshold();
        boolean blocked = isBlocked();

        if (blocked) {
            cancelPendingDisable();
            setHbmEnabled(false);
            return;
        }

        if (AutoHBMPolicy.shouldEnable(mLastLux, threshold, mAutoHBMActive, false)) {
            cancelPendingDisable();
            setHbmEnabled(true);
        } else if (AutoHBMPolicy.shouldScheduleDisable(mLastLux, threshold, mAutoHBMActive)) {
            scheduleDisable();
        } else if (mLastLux >= threshold) {
            cancelPendingDisable();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DisplayNodes.getAutoHbmEnableKey().equals(key)
                && !sharedPreferences.getBoolean(key, false)) {
            cancelPendingDisable();
            setHbmEnabled(false);
            stopSelf();
        } else if (DisplayNodes.getDcDimmingEnableKey().equals(key)) {
            if (sharedPreferences.getBoolean(key, false)) {
                cancelPendingDisable();
                setHbmEnabled(false);
            } else {
                evaluateLux();
            }
        }
    }

    @Override
    public void onDestroy() {
        cancelPendingDisable();
        unregisterLightSensor();
        setHbmEnabled(false);
        unregisterReceiver(mScreenStateReceiver);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
