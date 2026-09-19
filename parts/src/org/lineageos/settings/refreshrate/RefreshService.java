/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.refreshrate;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class RefreshService extends Service {
    private static final String TAG = "RefreshService";

    private String mPreviousApp = "";
    private boolean mScreenOn = true;
    private RefreshUtils mRefreshUtils;
    private IActivityTaskManager mActivityTaskManager;
    private boolean mReceiverRegistered;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mScreenOn = false;
                mPreviousApp = "";
                mRefreshUtils.restoreBaseline();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                mScreenOn = true;
                applyFocusedApp();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mRefreshUtils = new RefreshUtils(this);
        mRefreshUtils.captureBaselineIfNeeded();
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
        applyFocusedApp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        applyFocusedApp();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRefreshUtils.restoreBaseline();
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister task listener", e);
        }
        if (mReceiverRegistered) unregisterReceiver(mIntentReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyFocusedApp() {
        if (!mScreenOn || mActivityTaskManager == null || mRefreshUtils == null) return;
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            if (info == null || info.topActivity == null) {
                mRefreshUtils.restoreBaseline();
                mPreviousApp = "";
                return;
            }
            String foregroundApp = info.topActivity.getPackageName();
            if (!foregroundApp.equals(mPreviousApp)) {
                mRefreshUtils.setRefreshRate(foregroundApp);
                mPreviousApp = foregroundApp;
            } else {
                // A user may have edited the foreground app's assignment while it stayed focused.
                mRefreshUtils.setRefreshRate(foregroundApp);
            }
        } catch (RuntimeException | RemoteException e) {
            Log.w(TAG, "Unable to resolve focused app", e);
            mRefreshUtils.restoreBaseline();
            mPreviousApp = "";
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            applyFocusedApp();
        }
    };
}
