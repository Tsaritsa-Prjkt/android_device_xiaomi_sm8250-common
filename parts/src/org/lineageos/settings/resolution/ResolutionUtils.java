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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.preference.PreferenceManager;

public final class ResolutionUtils {
    private static final String TAG = "ResolutionUtils";
    private static final String RESOLUTION_CONTROL = "resolutioncontrol";
    private static final String RESOLUTION_GLOBAL_STATE = "resolution.global_state";
    private static final String SAVED_DENSITY = "resolution.saved_native_density";
    private static final String[] BUCKETS = {
            "resolution.480p", "resolution.540p", "resolution.720p"
    };
    private static final int[] WIDTHS = {0, 480, 540, 720};
    private static final Object DISPLAY_LOCK = new Object();

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_480P = 1;
    protected static final int STATE_540P = 2;
    protected static final int STATE_720P = 3;

    private final SharedPreferences mSharedPrefs;
    private final Context mContext;

    protected ResolutionUtils(Context context) {
        mContext = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static void startService(Context context) {
        // The service is the only display writer; activities only update preferences.
        context.startServiceAsUser(new Intent(context, ResolutionService.class), UserHandle.CURRENT);
    }

    static boolean isResolutionPreference(String key) {
        return RESOLUTION_GLOBAL_STATE.equals(key) || RESOLUTION_CONTROL.equals(key);
    }

    private static int validState(int state) {
        return state >= STATE_DEFAULT && state <= STATE_720P ? state : STATE_DEFAULT;
    }

    public int getGlobalState() {
        return validState(mSharedPrefs.getInt(RESOLUTION_GLOBAL_STATE, STATE_DEFAULT));
    }

    public void setGlobalState(int state) {
        mSharedPrefs.edit().putInt(RESOLUTION_GLOBAL_STATE, validState(state)).apply();
        startService(mContext);
    }

    public boolean applyBaselineFromGlobal() {
        return applyResolution(getGlobalState());
    }

    // Keep the existing bucket format so previously configured apps are preserved.
    private String[] getPackageLists() {
        String value = mSharedPrefs.getString(RESOLUTION_CONTROL, "");
        if (value == null) value = "";
        String[] stored = value.split(";", -1);
        String[] lists = new String[BUCKETS.length];
        for (int i = 0; i < lists.length; i++) {
            lists[i] = i < stored.length && stored[i].startsWith(BUCKETS[i])
                    ? stored[i].substring(BUCKETS[i].length()) : "";
        }
        return lists;
    }

    protected void writePackage(String packageName, int mode) {
        if (packageName == null || packageName.isEmpty()) return;
        String[] lists = getPackageLists();
        mode = validState(mode);
        String[] stored = new String[BUCKETS.length];
        for (int i = 0; i < stored.length; i++) {
            StringBuilder bucket = new StringBuilder(BUCKETS[i]);
            for (String entry : lists[i].split(",")) {
                if (!entry.isEmpty() && !entry.equals(packageName)) {
                    bucket.append(entry).append(',');
                }
            }
            if (mode == i + 1) bucket.append(packageName).append(',');
            stored[i] = bucket.toString();
        }
        mSharedPrefs.edit().putString(RESOLUTION_CONTROL, String.join(";", stored)).apply();
        startService(mContext);
    }

    protected int getStateForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return STATE_DEFAULT;
        String[] lists = getPackageLists();
        for (int i = 0; i < lists.length; i++) {
            for (String entry : lists[i].split(",")) {
                if (packageName.equals(entry)) return i + 1;
            }
        }
        return STATE_DEFAULT;
    }

    protected boolean setResolution(String packageName) {
        int state = getStateForPackage(packageName);
        return applyResolution(state == STATE_DEFAULT ? getGlobalState() : state);
    }

    private static Point scaledSize(Point nativeSize, int state) {
        if (state == STATE_DEFAULT) return nativeSize;
        int width = WIDTHS[state];
        return new Point(width, (int) Math.round((double) nativeSize.y * width / nativeSize.x));
    }

    private static boolean sameSize(Point a, Point b) {
        return a.x == b.x && a.y == b.y;
    }

    private boolean applyResolution(int state) {
        synchronized (DISPLAY_LOCK) {
            try {
                IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
                final int displayId = Display.DEFAULT_DISPLAY;
                final int userId = UserHandle.myUserId();
                Point nativeSize = new Point();
                Point currentSize = new Point();
                wm.getInitialDisplaySize(displayId, nativeSize);
                wm.getBaseDisplaySize(displayId, currentSize);
                int nativeDensity = wm.getInitialDisplayDensity(displayId);
                int currentDensity = wm.getBaseDisplayDensity(displayId);
                if (nativeSize.x <= 0 || nativeSize.y <= 0
                        || nativeDensity <= 0 || currentDensity <= 0) {
                    Log.e(TAG, "Display metrics are not ready");
                    return false;
                }

                boolean atNativeSize = sameSize(currentSize, nativeSize);
                int savedDensity = mSharedPrefs.getInt(SAVED_DENSITY, -1);
                // Never capture an already scaled density as the native baseline.
                // An old installation without a snapshot falls back to WMS's native DPI.
                int baselineDensity = savedDensity > 0 ? savedDensity
                        : (atNativeSize ? currentDensity : nativeDensity);
                Point targetSize = scaledSize(nativeSize, state);
                int targetDensity;

                // Also keep a recovery snapshot when repairing an old forced size:
                // clearing size may succeed before a subsequent density write fails.
                if (savedDensity <= 0 && (state != STATE_DEFAULT || !atNativeSize)) {
                    if (!mSharedPrefs.edit().putInt(SAVED_DENSITY, baselineDensity).commit()) {
                        Log.e(TAG, "Cannot save native density; leaving display unchanged");
                        return false;
                    }
                    savedDensity = baselineDensity;
                }

                if (state == STATE_DEFAULT) {
                    targetDensity = baselineDensity;
                    // A recreated activity/service can encounter a persisted forced
                    // size even though its instance-local state would be Default.
                    if (!atNativeSize) wm.clearForcedDisplaySize(displayId);
                    if (targetDensity == nativeDensity
                            && (savedDensity > 0 || !atNativeSize
                                    || currentDensity != targetDensity)) {
                        wm.clearForcedDisplayDensityForUser(displayId, userId);
                    } else if (wm.getBaseDisplayDensity(displayId) != targetDensity) {
                        wm.setForcedDisplayDensityForUser(displayId, targetDensity, userId);
                    }
                } else {
                    // Always scale the persisted native DPI, never the last scaled DPI.
                    targetDensity = Math.max(72, (int) Math.round(
                            (double) baselineDensity * targetSize.x / nativeSize.x));
                    if (currentDensity != targetDensity) {
                        wm.setForcedDisplayDensityForUser(displayId, targetDensity, userId);
                    }
                    if (!sameSize(currentSize, targetSize)) {
                        wm.setForcedDisplaySize(displayId, targetSize.x, targetSize.y);
                    }
                    // Some framework branches recalculate density on a size change.
                    if (wm.getBaseDisplayDensity(displayId) != targetDensity) {
                        wm.setForcedDisplayDensityForUser(displayId, targetDensity, userId);
                    }
                }

                wm.getBaseDisplaySize(displayId, currentSize);
                if (!sameSize(currentSize, targetSize)
                        || wm.getBaseDisplayDensity(displayId) != targetDensity) {
                    Log.e(TAG, "Display did not accept resolution state " + state);
                    return false;
                }
                if (state == STATE_DEFAULT && savedDensity > 0
                        && !mSharedPrefs.edit().remove(SAVED_DENSITY).commit()) {
                    Log.e(TAG, "Cannot clear restored density snapshot");
                    return false;
                }
                return true;
            } catch (RemoteException | SecurityException e) {
                // Keep the snapshot on failure so the service can retry safely.
                Log.e(TAG, "Unable to apply resolution state " + state, e);
                return false;
            }
        }
    }

    public String getCurrentResolutionString() {
        try {
            Point size = new Point();
            WindowManagerGlobal.getWindowManagerService().getBaseDisplaySize(
                    Display.DEFAULT_DISPLAY, size);
            if (size.x > 0 && size.y > 0) return size.x + "x" + size.y;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to read active display size", e);
        }
        return "\u2014";
    }

    public String getResolutionString(int state) {
        try {
            Point nativeSize = new Point();
            WindowManagerGlobal.getWindowManagerService().getInitialDisplaySize(
                    Display.DEFAULT_DISPLAY, nativeSize);
            if (nativeSize.x > 0 && nativeSize.y > 0) {
                Point size = scaledSize(nativeSize, validState(state));
                return size.x + "x" + size.y;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to read native display size", e);
        }
        return "\u2014";
    }
}
