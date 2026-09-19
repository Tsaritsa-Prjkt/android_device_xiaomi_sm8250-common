/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.gpumanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

/** Screenshot-styled GPU manager backed by the existing KGSL controls. */
public class GpuManagerActivity extends Activity {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final GpuManagerUtils mUtils = new GpuManagerUtils();

    private SharedPreferences mPrefs;
    private Spinner mGovernor;
    private Spinner mMin;
    private Spinner mMax;
    private Switch mForceClk;
    private Switch mForceBus;
    private Switch mForceRail;
    private Switch mForceNoNap;
    private Switch mBusSplit;
    private Switch mApplyBoot;
    private TextView mModel;
    private TextView mLoad;
    private TextView mTemp;
    private TextView mFreq;
    private TextView mThermal;
    private String[] mGovernorValues = new String[0];
    private String[] mFrequencyValues = new String[0];

    private final Runnable mUpdater = new Runnable() {
        @Override public void run() {
            updateTelemetry();
            mHandler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpu_manager);
        Context storage = createDeviceProtectedStorageContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(storage);

        findViewById(R.id.xp_back).setOnClickListener(v -> finish());
        mModel = findViewById(R.id.gpu_model);
        mLoad = findViewById(R.id.gpu_load);
        mTemp = findViewById(R.id.gpu_temp);
        mFreq = findViewById(R.id.gpu_freq);
        mThermal = findViewById(R.id.gpu_thermal_level);
        mGovernor = findViewById(R.id.gpu_governor);
        mMin = findViewById(R.id.gpu_min);
        mMax = findViewById(R.id.gpu_max);
        mForceClk = findViewById(R.id.gpu_force_clk);
        mForceBus = findViewById(R.id.gpu_force_bus);
        mForceRail = findViewById(R.id.gpu_force_rail);
        mForceNoNap = findViewById(R.id.gpu_force_no_nap);
        mBusSplit = findViewById(R.id.gpu_bus_split);
        mApplyBoot = findViewById(R.id.gpu_apply_boot);

        findViewById(R.id.gpu_apply).setOnClickListener(v -> applySettings());
        findViewById(R.id.gpu_reset).setOnClickListener(v -> resetSettings());
        mApplyBoot.setChecked(mPrefs.getBoolean(GpuManagerUtils.PREF_APPLY_ON_BOOT, false));
        mApplyBoot.setOnCheckedChangeListener((button, checked) ->
                mPrefs.edit().putBoolean(GpuManagerUtils.PREF_APPLY_ON_BOOT, checked).apply());

        loadControls();
        updateTelemetry();
    }

    @Override protected void onResume() {
        super.onResume();
        mHandler.removeCallbacks(mUpdater);
        mHandler.post(mUpdater);
    }

    @Override protected void onPause() {
        mHandler.removeCallbacks(mUpdater);
        super.onPause();
    }

    private void loadControls() {
        mModel.setText(mUtils.getGpuModel());
        mGovernorValues = mUtils.getAvailableGovernors();
        if (mGovernorValues == null) mGovernorValues = new String[0];
        bindSpinner(mGovernor, mGovernorValues, mGovernorValues, mUtils.getCurrentGovernor());

        mFrequencyValues = mUtils.getAvailableFrequencies();
        if (mFrequencyValues == null) mFrequencyValues = new String[0];
        String[] freqLabels = new String[mFrequencyValues.length];
        for (int i = 0; i < mFrequencyValues.length; i++) {
            freqLabels[i] = gpuMhz(mFrequencyValues[i]) + " MHz";
        }
        bindSpinner(mMin, freqLabels, mFrequencyValues, mUtils.getCurrentMinFrequency());
        bindSpinner(mMax, freqLabels, mFrequencyValues, mUtils.getCurrentMaxFrequency());

        mForceClk.setChecked(mUtils.getForceClkOn());
        mForceBus.setChecked(mUtils.getForceBusOn());
        mForceRail.setChecked(mUtils.getForceRailOn());
        mForceNoNap.setChecked(mUtils.getForceNoNap());
        mBusSplit.setChecked(mUtils.getBusSplit());
        updateGovernorActive();
    }

    private void bindSpinner(Spinner spinner, String[] labels, String[] values, String selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.xp_spinner_item, R.id.text1, labels);
        adapter.setDropDownViewResource(R.layout.xp_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int index = indexOf(values, selected);
        if (index >= 0) spinner.setSelection(index, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) ((TextView) view).setTextColor(getColor(R.color.xp_on_background));
                if (spinner == mGovernor) updateGovernorActive();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void updateGovernorActive() {
        TextView active = findViewById(R.id.gpu_governor_active);
        String value = selected(mGovernorValues, mGovernor);
        active.setText(value == null ? "" : "Active: " + value);
    }

    private void updateTelemetry() {
        if (isFinishing()) return;
        String busy = mUtils.getGpuBusyPercentage();
        mLoad.setText(busy.endsWith("%") ? busy : busy + "%");
        String temp = mUtils.getGpuTemperature();
        mTemp.setText("0".equals(temp) ? "—" : temp + "°C");
        String freq = mUtils.getCurrentFrequency();
        mFreq.setText("0".equals(freq) ? "—" : gpuMhz(freq) + " MHz");
        mThermal.setText("Level " + mUtils.getThermalPowerLevel());
    }

    private void applySettings() {
        String governor = selected(mGovernorValues, mGovernor);
        String min = selected(mFrequencyValues, mMin);
        String max = selected(mFrequencyValues, mMax);
        boolean ok = governor != null && min != null && max != null;
        if (ok) ok = mUtils.setGovernor(governor);
        if (ok) ok = mUtils.setFrequencyRange(min, max);
        if (ok) ok = mUtils.setForceClkOn(mForceClk.isChecked());
        if (ok) ok = mUtils.setForceBusOn(mForceBus.isChecked());
        if (ok) ok = mUtils.setForceRailOn(mForceRail.isChecked());
        if (ok) ok = mUtils.setForceNoNap(mForceNoNap.isChecked());
        if (ok) ok = mUtils.setBusSplit(mBusSplit.isChecked());

        if (ok) {
            mPrefs.edit()
                    .putString(GpuManagerUtils.PREF_GOVERNOR, governor)
                    .putString(GpuManagerUtils.PREF_MIN_FREQ, min)
                    .putString(GpuManagerUtils.PREF_MAX_FREQ, max)
                    .putBoolean(GpuManagerUtils.PREF_FORCE_CLK_ON, mForceClk.isChecked())
                    .putBoolean(GpuManagerUtils.PREF_FORCE_BUS_ON, mForceBus.isChecked())
                    .putBoolean(GpuManagerUtils.PREF_FORCE_RAIL_ON, mForceRail.isChecked())
                    .putBoolean(GpuManagerUtils.PREF_FORCE_NO_NAP, mForceNoNap.isChecked())
                    .putBoolean(GpuManagerUtils.PREF_BUS_SPLIT, mBusSplit.isChecked())
                    .apply();
        } else {
            loadControls();
        }
        Toast.makeText(this, ok ? R.string.settings_applied : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

    private void resetSettings() {
        boolean ok = mUtils.resetToDefaults();
        loadControls();
        if (ok) {
            mPrefs.edit()
                    .remove(GpuManagerUtils.PREF_GOVERNOR)
                    .remove(GpuManagerUtils.PREF_MIN_FREQ)
                    .remove(GpuManagerUtils.PREF_MAX_FREQ)
                    .remove(GpuManagerUtils.PREF_FORCE_CLK_ON)
                    .remove(GpuManagerUtils.PREF_FORCE_BUS_ON)
                    .remove(GpuManagerUtils.PREF_FORCE_RAIL_ON)
                    .remove(GpuManagerUtils.PREF_FORCE_NO_NAP)
                    .remove(GpuManagerUtils.PREF_BUS_SPLIT)
                    .apply();
        }
        Toast.makeText(this, ok ? R.string.settings_reset : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

    private static int indexOf(String[] values, String selected) {
        if (values == null || selected == null) return -1;
        for (int i = 0; i < values.length; i++) if (selected.equals(values[i])) return i;
        return -1;
    }

    private static String selected(String[] values, Spinner spinner) {
        if (values == null || values.length == 0) return null;
        int position = spinner.getSelectedItemPosition();
        return position >= 0 && position < values.length ? values[position] : null;
    }

    private static long gpuMhz(String hz) {
        try { return Long.parseLong(hz) / 1_000_000L; } catch (RuntimeException e) { return 0; }
    }
}
