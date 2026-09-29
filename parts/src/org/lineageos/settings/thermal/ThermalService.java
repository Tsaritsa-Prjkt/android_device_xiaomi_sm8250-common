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
import android.os.RemoteException;
import android.util.Log;

public class ThermalService extends Service {

    private static final String TAG = "ThermalService";
    private static final boolean DEBUG = false;

    private boolean mScreenOn = true;
    private String mCurrentApp = "";
    private ThermalUtils mThermalUtils;

    private IActivityTaskManager mActivityTaskManager;
    private boolean mReceiverRegistered;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mDestroyed;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    setThermalProfile();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    setThermalProfile();
                    break;
            }
            mThermalUtils.resetTouchModes();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Creating service");
        if (!ThermalUtils.isServiceEnabled(this)) {
            stopSelf();
            return;
        }
        mThermalUtils = new ThermalUtils(this);
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            // Do nothing
        }
        registerReceiver();
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        if (mReceiverRegistered) {
            unregisterReceiver(mIntentReceiver);
            mReceiverRegistered = false;
        }
        if (mActivityTaskManager != null) {
            try {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                // Do nothing
            }
        }
        if (mThermalUtils != null) {
            mThermalUtils.setDefaultThermalProfile();
            mThermalUtils.resetTouchModes();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        if (!ThermalUtils.isServiceEnabled(this)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        updateForegroundApp();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mThermalUtils != null) {
            mThermalUtils.updateTouchRotation();
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        this.registerReceiver(mIntentReceiver, filter);
        mReceiverRegistered = true;
    }

    private void setThermalProfile() {
        if (mDestroyed || mThermalUtils == null) {
            return;
        }
        if (mScreenOn) {
            mThermalUtils.setThermalProfile(mCurrentApp);
        } else {
            mThermalUtils.setDefaultThermalProfile();
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            mHandler.post(ThermalService.this::updateForegroundApp);
        }
    };

    private void updateForegroundApp() {
        if (mDestroyed || mActivityTaskManager == null || mThermalUtils == null) {
            return;
        }
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            if (info != null && info.topActivity != null) {
                mCurrentApp = info.topActivity.getPackageName();
                setThermalProfile();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to get foreground app", e);
        }
    }
}
