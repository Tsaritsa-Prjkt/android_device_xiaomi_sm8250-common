/*
 * Copyright (C) 2021 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings;

import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Display;
import android.widget.Toast;

import org.lineageos.settings.refreshrate.RefreshUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class RefreshRateTileService extends TileService {
    private final List<Float> mAvailableRates = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        loadAvailableRates();
    }

    private void loadAvailableRates() {
        mAvailableRates.clear();
        DisplayManager manager = getSystemService(DisplayManager.class);
        Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return;

        Display.Mode current = display.getMode();
        LinkedHashSet<Float> rates = new LinkedHashSet<>();
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                rates.add(roundRate(mode.getRefreshRate()));
            }
        }
        mAvailableRates.addAll(rates);
        Collections.sort(mAvailableRates);
    }

    private static float roundRate(float rate) {
        return Float.parseFloat(String.format(Locale.US, "%.02f", rate));
    }

    private int nearestIndex(float target) {
        if (mAvailableRates.isEmpty()) return -1;
        int nearest = 0;
        float best = Math.abs(mAvailableRates.get(0) - target);
        for (int i = 1; i < mAvailableRates.size(); i++) {
            float delta = Math.abs(mAvailableRates.get(i) - target);
            if (delta < best) {
                best = delta;
                nearest = i;
            }
        }
        return nearest;
    }

    private boolean setRate(float rate) {
        float oldMin = Settings.System.getFloat(getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, rate);
        float oldPeak = Settings.System.getFloat(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, rate);
        boolean minOk = Settings.System.putFloat(getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, rate);
        boolean peakOk = Settings.System.putFloat(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, rate);
        if (!(minOk && peakOk)) {
            Settings.System.putFloat(getContentResolver(), Settings.System.MIN_REFRESH_RATE, oldMin);
            Settings.System.putFloat(getContentResolver(), Settings.System.PEAK_REFRESH_RATE, oldPeak);
            return false;
        }
        // Explicit tile changes become the user's baseline for per-app overrides.
        RefreshUtils.updateBaseline(this, rate, rate);
        return true;
    }

    private String formatRate(float rate) {
        return String.format(Locale.US, "%.02f Hz", rate).replaceAll("[\\.,]00", "");
    }

    private void updateTileView() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (mAvailableRates.isEmpty()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(null);
            tile.updateTile();
            return;
        }
        float min = Settings.System.getFloat(getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, mAvailableRates.get(0));
        float peak = Settings.System.getFloat(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, mAvailableRates.get(mAvailableRates.size() - 1));
        int minIndex = nearestIndex(min);
        int peakIndex = nearestIndex(peak);
        float nearestMin = mAvailableRates.get(minIndex);
        float nearestPeak = mAvailableRates.get(peakIndex);
        String text = nearestMin == nearestPeak
                ? formatRate(nearestMin)
                : formatRate(nearestMin) + " - " + formatRate(nearestPeak);
        tile.setContentDescription(text);
        tile.setSubtitle(text);
        tile.setState(nearestMin == nearestPeak ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        loadAvailableRates();
        updateTileView();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (mAvailableRates.isEmpty()) return;
        float current = Settings.System.getFloat(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, mAvailableRates.get(0));
        int index = nearestIndex(current);
        int next = (index + 1) % mAvailableRates.size();
        if (!setRate(mAvailableRates.get(next))) {
            Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        updateTileView();
    }
}
