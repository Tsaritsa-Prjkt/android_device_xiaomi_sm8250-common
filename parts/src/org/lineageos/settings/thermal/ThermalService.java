/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.thermal;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

public class ThermalService extends Service {
    private static final String TAG = "ThermalService";

    // TaskStackListener callbacks arrive on Binder threads. Keep all profile and
    // touch changes on the service thread, including screen-state transitions.
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mDestroyed;
    private boolean mScreenOn;
    private String mCurrentApp = "";
    private ThermalUtils mThermalUtils;
    private IActivityTaskManager mActivityTaskManager;

    private final Runnable mRefreshForeground = () -> refreshForegroundApp(false);

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mDestroyed) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mScreenOn = false;
                mThermalUtils.resetTouchModes();
                mThermalUtils.setDefaultThermalProfile();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                mScreenOn = true;
                // Do not reset touch modes after applying the foreground app:
                // doing so immediately undoes its selected gaming touch profile.
                refreshForegroundApp(true);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize dependencies before registering a listener which may call
        // back immediately. A swallowed exception used to lose the first app.
        mThermalUtils = new ThermalUtils(this);
        mActivityTaskManager = ActivityTaskManager.getService();
        PowerManager powerManager = getSystemService(PowerManager.class);
        mScreenOn = powerManager != null && powerManager.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        if (mActivityTaskManager != null) {
            try {
                mActivityTaskManager.registerTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot register task listener", e);
            }
        }
        // A sticky restart can occur while a game is already foreground, with
        // no subsequent stack transition to trigger the saved profile.
        refreshForegroundApp(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        refreshForegroundApp(true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        unregisterReceiver(mIntentReceiver);
        if (mActivityTaskManager != null) {
            try {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot unregister task listener", e);
            }
        }
        mHandler.removeCallbacks(mRefreshForeground);
        mThermalUtils.resetTouchModes();
        mThermalUtils.setDefaultThermalProfile();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mDestroyed) mThermalUtils.updateTouchRotation();
    }

    private void refreshForegroundApp(boolean force) {
        if (mDestroyed) return;
        String foregroundApp = "";
        if (mActivityTaskManager != null) {
            try {
                RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
                if (info != null && info.topActivity != null) {
                    foregroundApp = info.topActivity.getPackageName();
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Cannot query foreground app", e);
                return;
            }
        }
        if (!force && foregroundApp.equals(mCurrentApp)) return;
        mCurrentApp = foregroundApp;
        if (mScreenOn && !mCurrentApp.isEmpty()) {
            mThermalUtils.setThermalProfile(mCurrentApp);
        } else {
            mThermalUtils.resetTouchModes();
            mThermalUtils.setDefaultThermalProfile();
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            if (mDestroyed) return;
            mHandler.removeCallbacks(mRefreshForeground);
            mHandler.post(mRefreshForeground);
        }
    };
}
