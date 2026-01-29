/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *               2020-2024 Paranoid Android
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

package co.aospa.glyph.Settings;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.SwitchPreference;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.GlyphScheduleManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Services.BatterySaverService;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class SettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener {

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mBatterySaverPreference;

    private SwitchPreferenceCompat mFlipPreference;
    private SwitchPreferenceCompat mAutoBrightnessPreference;
    private SeekBarPreference mBrightnessPreference;
    private PrimarySwitchPreference mNotifsPreference;
    private PrimarySwitchPreference mCallPreference;
    private SwitchPreferenceCompat mSIMmergePreference;
    private PrimarySwitchPreference mCallPreferenceSIM1;
    private PrimarySwitchPreference mCallPreferenceSIM2;
    private SwitchPreferenceCompat mChargingLevelPreference;
    private SwitchPreferenceCompat mChargingPowersharePreference;
    private SwitchPreferenceCompat mVolumeLevelPreference;
    private SwitchPreferenceCompat mMusicVisualizerPreference;
    private ListPreference mFlipRingerModePreference;
    private SwitchPreferenceCompat mProgressPreference;
    private SwitchPreferenceCompat mProgressMusicPreference;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;
    private Preference mSchedulePreference;

    private Handler mHandler = new Handler();

    private BroadcastReceiver mScheduleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("co.aospa.glyph.UPDATE_MAIN_SWITCH".equals(intent.getAction())) {
                updateMainSwitchState();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.glyph_settings);

        mContentResolver = getActivity().getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);

        boolean glyphEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(glyphEnabled);

        mBatterySaverPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_BATTERY_SAVER_ENABLE);
        mBatterySaverPreference.setOnPreferenceChangeListener(this);

        mFlipPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_FLIP_ENABLE);
        mFlipPreference.setEnabled(glyphEnabled);
        mFlipPreference.setOnPreferenceChangeListener(this);

        mAutoBrightnessPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE);
        mAutoBrightnessPreference.setEnabled(glyphEnabled);
        mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        mAutoBrightnessPreference.setChecked(SettingsManager.isGlyphAutoBrightnessEnabled());
        if (ResourceUtils.getString("glyph_light_sensor").isBlank()) {
            getPreferenceScreen().removePreference(mAutoBrightnessPreference);
        }

        mBrightnessPreference = (SeekBarPreference) findPreference(Constants.GLYPH_BRIGHTNESS);
        if (mAutoBrightnessPreference.isChecked()) {
            mBrightnessPreference.setEnabled(false);
        } else {
            mBrightnessPreference.setEnabled(glyphEnabled);
        }
        mBrightnessPreference.setMin(1);
        mBrightnessPreference.setMax(Constants.getBrightnessLevels().length);
        mBrightnessPreference.setValue(SettingsManager.getGlyphBrightnessSetting());
        mBrightnessPreference.setUpdatesContinuously(true);
        mBrightnessPreference.setOnPreferenceChangeListener(this);

        mNotifsPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_NOTIFS_ENABLE);
        mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
        mNotifsPreference.setEnabled(glyphEnabled);
        mNotifsPreference.setSwitchEnabled(glyphEnabled);
        mNotifsPreference.setOnPreferenceChangeListener(this);

        mSIMmergePreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CALL_SIM_MERGE_ENABLE);
        mSIMmergePreference.setOnPreferenceChangeListener(this);

        mCallPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_CALL_ENABLE);
        mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled(0));
        mCallPreference.setEnabled(glyphEnabled);
        mCallPreference.setSwitchEnabled(glyphEnabled);
        mCallPreference.setOnPreferenceChangeListener(this);

        mCallPreferenceSIM1 = (PrimarySwitchPreference) findPreference(Constants.GLYPH_CALL_ENABLE_SIM1);
        mCallPreferenceSIM1.setChecked(SettingsManager.isGlyphCallEnabled(1));
        mCallPreferenceSIM1.setEnabled(glyphEnabled);
        mCallPreferenceSIM1.setSwitchEnabled(glyphEnabled);
        mCallPreferenceSIM1.setOnPreferenceChangeListener(this);

        mCallPreferenceSIM2 = (PrimarySwitchPreference) findPreference(Constants.GLYPH_CALL_ENABLE_SIM2);
        mCallPreferenceSIM2.setChecked(SettingsManager.isGlyphCallEnabled(2));
        mCallPreferenceSIM2.setEnabled(glyphEnabled);
        mCallPreferenceSIM2.setSwitchEnabled(glyphEnabled);
        mCallPreferenceSIM2.setOnPreferenceChangeListener(this);

        mCallPreference.setVisible(mSIMmergePreference.isChecked());
        mCallPreferenceSIM1.setVisible(!mSIMmergePreference.isChecked());
        mCallPreferenceSIM2.setVisible(!mSIMmergePreference.isChecked());

        mChargingLevelPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CHARGING_LEVEL_ENABLE);
        mChargingLevelPreference.setEnabled(glyphEnabled);
        mChargingLevelPreference.setOnPreferenceChangeListener(this);

        mChargingPowersharePreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE);

        if (Constants.isPowershareSupported()) {
           mChargingPowersharePreference.setEnabled(glyphEnabled);
           mChargingPowersharePreference.setOnPreferenceChangeListener(this);
        } else {
           mChargingPowersharePreference.setVisible(false);
        }

        mVolumeLevelPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_VOLUME_LEVEL_ENABLE);
        mVolumeLevelPreference.setEnabled(glyphEnabled);
        mVolumeLevelPreference.setOnPreferenceChangeListener(this);

        mMusicVisualizerPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE);
        mMusicVisualizerPreference.setEnabled(glyphEnabled);
        mMusicVisualizerPreference.setOnPreferenceChangeListener(this);

        mFlipRingerModePreference = (ListPreference) findPreference(Constants.GLYPH_FLIP_RINGER_MODE);
        mFlipRingerModePreference.setEnabled(glyphEnabled && mFlipPreference.isChecked());
        mFlipRingerModePreference.setOnPreferenceChangeListener(this);

        mSchedulePreference = (Preference) findPreference(Constants.GLYPH_SCHEDULE);
        updateScheduleSummary();

        mProgressPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_PROGRESS_ENABLE);
        mProgressPreference.setEnabled(glyphEnabled);
        mProgressPreference.setOnPreferenceChangeListener(this);

        mProgressMusicPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_PROGRESS_MUSIC_ENABLE);
        mProgressMusicPreference.setEnabled(glyphEnabled && mProgressPreference.isChecked());
        mProgressMusicPreference.setOnPreferenceChangeListener(this);

        IntentFilter filter = new IntentFilter("co.aospa.glyph.UPDATE_MAIN_SWITCH");
        requireContext().registerReceiver(mScheduleUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        mHandler.post(ServiceUtils::checkGlyphService);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(Constants.GLYPH_CALL_ENABLE)) {
            SettingsManager.setGlyphCallEnabled(!mCallPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_ENABLE)) {
            SettingsManager.setGlyphNotifsEnabled(!mNotifsPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE)) {
            mBrightnessPreference.setEnabled(mAutoBrightnessPreference.isChecked());
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_RINGER_MODE)) {
            int mode = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(mContentResolver, 
                Constants.GLYPH_FLIP_RINGER_MODE, mode);
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_ENABLE)) {
            boolean flipEnabled = (Boolean) newValue;
            mFlipRingerModePreference.setEnabled(flipEnabled && SettingsManager.isGlyphEnabled());
        }
        if (preferenceKey.equals(Constants.GLYPH_PROGRESS_ENABLE)) {
            boolean enabled = (Boolean) newValue;
            mProgressMusicPreference.setEnabled(enabled && SettingsManager.isGlyphEnabled());
            
            if (enabled) {
                ServiceUtils.startProgressService();
                mHandler.postDelayed(ServiceUtils::checkGlyphService, 250);
            } else {
                ServiceUtils.checkGlyphService();
            }
            return true;
        }

        if (preferenceKey.equals(Constants.GLYPH_PROGRESS_MUSIC_ENABLE)) {
            mHandler.postDelayed(ServiceUtils::checkGlyphService, 100);
            return true;
        }

        if (preferenceKey.equals(Constants.GLYPH_BATTERY_SAVER_ENABLE)) {
            updateBatterySaver((Boolean) newValue);
        }

        if (preferenceKey.equals(Constants.GLYPH_CALL_SIM_MERGE_ENABLE)) {
            boolean enabled = (boolean) newValue;
            mCallPreference.setVisible(enabled);
            mCallPreferenceSIM1.setVisible(!enabled);
            mCallPreferenceSIM2.setVisible(!enabled);
        }

        mHandler.post(ServiceUtils::checkGlyphService);

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SettingsManager.enableGlyph(isChecked);

        mFlipPreference.setEnabled(isChecked);
        mAutoBrightnessPreference.setEnabled(isChecked);
        mBrightnessPreference.setEnabled(isChecked && !mAutoBrightnessPreference.isChecked());
        mNotifsPreference.setEnabled(isChecked);
        mNotifsPreference.setSwitchEnabled(isChecked);
        mCallPreference.setEnabled(isChecked);
        mCallPreference.setSwitchEnabled(isChecked);
        mChargingLevelPreference.setEnabled(isChecked);
        if (Constants.isPowershareSupported()) {
            mChargingPowersharePreference.setEnabled(isChecked);
        }
        mVolumeLevelPreference.setEnabled(isChecked);
        mMusicVisualizerPreference.setEnabled(isChecked);
        mFlipRingerModePreference.setEnabled(isChecked && mFlipPreference.isChecked());
        mProgressPreference.setEnabled(isChecked);
        mProgressMusicPreference.setEnabled(isChecked && mProgressPreference.isChecked());

        mHandler.post(() -> {
            ServiceUtils.checkGlyphService();
            updateTorchTile();
            updateMainSwitchState();
        });
    }
    
    private void updateTorchTile() {
        try {
            Intent intent = new Intent("co.aospa.glyph.UPDATE_TORCH_TILE");
            requireContext().sendBroadcast(intent);
        } catch (Exception e) {
        }
    }

    private void updateBatterySaver(boolean state) {
        try {
            Intent intent = new Intent(requireContext(), BatterySaverService.class);
            intent.setAction("co.aospa.glyph.UPDATE_BATTERY_SAVER");
            intent.putExtra("status", state);
            requireContext().startService(intent);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (Constants.GLYPH_NOTIFS_ENABLE.equals(preference.getKey())
        || Constants.GLYPH_PROGRESS_ENABLE.equals(preference.getKey())) {
                if (!ServiceUtils.isNotificationServiceEnabled()) {
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.glyph_settings_notifs_permission_dialog_title)
                        .setMessage(R.string.glyph_settings_notifs_permission_dialog_message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            requireContext().startActivity(intent);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                    return true;
                }
            }
    return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onDestroy() {
        mSettingObserver.unregister(mContentResolver);
        try {
            requireContext().unregisterReceiver(mScheduleUpdateReceiver);
        } catch (Exception e) {
            // Receiver not registered
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateScheduleSummary();
        updateMainSwitchState();
    }

    private void updateScheduleSummary() {
        if (mSchedulePreference != null) {
            String summary = GlyphScheduleManager.getScheduleSummary(requireContext());
            mSchedulePreference.setSummary(summary);
        }
    }

    private void updateMainSwitchState() {
        if (mSwitchBar != null) {
            boolean baseEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();
            boolean effectiveEnabled = SettingsManager.isGlyphEnabled();
            boolean batterySavingActive = StatusManager.isBatterySavingActive();
            
            mSwitchBar.setChecked(baseEnabled);
            
            if (baseEnabled && !effectiveEnabled) {
                mSwitchBar.setSummary("Currently disabled by schedule");
            } else if (batterySavingActive) {
                mSwitchBar.setSummary(
                        ResourceUtils.getString("glyph_settings_summary_battery_saving"));
            } else {
                 mSwitchBar.setSummary("");
            }
        }
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_CALL_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_NOTIFS_ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_ENABLE))) {
                mSwitchBar.setChecked(SettingsManager.isGlyphEnabledIgnoreSchedule());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_CALL_ENABLE))) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled(0));
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_CALL_ENABLE_SIM1))) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled(1));
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_CALL_ENABLE_SIM2))) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled(2));
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_NOTIFS_ENABLE))) {
                mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
            }
        }
    }
}
