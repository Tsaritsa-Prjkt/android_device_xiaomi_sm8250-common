// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.kcal;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.lineageos.settings.R;

import java.io.IOException;

public class KcalSettingsFragment extends Fragment {
    private static final int[] LABELS = { R.string.kcal_red, R.string.kcal_green,
            R.string.kcal_blue, R.string.kcal_saturation, R.string.kcal_value,
            R.string.kcal_contrast, R.string.kcal_hue };
    private static final int TEAL = 0xff00bfa5;
    private static final int[] COLORS = {
            0xffe53935, 0xff43a047, 0xff1e88e5, TEAL, TEAL, TEAL, TEAL
    };
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SeekBar[] sliders = new SeekBar[LABELS.length];
    private KcalSettings settings;
    private CompoundButton toggle;
    private View reset;
    private TextView status;
    private int generation;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        return inflater.inflate(R.layout.kcal_settings_layout, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        settings = new KcalSettings(requireContext());
        toggle = view.findViewById(R.id.kcal_switch);
        reset = view.findViewById(R.id.kcal_reset);
        status = view.findViewById(R.id.kcal_status);
        LinearLayout rows = view.findViewById(R.id.kcal_sliders);
        for (int i = 0; i < sliders.length; i++) addSlider(rows, i);
        toggle.setOnClickListener(v -> update(toggle.isChecked(), values()));
        reset.setOnClickListener(v -> update(toggle.isChecked(), KcalNodes.DEFAULT.clone()));
    }

    private void addSlider(LinearLayout rows, int index) {
        View row = getLayoutInflater().inflate(R.layout.kcal_slider, rows, false);
        if (index == 3) {
            row.findViewById(R.id.kcal_divider).setVisibility(View.VISIBLE);
        }
        rows.addView(row);
        TextView label = row.findViewById(R.id.kcal_label);
        TextView number = row.findViewById(R.id.kcal_number);
        label.setText(LABELS[index]);
        SeekBar slider = row.findViewById(R.id.kcal_slider);
        sliders[index] = slider;
        slider.setContentDescription(getString(LABELS[index]));
        slider.setMin(KcalNodes.MIN[index]);
        slider.setMax(KcalNodes.MAX[index]);
        slider.setProgress(KcalNodes.DEFAULT[index]);
        number.setText(Integer.toString(slider.getProgress()));
        ColorStateList tint = ColorStateList.valueOf(COLORS[index]);
        slider.setThumbTintList(tint);
        slider.setProgressTintList(tint);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean tracking;

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                tracking = true;
            }

            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                number.setText(Integer.toString(progress));
                if (fromUser && !tracking) update(true, values()); // Keyboard or accessibility.
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                tracking = false;
                if (bar.isEnabled()) update(true, values());
            }
        });
    }

    private int[] values() {
        int[] result = new int[sliders.length];
        for (int i = 0; i < result.length; i++) result[i] = sliders[i].getProgress();
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        update(null, null);
    }

    private void update(Boolean requested, int[] profile) {
        final int task = ++generation;
        final KcalSettings current = settings;
        setControls(false, false);
        KcalSettings.IO.execute(() -> {
            boolean failed = false;
            if (requested == null) {
                current.discover();
            } else {
                try {
                    current.change(requested, profile);
                } catch (IOException e) {
                    failed = true;
                    Log.e("XiaomiParts", "Could not apply KCAL", e);
                    current.discover();
                }
            }
            KcalNodes nodes = current.nodes;
            int[] saved = current.profile();
            boolean enabled = nodes != null && current.enabled();
            int message = nodes == null ? R.string.kcal_unavailable
                    : failed ? R.string.kcal_error
                    : enabled ? R.string.kcal_active : R.string.kcal_inactive;
            main.post(() -> {
                if (task != generation || getView() == null) return;
                toggle.setChecked(enabled);
                for (int i = 0; i < saved.length; i++) {
                    sliders[i].setMin(nodes == null ? KcalNodes.MIN[i] : nodes.minimum(i));
                    sliders[i].setProgress(saved[i]);
                }
                status.setText(message);
                setControls(nodes != null, enabled);
            });
        });
    }

    private void setControls(boolean supported, boolean enabled) {
        toggle.setEnabled(supported);
        reset.setEnabled(supported);
        for (SeekBar slider : sliders) {
            slider.setEnabled(enabled);
            slider.setAlpha(enabled ? 1f : 0.38f);
        }
    }

    @Override
    public void onDestroyView() {
        generation++;
        java.util.Arrays.fill(sliders, null);
        toggle = null;
        reset = null;
        status = null;
        super.onDestroyView();
    }
}
