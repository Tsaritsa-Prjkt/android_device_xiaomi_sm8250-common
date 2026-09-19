/* SPDX-License-Identifier: Apache-2.0 */
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
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;

public class ThermalService extends Service implements DisplayManager.DisplayListener {
    private static final String TAG = "ThermalService";

    private boolean mScreenOn = true;
    private String mCurrentApp = "";
    private ThermalUtils mThermalUtils;
    private IActivityTaskManager mActivityTaskManager;
    private DisplayManager mDisplayManager;
    private boolean mReceiverRegistered;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mScreenOn = false;
                mCurrentApp = "";
                mThermalUtils.setDefaultThermalProfile();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                mScreenOn = true;
                applyFocusedApp();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mThermalUtils = new ThermalUtils(this);
        mActivityTaskManager = ActivityTaskManager.getService();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to register task listener", e);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mReceiverRegistered = true;

        mDisplayManager = getSystemService(DisplayManager.class);
        if (mDisplayManager != null) {
            mDisplayManager.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
        }
        applyFocusedApp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        applyFocusedApp();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mThermalUtils.setDefaultThermalProfile();
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister task listener", e);
        }
        if (mReceiverRegistered) unregisterReceiver(mIntentReceiver);
        if (mDisplayManager != null) mDisplayManager.unregisterDisplayListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyFocusedApp() {
        if (!mScreenOn || mActivityTaskManager == null || mThermalUtils == null) return;
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            if (info == null || info.topActivity == null) {
                mCurrentApp = "";
                mThermalUtils.setDefaultThermalProfile();
                return;
            }
            String packageName = info.topActivity.getPackageName();
            mCurrentApp = packageName;
            mThermalUtils.setThermalProfile(packageName);
        } catch (RuntimeException | RemoteException e) {
            Log.w(TAG, "Unable to resolve focused app", e);
            mCurrentApp = "";
            mThermalUtils.setDefaultThermalProfile();
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            applyFocusedApp();
        }
    };

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) mThermalUtils.updateTouchRotation();
    }

    @Override public void onDisplayAdded(int displayId) {}
    @Override public void onDisplayRemoved(int displayId) {}
}
