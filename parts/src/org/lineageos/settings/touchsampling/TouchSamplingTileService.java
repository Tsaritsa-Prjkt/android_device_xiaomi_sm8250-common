/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.touchsampling;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import org.lineageos.settings.R;

public class TouchSamplingTileService extends TileService {
    private void updateUi() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (!TouchSamplingUtils.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(TouchSamplingUtils.isEnabled(this)
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
        if (!TouchSamplingUtils.isSupported()) {
            updateUi();
            return;
        }
        boolean enabled = !TouchSamplingUtils.isEnabled(this);
        if (!TouchSamplingUtils.setEnabled(this, enabled)) {
            Toast.makeText(this, R.string.parts_apply_failed, Toast.LENGTH_SHORT).show();
        }
        updateUi();
    }
}
