/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.kernelmanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

/** Screenshot-styled CPU/kernel tuning page. */
public class KernelManagerActivity extends Activity {
    private final KernelManagerUtils mUtils = new KernelManagerUtils();
    private SharedPreferences mPrefs;
    private Spinner mGovernor;
    private Spinner mEffMin;
    private Spinner mEffMax;
    private Spinner mPerfMin;
    private Spinner mPerfMax;
    private Switch mApplyBoot;
    private String[] mGovernors = new String[0];
    private String[] mEffValues = new String[0];
    private String[] mPerfValues = new String[0];

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kernel_manager);
        Context storage = createDeviceProtectedStorageContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(storage);

        findViewById(R.id.xp_back).setOnClickListener(v -> finish());
        mGovernor = findViewById(R.id.kernel_governor);
        mEffMin = findViewById(R.id.kernel_eff_min);
        mEffMax = findViewById(R.id.kernel_eff_max);
        mPerfMin = findViewById(R.id.kernel_perf_min);
        mPerfMax = findViewById(R.id.kernel_perf_max);
        mApplyBoot = findViewById(R.id.kernel_apply_boot);
        mApplyBoot.setChecked(mPrefs.getBoolean(KernelManagerUtils.PREF_APPLY_ON_BOOT, false));
        mApplyBoot.setOnCheckedChangeListener((button, checked) ->
                mPrefs.edit().putBoolean(KernelManagerUtils.PREF_APPLY_ON_BOOT, checked).apply());

        findViewById(R.id.kernel_apply).setOnClickListener(v -> applySettings());
        findViewById(R.id.kernel_reset).setOnClickListener(v -> resetSettings());
        loadControls();
    }

    private void loadControls() {
        mGovernors = mUtils.getAvailableGovernors();
        if (mGovernors == null) mGovernors = new String[0];
        bind(mGovernor, mGovernors, mGovernors,
                mUtils.getCurrentGovernor(KernelManagerUtils.EFFICIENCY_CLUSTER));

        mEffValues = safe(mUtils.getEfficiencyUiFrequencies());
        String[] effLabels = labels(mEffValues);
        bind(mEffMin, effLabels, mEffValues, mUtils.getCurrentMinFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));
        bind(mEffMax, effLabels, mEffValues, mUtils.getCurrentMaxFrequency(KernelManagerUtils.EFFICIENCY_CLUSTER));

        mPerfValues = safe(mUtils.getPerformanceUiFrequencies());
        String[] perfLabels = labels(mPerfValues);
        bind(mPerfMin, perfLabels, mPerfValues, mUtils.getPerformanceCurrentMinFrequency());
        bind(mPerfMax, perfLabels, mPerfValues, mUtils.getPerformanceCurrentMaxFrequency());
        updateActiveGovernor();
    }

    private void bind(Spinner spinner, String[] labels, String[] values, String selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.xp_spinner_item, R.id.text1, labels);
        adapter.setDropDownViewResource(R.layout.xp_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int index = indexOf(values, selected);
        if (index < 0) index = nearestNumericIndex(values, selected);
        if (index >= 0) spinner.setSelection(index, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) ((TextView) view).setTextColor(getColor(R.color.xp_on_background));
                if (spinner == mGovernor) updateActiveGovernor();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void updateActiveGovernor() {
        String governor = selected(mGovernors, mGovernor);
        ((TextView) findViewById(R.id.kernel_governor_active)).setText(
                governor == null ? "" : "Active: " + governor);
    }

    private void applySettings() {
        String governor = selected(mGovernors, mGovernor);
        String effMin = selected(mEffValues, mEffMin);
        String effMax = selected(mEffValues, mEffMax);
        String perfMin = selected(mPerfValues, mPerfMin);
        String perfMax = selected(mPerfValues, mPerfMax);
        boolean ok = governor != null && effMin != null && effMax != null && perfMin != null && perfMax != null;
        if (ok) ok = mUtils.setGovernor(governor);
        if (ok) ok = mUtils.setEfficiencyClusterFrequency(effMin, effMax);
        if (ok) ok = mUtils.setPerformanceClusterFrequency(perfMin, perfMax);

        if (ok) {
            mPrefs.edit()
                    .putString(KernelManagerUtils.PREF_GOVERNOR, governor)
                    .putString(KernelManagerUtils.PREF_EFFICIENCY_MIN, effMin)
                    .putString(KernelManagerUtils.PREF_EFFICIENCY_MAX, effMax)
                    .putString(KernelManagerUtils.PREF_PERFORMANCE_MIN, perfMin)
                    .putString(KernelManagerUtils.PREF_PERFORMANCE_MAX, perfMax)
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
                    .remove(KernelManagerUtils.PREF_GOVERNOR)
                    .remove(KernelManagerUtils.PREF_EFFICIENCY_MIN)
                    .remove(KernelManagerUtils.PREF_EFFICIENCY_MAX)
                    .remove(KernelManagerUtils.PREF_PERFORMANCE_MIN)
                    .remove(KernelManagerUtils.PREF_PERFORMANCE_MAX)
                    .apply();
        }
        Toast.makeText(this, ok ? R.string.settings_reset : R.string.settings_apply_failed,
                Toast.LENGTH_SHORT).show();
    }

    private static String[] safe(String[] values) { return values == null ? new String[0] : values; }
    private static String[] labels(String[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = cpuMhz(values[i]) + " MHz";
        return labels;
    }
    private static int nearestNumericIndex(String[] values, String selected) {
        if (values == null || values.length == 0 || selected == null) return -1;
        try {
            long target = Long.parseLong(selected);
            int bestIndex = -1;
            long bestDelta = Long.MAX_VALUE;
            for (int i = 0; i < values.length; i++) {
                try {
                    long value = Long.parseLong(values[i]);
                    long delta = Math.abs(value - target);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestIndex = i;
                    }
                } catch (RuntimeException ignored) { }
            }
            return bestIndex;
        } catch (RuntimeException e) {
            return -1;
        }
    }
    private static int indexOf(String[] values, String selected) {
        if (values == null || selected == null) return -1;
        for (int i = 0; i < values.length; i++) if (selected.equals(values[i])) return i;
        return -1;
    }
    private static String selected(String[] values, Spinner spinner) {
        if (values == null || values.length == 0) return null;
        int p = spinner.getSelectedItemPosition();
        return p >= 0 && p < values.length ? values[p] : null;
    }
    private static long cpuMhz(String khz) {
        try { return Long.parseLong(khz) / 1000L; } catch (RuntimeException e) { return 0; }
    }
}
