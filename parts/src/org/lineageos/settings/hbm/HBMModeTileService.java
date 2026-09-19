/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.hbm;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import org.lineageos.settings.R;
import org.lineageos.settings.display.DisplayUtils;

public class HBMModeTileService extends TileService {
    private void updateUi() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (!DisplayUtils.isHbmSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(DisplayUtils.isHbmEnabled(this)
                    ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateUi();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!DisplayUtils.isHbmSupported()) {
            updateUi();
            return;
        }
        boolean enabled = !DisplayUtils.isHbmEnabled(this);
        if (!DisplayUtils.setHbm(this, enabled)) {
            Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        updateUi();
    }
}
