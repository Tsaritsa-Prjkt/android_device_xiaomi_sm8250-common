/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.hbm;

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
import android.os.IBinder;
import android.os.PowerManager;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DisplayUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AutoHBMService extends Service {
    private boolean mAutoHbmActive;
    private ExecutorService mExecutorService;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private volatile float mLastLux;

    private void activateLightSensorRead() {
        submit(() -> {
            if (mSensorManager == null) {
                mSensorManager = getSystemService(SensorManager.class);
            }
            if (mSensorManager == null) return;
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (mLightSensor != null) {
                mSensorManager.registerListener(
                        mSensorEventListener, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });
    }

    private void deactivateLightSensorRead() {
        submit(() -> {
            if (mSensorManager != null) mSensorManager.unregisterListener(mSensorEventListener);
            boolean wasAutoHbmActive = mAutoHbmActive;
            mAutoHbmActive = false;
            if (wasAutoHbmActive) {
                // setHbmTemporary() protects an explicit manual HBM request.
                DisplayUtils.setHbmTemporary(AutoHBMService.this, false);
            }
        });
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.values.length == 0) return;

            final float lux = event.values[0];
            mLastLux = lux;

            // Manual HBM owns the hardware state. Auto HBM must not later turn it off.
            if (DisplayUtils.isHbmManuallyRequested(AutoHBMService.this)) {
                mAutoHbmActive = false;
                return;
            }

            KeyguardManager km = getSystemService(KeyguardManager.class);
            boolean keyguardShowing = km != null && km.isKeyguardLocked();
            float luxThreshold = parseFloatPreference(HBMFragment.KEY_AUTO_HBM_THRESHOLD, 7000f);
            long disableDelaySeconds = parseLongPreference(HBMFragment.KEY_HBM_DISABLE_TIME, 1L);

            if (lux > luxThreshold) {
                if ((!mAutoHbmActive || !DisplayUtils.isHbmEnabled(AutoHBMService.this))
                        && !keyguardShowing
                        && !DisplayUtils.isDcDimmingEnabled(AutoHBMService.this)) {
                    if (DisplayUtils.setHbmTemporary(AutoHBMService.this, true)) {
                        mAutoHbmActive = true;
                    }
                }
                return;
            }

            if (mAutoHbmActive) {
                final float thresholdAtSchedule = luxThreshold;
                submit(() -> {
                    try {
                        Thread.sleep(Math.max(0L, disableDelaySeconds) * 1000L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (mLastLux < thresholdAtSchedule && mAutoHbmActive) {
                        // A manual request may have arrived while the delay was pending.
                        if (!DisplayUtils.isHbmManuallyRequested(AutoHBMService.this)) {
                            DisplayUtils.setHbmTemporary(AutoHBMService.this, false);
                        }
                        mAutoHbmActive = false;
                    }
                });
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private float parseFloatPreference(String key, float fallback) {
        try {
            return Float.parseFloat(mSharedPrefs.getString(key, Float.toString(fallback)));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private long parseLongPreference(String key, long fallback) {
        try {
            return Long.parseLong(mSharedPrefs.getString(key, Long.toString(fallback)));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                activateLightSensorRead();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                deactivateLightSensorRead();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutorService = Executors.newSingleThreadExecutor();
        Context storage = getApplicationContext().createDeviceProtectedStorageContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(storage);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isInteractive()) activateLightSensorRead();
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!DisplayUtils.isHbmSupported()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        if (mSensorManager != null) mSensorManager.unregisterListener(mSensorEventListener);
        boolean wasAutoHbmActive = mAutoHbmActive;
        mAutoHbmActive = false;
        if (wasAutoHbmActive) {
            DisplayUtils.setHbmTemporary(this, false);
        }
        if (mExecutorService != null) mExecutorService.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
