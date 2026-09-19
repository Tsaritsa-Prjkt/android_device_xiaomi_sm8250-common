/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.lineageos.settings.charge.ChargeActivity;
import org.lineageos.settings.corecontrol.CoreControlActivity;
import org.lineageos.settings.dashboard.DeviceInfoUtils;
import org.lineageos.settings.dirac.DiracActivity;
import org.lineageos.settings.display.DcDimmingSettingsActivity;
import org.lineageos.settings.gpumanager.GpuManagerActivity;
import org.lineageos.settings.hbm.HBMActivity;
import org.lineageos.settings.kernelmanager.KernelManagerActivity;
import org.lineageos.settings.refreshrate.RefreshActivity;
import org.lineageos.settings.saturation.SaturationActivity;
import org.lineageos.settings.speaker.ClearSpeakerActivity;
import org.lineageos.settings.thermal.ThermalActivity;
import org.lineageos.settings.touchsampling.TouchSamplingSettingsActivity;

/** XiaomiParts dashboard: a single entry point for all device-specific controls. */
public class XiaomiPartsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xiaomi_parts);

        TextView deviceName = findViewById(R.id.xp_device_name);
        TextView codename = findViewById(R.id.xp_device_codename);
        deviceName.setText(DeviceInfoUtils.getMarketingName());
        codename.setText("device: " + DeviceInfoUtils.getCodename());

        findViewById(R.id.xp_back).setOnClickListener(v -> finish());

        bind(R.id.card_gpu, GpuManagerActivity.class);
        bind(R.id.card_core, CoreControlActivity.class);
        bind(R.id.card_thermal, ThermalActivity.class);
        bind(R.id.card_kernel, KernelManagerActivity.class);
        bind(R.id.card_clear_speaker, ClearSpeakerActivity.class);
        bind(R.id.card_refresh, RefreshActivity.class);
        bind(R.id.card_charge, ChargeActivity.class);
        bind(R.id.card_saturation, SaturationActivity.class);
        bind(R.id.card_dc, DcDimmingSettingsActivity.class);
        bind(R.id.card_hbm, HBMActivity.class);
        bind(R.id.card_touch, TouchSamplingSettingsActivity.class);
        bind(R.id.card_dirac, DiracActivity.class);
    }

    private void bind(int viewId, Class<?> activityClass) {
        View view = findViewById(viewId);
        if (view == null) return;
        view.setOnClickListener(v -> startActivity(new Intent(this, activityClass)));
    }
}
