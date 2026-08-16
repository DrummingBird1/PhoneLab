package com.sensolab.devicemonitor;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.util.Locale;

/**
 * Quick Settings Tile showing live CPU temperature. Reuses {@link ThermalZones#cpu()}
 * (extracted in Batch 2) rather than duplicating the thermal-zone matching logic.
 */
public class CpuTempTileService extends TileService {

    @Override public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        updateTile(); // manual refresh on tap
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        float cpu = ThermalZones.cpu();
        if (cpu > 0) {
            float disp = AppPrefs.temp(cpu, this);
            String unit = AppPrefs.tempUnit(this);
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(String.format(Locale.US, "%.0f%s", disp, unit));
        } else {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.qs_tile_label));
        }
        tile.updateTile();
    }
}
