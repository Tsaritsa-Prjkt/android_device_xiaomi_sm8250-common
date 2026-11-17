/*
 * Copyright (C) 2025 KamiKaonashi
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

package org.lineageos.settings.resolution;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class ResolutionService extends Service {
    private static final String TAG = "ResolutionService";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 300;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ResolutionUtils mResolutionUtils;
    private IActivityTaskManager mActivityTaskManager;
    private SharedPreferences mPreferences;
    private int mRetries;
    private boolean mDestroyed;

    private final Runnable mApplyResolution = this::applyForegroundResolution;
    private final Runnable mScheduleApply = () -> {
        if (mDestroyed) return;
        mRetries = 0;
        mHandler.removeCallbacks(mApplyResolution);
        mHandler.post(mApplyResolution);
    };

    private void scheduleApply() {
        // TaskStackListener may run on Binder threads. Coalesce focus and
        // preference events, then perform all display writes on the main looper.
        mHandler.removeCallbacks(mScheduleApply);
        mHandler.post(mScheduleApply);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            (preferences, key) -> {
                if (ResolutionUtils.isResolutionPreference(key)) scheduleApply();
            };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scheduleApply();
        }
    };

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            scheduleApply();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mResolutionUtils = new ResolutionUtils(this);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        mActivityTaskManager = ActivityTaskManager.getService();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to observe foreground tasks", e);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Reconcile on a START_STICKY restart too, including a null intent.
        scheduleApply();
        return START_STICKY;
    }

    private void applyForegroundResolution() {
        if (mDestroyed || ActivityManager.getCurrentUser() != UserHandle.myUserId()) return;
        boolean applied = false;
        try {
            PowerManager power = getSystemService(PowerManager.class);
            KeyguardManager keyguard = getSystemService(KeyguardManager.class);
            if (!power.isInteractive() || keyguard.isKeyguardLocked()) {
                applied = mResolutionUtils.applyBaselineFromGlobal();
            } else {
                // Resolve once; never restore the baseline before an app override.
                RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
                applied = mResolutionUtils.setResolution(info != null && info.topActivity != null
                        ? info.topActivity.getPackageName() : null);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to read foreground task", e);
        }
        if (!applied && mRetries++ < MAX_RETRIES) {
            mHandler.postDelayed(mApplyResolution, RETRY_DELAY_MS);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
        try {
            mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to remove foreground task listener", e);
        }
        unregisterReceiver(mIntentReceiver);
        super.onDestroy();
    }
}
