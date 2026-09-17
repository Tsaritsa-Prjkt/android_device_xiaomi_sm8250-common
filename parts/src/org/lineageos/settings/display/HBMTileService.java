/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.lineageos.settings.display;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

public class HBMTileService extends TileService {

    private String HBM_ENABLE_KEY;
    private String AUTO_HBM_ENABLE_KEY;

    private void updateUI(boolean enabled, boolean autoEnabled) {
        final Tile tile = getQsTile();
        tile.setState(autoEnabled ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        HBM_ENABLE_KEY = DisplayNodes.getHbmEnableKey();
        AUTO_HBM_ENABLE_KEY = DisplayNodes.getAutoHbmEnableKey();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateUI(sharedPrefs.getBoolean(HBM_ENABLE_KEY, false),
                sharedPrefs.getBoolean(AUTO_HBM_ENABLE_KEY, false));
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean autoEnabled = sharedPrefs.getBoolean(AUTO_HBM_ENABLE_KEY, false);
        if (autoEnabled) {
            updateUI(false, true);
            return;
        }
        final boolean enabled = !(sharedPrefs.getBoolean(HBM_ENABLE_KEY, false));
        final boolean success = enabled
                ? HBMController.enable(this, sharedPrefs)
                : HBMController.disable(this, sharedPrefs);
        if (!success) {
            updateUI(!enabled, false);
            return;
        }
        sharedPrefs.edit().putBoolean(HBM_ENABLE_KEY, enabled).commit();
        updateUI(enabled, false);
    }
}
