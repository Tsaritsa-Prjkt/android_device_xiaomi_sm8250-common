/*
 * Copyright (C) 2018 The LineageOS Project
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

package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.CustomSeekBarPreference;
import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

public class DisplaySettingsFragment extends SettingsBasePreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_SATURATION_CATEGORY = "saturation_category";
    private static final String KEY_SATURATION_PREVIEW = "saturation_preview";
    private static final String KEY_SATURATION = "saturation";
    private static final int DEFAULT_SATURATION = 100;

    private SwitchPreferenceCompat mDcDimmingPreference;
    private SwitchPreferenceCompat mHBMPreference;
    private SwitchPreferenceCompat mAutoHBMPreference;
    private SeekBarPreference mAutoHBMThresholdPreference;
    private SeekBarPreference mHBMDisableTimePreference;
    private CustomSeekBarPreference mSaturationPreference;

    private View mViewArrowPrevious;
    private View mViewArrowNext;
    private ViewPager mViewPager;
    private ImageView[] mDotIndicators;
    private View[] mViewPagerImages;

    private String mDcDimmingEnableKey;
    private String mDcDimmingNode;
    private String mHbmEnableKey;
    private String mAutoHbmEnableKey;
    private String mHbmNode;

    private SharedPreferences mPrefs;
    private SaturationController mSaturationController;
    private boolean mHbmSupported;
    private boolean mAutoHbmSupported;
    private boolean mScrollToSaturation;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mDcDimmingEnableKey = DisplayNodes.getDcDimmingEnableKey();
        mDcDimmingNode = DisplayNodes.getDcDimmingNode();
        mHbmEnableKey = DisplayNodes.getHbmEnableKey();
        mAutoHbmEnableKey = DisplayNodes.getAutoHbmEnableKey();
        mHbmNode = DisplayNodes.getHbmNode();

        addPreferencesFromResource(R.xml.display_settings);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        mDcDimmingPreference = findPreference(mDcDimmingEnableKey);
        mHBMPreference = findPreference(mHbmEnableKey);
        mAutoHBMPreference = findPreference(mAutoHbmEnableKey);
        mAutoHBMThresholdPreference = findPreference(DisplayNodes.getAutoHbmThresholdKey());
        mHBMDisableTimePreference = findPreference(DisplayNodes.getAutoHbmDisableTimeKey());

        mSaturationController = new SaturationController();
        LayoutPreference preview = findPreference(KEY_SATURATION_PREVIEW);
        if (preview != null) {
            addViewPager(preview);
        }
        mSaturationPreference = findPreference(KEY_SATURATION);
        if (mSaturationPreference != null) {
            mSaturationPreference.setOnPreferenceChangeListener(this);
            mSaturationController.apply(mPrefs.getInt(KEY_SATURATION, DEFAULT_SATURATION));
        }

        if (FileUtils.fileExists(mDcDimmingNode)) {
            mDcDimmingPreference.setEnabled(true);
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            mDcDimmingPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
            mDcDimmingPreference.setEnabled(false);
        }

        mHbmSupported = FileUtils.fileExists(mHbmNode);
        mAutoHbmSupported = mHbmSupported && hasLightSensor(requireContext());

        if (mHbmSupported) {
            mHBMPreference.setOnPreferenceChangeListener(this);
        } else {
            mHBMPreference.setSummary(R.string.hbm_enable_summary_not_supported);
        }

        mAutoHBMPreference.setPersistent(false);
        mAutoHBMPreference.setChecked(mPrefs.getBoolean(mAutoHbmEnableKey, false));
        mAutoHBMPreference.setOnPreferenceChangeListener(this);
        if (!mAutoHbmSupported) {
            mAutoHBMPreference.setChecked(false);
            mAutoHBMPreference.setSummary(R.string.auto_hbm_summary_not_supported);
            mPrefs.edit().putBoolean(mAutoHbmEnableKey, false).apply();
            AutoHBMService.stop(requireContext());
        }

        updateHbmPreferenceState();

        if (requireActivity().getIntent().getBooleanExtra(
                DisplaySettingsActivity.EXTRA_SCROLL_TO_SATURATION, false)) {
            mScrollToSaturation = true;
            requireActivity().getIntent().removeExtra(
                    DisplaySettingsActivity.EXTRA_SCROLL_TO_SATURATION);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mScrollToSaturation) {
            mScrollToSaturation = false;
            scrollToPreference(KEY_SATURATION_CATEGORY);
        }
    }

    private static boolean hasLightSensor(Context context) {
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        return sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    private void updateHbmPreferenceState() {
        boolean autoEnabled = mAutoHBMPreference.isChecked() && mAutoHbmSupported;
        mHBMPreference.setEnabled(mHbmSupported && !autoEnabled);
        mAutoHBMPreference.setEnabled(mAutoHbmSupported);
        mAutoHBMThresholdPreference.setEnabled(mAutoHbmSupported && autoEnabled);
        mHBMDisableTimePreference.setEnabled(mAutoHbmSupported && autoEnabled);
    }

    private boolean disableManualHbm() {
        if (!HBMController.disable(requireContext(), mPrefs)) {
            return false;
        }
        mPrefs.edit().putBoolean(mHbmEnableKey, false).apply();
        mHBMPreference.setChecked(false);
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mDcDimmingEnableKey.equals(preference.getKey())) {
            FileUtils.writeLine(mDcDimmingNode, (Boolean) newValue ? "1" : "0");
            return true;
        }

        if (mHbmEnableKey.equals(preference.getKey())) {
            if (mPrefs.getBoolean(mAutoHbmEnableKey, false)) {
                return false;
            }

            boolean enabled = (Boolean) newValue;
            return enabled
                    ? HBMController.enable(requireContext(), mPrefs)
                    : HBMController.disable(requireContext(), mPrefs);
        }

        if (mAutoHbmEnableKey.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            if (!mAutoHbmSupported) {
                return false;
            }

            if (enabled) {
                if (!disableManualHbm()) {
                    return false;
                }
            } else if (!HBMController.disable(requireContext(), mPrefs)) {
                return false;
            }

            mPrefs.edit().putBoolean(mAutoHbmEnableKey, enabled).apply();
            mAutoHBMPreference.setChecked(enabled);

            if (enabled) {
                AutoHBMService.start(requireContext());
            } else {
                AutoHBMService.stop(requireContext());
            }

            updateHbmPreferenceState();
            return true;
        }

        if (KEY_SATURATION.equals(preference.getKey())) {
            mSaturationController.apply((Integer) newValue);
            return true;
        }

        return false;
    }

    private void addViewPager(LayoutPreference preview) {
        mViewPager = preview.findViewById(R.id.viewpager);
        int[] drawables = {
                R.drawable.image_preview1,
                R.drawable.image_preview2,
                R.drawable.image_preview3
        };

        mViewPagerImages = new View[drawables.length];
        for (int i = 0; i < drawables.length; i++) {
            View imageView = getLayoutInflater().inflate(R.layout.image_layout, null);
            imageView.<ImageView>findViewById(R.id.imageView).setImageResource(drawables[i]);
            mViewPagerImages[i] = imageView;
        }

        mViewPager.setAdapter(new ImagePreviewPagerAdapter(mViewPagerImages));

        mViewArrowPrevious = preview.findViewById(R.id.arrow_previous);
        mViewArrowPrevious.setOnClickListener(v ->
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1, true));
        mViewArrowNext = preview.findViewById(R.id.arrow_next);
        mViewArrowNext.setOnClickListener(v ->
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1, true));

        mViewPager.addOnPageChangeListener(createPageListener());

        ViewGroup viewGroup = preview.findViewById(R.id.viewGroup);
        mDotIndicators = new ImageView[mViewPagerImages.length];
        for (int i = 0; i < mDotIndicators.length; i++) {
            ImageView dot = new ImageView(requireContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(12, 12);
            lp.setMargins(6, 0, 6, 0);
            dot.setLayoutParams(lp);
            viewGroup.addView(dot);
            mDotIndicators[i] = dot;
        }

        updateIndicator(mViewPager.getCurrentItem());
    }

    private ViewPager.OnPageChangeListener createPageListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                    int positionOffsetPixels) {
                if (positionOffset != 0f) {
                    for (View view : mViewPagerImages) {
                        view.setVisibility(View.VISIBLE);
                    }
                } else {
                    mViewPagerImages[position].setContentDescription(
                            getString(R.string.image_preview_content_description));
                    updateIndicator(position);
                }
            }

            @Override
            public void onPageSelected(int position) { }

            @Override
            public void onPageScrollStateChanged(int state) { }
        };
    }

    private void updateIndicator(int position) {
        for (int i = 0; i < mViewPagerImages.length; i++) {
            mDotIndicators[i].setBackgroundResource(position == i
                    ? R.drawable.ic_image_preview_page_indicator_focused
                    : R.drawable.ic_image_preview_page_indicator_unfocused);
            mViewPagerImages[i].setVisibility(position == i ? View.VISIBLE : View.INVISIBLE);
        }

        mViewArrowPrevious.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        mViewArrowNext.setVisibility(position == mViewPagerImages.length - 1
                ? View.INVISIBLE : View.VISIBLE);
    }

    private static final class ImagePreviewPagerAdapter extends PagerAdapter {
        private final View[] mPageViewList;

        ImagePreviewPagerAdapter(View[] pageViewList) {
            mPageViewList = pageViewList;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(mPageViewList[position]);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(mPageViewList[position]);
            return mPageViewList[position];
        }

        @Override
        public int getCount() {
            return mPageViewList.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return object == view;
        }
    }
}
