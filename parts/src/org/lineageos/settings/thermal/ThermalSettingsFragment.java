/*
 * Copyright (C) 2020 The LineageOS Project
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
package org.lineageos.settings.thermal;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ThermalSettingsFragment extends SettingsBasePreferenceFragment
        implements ApplicationsState.Callbacks {

    private static final int[] MODE_LABELS = {
            R.string.thermal_default,
            R.string.thermal_benchmark,
            R.string.thermal_browser,
            R.string.thermal_camera,
            R.string.thermal_dialer,
            R.string.thermal_gaming,
            R.string.thermal_streaming
    };

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private ThermalUtils mThermalUtils;
    private RecyclerView mAppsRecyclerView;
    private EditText mSearchView;
    private ImageButton mClearSearch;
    private Switch mEnabledSwitch;
    private boolean mProfilesEnabled;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mActivityFilter = new ActivityFilter(requireActivity().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter();
        mThermalUtils = new ThermalUtils(requireContext());
        mProfilesEnabled = ThermalUtils.isServiceEnabled(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.thermal_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAppsRecyclerView = view.findViewById(R.id.thermal_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);

        mEnabledSwitch = view.findViewById(R.id.thermal_enabled);
        mEnabledSwitch.setChecked(mProfilesEnabled);
        mEnabledSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            mProfilesEnabled = enabled;
            ThermalUtils.setServiceEnabled(requireContext(), enabled);
            updateEnabledState();
        });
        view.findViewById(R.id.thermal_enabled_container).setOnClickListener(
                v -> mEnabledSwitch.toggle());

        mSearchView = view.findViewById(R.id.thermal_search);
        mClearSearch = view.findViewById(R.id.thermal_search_clear);
        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                mClearSearch.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
                mAllPackagesAdapter.filter(text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mClearSearch.setOnClickListener(v -> {
            mSearchView.setText("");
            mSearchView.requestFocus();
        });

        updateEnabledState();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.thermal_title);
        mProfilesEnabled = ThermalUtils.isServiceEnabled(requireContext());
        mEnabledSwitch.setChecked(mProfilesEnabled);
        updateEnabledState();
        mSession.onResume();
        rebuild();
    }

    @Override
    public void onPause() {
        mSession.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mAppsRecyclerView != null) {
            mAppsRecyclerView.setAdapter(null);
            mAppsRecyclerView = null;
        }
        mSearchView = null;
        mClearSearch = null;
        mEnabledSwitch = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mSession.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null && isAdded()) {
            mAllPackagesAdapter.setEntries(entries);
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private void updateEnabledState() {
        if (getView() != null) {
            TextView summary = getView().findViewById(R.id.thermal_enabled_summary);
            summary.setText(mProfilesEnabled ? R.string.thermal_enable_summary
                    : R.string.thermal_disabled_summary);
        }
        if (mSearchView != null) {
            mSearchView.setEnabled(mProfilesEnabled);
            mClearSearch.setEnabled(mProfilesEnabled);
        }
        if (mAppsRecyclerView != null) {
            mAppsRecyclerView.setAlpha(mProfilesEnabled ? 1f : 0.5f);
        }
        mAllPackagesAdapter.notifyDataSetChanged();
    }

    private int clampState(int state) {
        return Math.max(0, Math.min(state, MODE_LABELS.length - 1));
    }

    private void showModeDialog(ApplicationsState.AppEntry entry, int selectedState) {
        final String[] labels = new String[MODE_LABELS.length];
        for (int i = 0; i < MODE_LABELS.length; i++) {
            labels[i] = getString(MODE_LABELS[i]);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.thermal_profile_dialog_title, entry.label))
                .setSingleChoiceItems(labels, selectedState, (dialog, which) -> {
                    if (mProfilesEnabled && which != selectedState) {
                        mThermalUtils.writePackage(entry.info.packageName, which);
                        int position = mAllPackagesAdapter.indexOf(entry);
                        if (position >= 0) {
                            mAllPackagesAdapter.notifyItemChanged(position);
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView mode;
        private final ImageView icon;
        private final ImageButton touchIcon;

        private ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.app_name);
            mode = view.findViewById(R.id.app_mode);
            icon = view.findViewById(R.id.app_icon);
            touchIcon = view.findViewById(R.id.touch);
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder>
            implements SectionIndexer {

        private final List<ApplicationsState.AppEntry> mAllEntries = new ArrayList<>();
        private final List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections = new String[0];
        private int[] mPositions = new int[0];
        private String mQuery = "";

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thermal_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApplicationsState.AppEntry entry = mEntries.get(position);
            int packageState = mProfilesEnabled ? clampState(
                    mThermalUtils.getStateForPackage(entry.info.packageName))
                    : ThermalUtils.STATE_DEFAULT;

            holder.title.setText(entry.label);
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);
            holder.mode.setText(MODE_LABELS[packageState]);
            holder.mode.setEnabled(mProfilesEnabled);
            holder.mode.setOnClickListener(v -> {
                if (mProfilesEnabled) {
                    showModeDialog(entry, packageState);
                }
            });
            holder.title.setOnClickListener(v -> holder.mode.performClick());

            boolean hasTouchControls = packageState == ThermalUtils.STATE_GAMING
                    || packageState == ThermalUtils.STATE_BENCHMARK;
            holder.touchIcon.setVisibility(hasTouchControls ? View.VISIBLE : View.GONE);
            holder.touchIcon.setEnabled(mProfilesEnabled);
            holder.touchIcon.setOnClickListener(v -> openTouchSettings(entry));
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries) {
            mAllEntries.clear();
            mAllEntries.addAll(entries);
            filter(mQuery);
        }

        private void filter(String query) {
            mQuery = query == null ? "" : query.trim();
            String normalizedQuery = mQuery.toLowerCase(Locale.getDefault());
            mEntries.clear();

            for (ApplicationsState.AppEntry entry : mAllEntries) {
                String label = entry.label == null ? "" : entry.label.toString();
                if (normalizedQuery.isEmpty()
                        || label.toLowerCase(Locale.getDefault()).contains(normalizedQuery)) {
                    mEntries.add(entry);
                }
            }

            rebuildSections();
            notifyDataSetChanged();
        }

        private void rebuildSections() {
            ArrayList<String> sections = new ArrayList<>();
            ArrayList<Integer> positions = new ArrayList<>();
            String lastSection = null;

            for (int i = 0; i < mEntries.size(); i++) {
                String label = String.valueOf(mEntries.get(i).label);
                String section = TextUtils.isEmpty(label)
                        ? "" : label.substring(0, 1).toUpperCase(Locale.getDefault());
                if (!TextUtils.equals(section, lastSection)) {
                    sections.add(section);
                    positions.add(i);
                    lastSection = section;
                }
            }

            mSections = sections.toArray(new String[0]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                mPositions[i] = positions.get(i);
            }
        }

        private int indexOf(ApplicationsState.AppEntry entry) {
            return mEntries.indexOf(entry);
        }

        @Override
        public int getPositionForSection(int section) {
            if (section < 0 || section >= mSections.length) {
                return -1;
            }
            return mPositions[section];
        }

        @Override
        public int getSectionForPosition(int position) {
            if (position < 0 || position >= getItemCount()) {
                return -1;
            }
            final int index = Arrays.binarySearch(mPositions, position);
            return index >= 0 ? index : -index - 2;
        }

        @Override
        public Object[] getSections() {
            return mSections;
        }
    }

    private void openTouchSettings(ApplicationsState.AppEntry entry) {
        TouchSettingsFragment fragment = new TouchSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putString("appName", String.valueOf(entry.label));
        arguments.putString("packageName", entry.info.packageName);
        fragment.setArguments(arguments);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();
    }

    private static class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<>();

        private ActivityFilter(PackageManager packageManager) {
            mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        private void updateLauncherInfoList() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(intent, 0);

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    mLauncherResolveInfoList.add(resolveInfo.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherResolveInfoList) {
                return mLauncherResolveInfoList.contains(entry.info.packageName);
            }
        }
    }
}
