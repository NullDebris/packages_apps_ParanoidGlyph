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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SliderPreference;

import android.util.Log;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.GlyphScheduleManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Services.BatterySaverService;
import static co.aospa.glyph.Utils.InterfaceUtils.showDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class SettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener {

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mBatterySaverPreference;

    private PrimarySwitchPreference mFlipPreference;
    private SwitchPreferenceCompat mAutoBrightnessPreference;
    private SliderPreference mBrightnessPreference;
    private PrimarySwitchPreference mNotifsPreference;
    private PrimarySwitchPreference mCallPreference;
    private PreferenceCategory mChargingCategory;
    private SwitchPreferenceCompat mChargingLevelPreference;
    private SwitchPreferenceCompat mChargingPowersharePreference;
    private PreferenceCategory mVolumeCategory;
    private SwitchPreferenceCompat mVolumeLevelPreference;
    private SwitchPreferenceCompat mMusicVisualizerPreference;
    private ListPreference mMusicVisualizerModePreference;
    private PreferenceCategory mProgressCategory;
    private SwitchPreferenceCompat mProgressPreference;
    private SwitchPreferenceCompat mProgressMediaPreference;
    private MultiSelectListPreference mProgressMediaWhitelistPreference;
    private PreferenceCategory mRedLedCategory;
    private SwitchPreferenceCompat mMicActivityPreference;
    private MultiSelectListPreference mMicActivityWhitelistPreference;
    private ListPreference mRedLedModePreference;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;
    private Preference mSchedulePreference;

    private static final long BRIGHTNESS_PREVIEW_TIMEOUT_MS = 3000;
    private final Runnable mStopBrightnessPreview = AnimationManager::stopBrightnessPreview;

    private Preference mUtilitiesPreference;

    private CompletableFuture<Boolean> pendingConfirmation;

    private Handler mHandler = new Handler();

    private Context context;

    String[] mediaPermissions = {
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
    };

    String[] micPermissions = {
            Manifest.permission.RECORD_AUDIO
    };

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
        context = getPreferenceManager().getContext();

        addPreferencesFromResource(R.xml.glyph_settings);

        mHandler.post(() -> {
            File callAnimPath
                    = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_CALL_CSV_PATH);
            File notifAnimPath
                    = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_NOTIF_CSV_PATH);
            if (!callAnimPath.exists()) callAnimPath.mkdirs();
            if (!notifAnimPath.exists()) notifAnimPath.mkdirs();
        });

        mContentResolver = getActivity().getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);

        boolean glyphEnabled = SettingsManager.isGlyphEnabledIgnoreSchedule();

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(glyphEnabled);

        mBatterySaverPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_BATTERY_SAVER_ENABLE);
        mBatterySaverPreference.setOnPreferenceChangeListener(this);

        mFlipPreference = findPreference(Constants.GLYPH_FLIP_ENABLE);
        mFlipPreference.setSwitchEnabled(glyphEnabled);
        mFlipPreference.setEnabled(glyphEnabled);
        mFlipPreference.setChecked(SettingsManager.isGlyphFlipEnabled());
        mFlipPreference.setOnPreferenceChangeListener(this);

        mAutoBrightnessPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE);
        mAutoBrightnessPreference.setEnabled(glyphEnabled);
        mAutoBrightnessPreference.setOnPreferenceChangeListener(this);
        mAutoBrightnessPreference.setChecked(SettingsManager.isGlyphAutoBrightnessEnabled());
        if (ResourceUtils.getString("glyph_light_sensor").isBlank()) {
            getPreferenceScreen().removePreference(mAutoBrightnessPreference);
        }

        mBrightnessPreference = (SliderPreference) findPreference(Constants.GLYPH_BRIGHTNESS);
        if (mAutoBrightnessPreference.isChecked()) {
            mBrightnessPreference.setEnabled(false);
        } else {
            mBrightnessPreference.setEnabled(glyphEnabled);
        }
        mBrightnessPreference.setMin(1);
        mBrightnessPreference.setMax(Constants.getBrightnessLevels().length);
        mBrightnessPreference.setValue(SettingsManager.getGlyphBrightnessSetting());
        mBrightnessPreference.setUpdatesContinuously(true);
        mBrightnessPreference.setSliderIncrement(1);
        mBrightnessPreference.setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        mBrightnessPreference.setTickVisible(true);
        mBrightnessPreference.setOnPreferenceChangeListener(this);

        mNotifsPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_NOTIFS_ENABLE);
        mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled()
                && checkNotificationService(false, null));
        mNotifsPreference.setEnabled(glyphEnabled);
        mNotifsPreference.setSwitchEnabled(glyphEnabled
                && checkNotificationService(false, null));
        mNotifsPreference.setOnPreferenceChangeListener(this);

        mCallPreference = (PrimarySwitchPreference) findPreference(Constants.GLYPH_CALL_ENABLE);
        mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
        mCallPreference.setEnabled(glyphEnabled);
        mCallPreference.setSwitchEnabled(glyphEnabled);
        mCallPreference.setOnPreferenceChangeListener(this);

        mChargingCategory = (PreferenceCategory) findPreference(Constants.GLYPH_CHARGING_CATEGORY);
        mChargingLevelPreference = (SwitchPreferenceCompat)
                findPreference(Constants.GLYPH_CHARGING_LEVEL_ENABLE);

        mChargingLevelPreference.setEnabled(glyphEnabled);
        mChargingLevelPreference.setOnPreferenceChangeListener(this);

        mChargingCategory.setVisible(!Constants.Device.isPhone2a());

        mChargingPowersharePreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE);

        if (Constants.isPowershareSupported()) {
           mChargingPowersharePreference.setEnabled(glyphEnabled);
           mChargingPowersharePreference.setOnPreferenceChangeListener(this);
        } else {
           mChargingPowersharePreference.setVisible(false);
        }

        mVolumeCategory = findPreference(Constants.GLYPH_VOLUME_CATEGORY);
        mVolumeCategory.setVisible(!Constants.Device.isPhone1());

        mVolumeLevelPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_VOLUME_LEVEL_ENABLE);
        mVolumeLevelPreference.setEnabled(glyphEnabled);
        mVolumeLevelPreference.setOnPreferenceChangeListener(this);

        mMusicVisualizerPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE);
        mMusicVisualizerPreference.setEnabled(glyphEnabled);
        mMusicVisualizerPreference.setOnPreferenceChangeListener(this);

        mMusicVisualizerModePreference = findPreference(Constants.GLYPH_MUSIC_VISUALIZER_MODE);
        mMusicVisualizerPreference.setEnabled(glyphEnabled);
        mMusicVisualizerModePreference.setOnPreferenceChangeListener(this);

        mSchedulePreference = (Preference) findPreference(Constants.GLYPH_SCHEDULE);
        updateScheduleSummary();

        mProgressCategory = findPreference(Constants.GLYPH_PROGRESS_CATEGORY);

        mProgressCategory.setVisible(!Constants.Device.isPhone1());

        mProgressPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_PROGRESS_ENABLE);
        mProgressPreference.setEnabled(glyphEnabled);
        mProgressPreference.setOnPreferenceChangeListener(this);

        mProgressMediaPreference = (SwitchPreferenceCompat) findPreference(Constants.GLYPH_PROGRESS_MEDIA_ENABLE);
        mProgressMediaPreference.setEnabled(glyphEnabled && mProgressPreference.isChecked());
        mProgressMediaPreference.setOnPreferenceChangeListener(this);

        mProgressMediaWhitelistPreference = findPreference(Constants.GLYPH_PROGRESS_MEDIA_WHITELIST);
        mProgressMediaWhitelistPreference.setEntries(
                ResourceUtils.getApplicationsWithPermission(true, mediaPermissions));
        mProgressMediaWhitelistPreference.setEntryValues(
                ResourceUtils.getApplicationsWithPermission(false, mediaPermissions));

        mRedLedCategory = findPreference(Constants.GLYPH_RED_LED_CATEGORY);
        mRedLedCategory.setVisible(Constants.Device.isPhone2() || Constants.Device.isPhone1());

        mMicActivityPreference = findPreference(Constants.GLYPH_MIC_ACTIVITY_ENABLE);
        mMicActivityPreference.setOnPreferenceChangeListener(this);
        mMicActivityPreference.setEnabled(glyphEnabled);

        mMicActivityWhitelistPreference = findPreference(Constants.GLYPH_MIC_ACTIVITY_WHITELIST);
        mMicActivityWhitelistPreference.setEntries(
                ResourceUtils.getApplicationsWithPermission(true, micPermissions));
        mMicActivityWhitelistPreference.setEntryValues(
                ResourceUtils.getApplicationsWithPermission(false, micPermissions));
        mMicActivityWhitelistPreference.setOnPreferenceChangeListener(this);
        mMicActivityWhitelistPreference.setEnabled(glyphEnabled);

        mRedLedModePreference = findPreference(Constants.GLYPH_RED_LED_MODE);
        mRedLedModePreference.setOnPreferenceChangeListener(this);
        mRedLedModePreference.setEnabled(glyphEnabled);

        mUtilitiesPreference = findPreference(Constants.GLYPH_UTILITIES);

        IntentFilter filter = new IntentFilter("co.aospa.glyph.UPDATE_MAIN_SWITCH");
        requireContext().registerReceiver(mScheduleUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        mHandler.post(ServiceUtils::checkGlyphService);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        switch (preferenceKey) {
            case Constants.GLYPH_FLIP_ENABLE -> {
                SettingsManager.setGlyphFlipEnabled((Boolean) newValue);
            }
            case Constants.GLYPH_CALL_ENABLE -> {
                SettingsManager.setGlyphCallEnabled((Boolean) newValue);
            }
            case Constants.GLYPH_NOTIFS_ENABLE -> {
                if (!checkNotificationService(true, preference)
                        && (Boolean) newValue) {
                    mHandler.post(() -> mNotifsPreference.setChecked(false));
                    return false;
                }
                SettingsManager.setGlyphNotifsEnabled((Boolean) newValue);
            }
            case Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE -> {
                mBrightnessPreference.setEnabled(!(Boolean) newValue);
            }
            case Constants.GLYPH_BRIGHTNESS -> {
                if (SettingsManager.isGlyphEnabled()) {
                    int settingValue = (Integer) newValue;
                    int[] levels = Constants.getBrightnessLevels();
                    int rawBrightness = levels[settingValue - 1];

                    mHandler.removeCallbacks(mStopBrightnessPreview);
                    AnimationManager.previewBrightness(rawBrightness);
                    mHandler.postDelayed(mStopBrightnessPreview, BRIGHTNESS_PREVIEW_TIMEOUT_MS);
                }
            }
            case Constants.GLYPH_PROGRESS_ENABLE -> {
                boolean enabled = (Boolean) newValue;

                if (enabled && !checkNotificationService(true, preference)) return false;

                mProgressMediaPreference.setEnabled(enabled && SettingsManager.isGlyphEnabled());

                if (enabled) {
                    ServiceUtils.startProgressService();
                    mHandler.postDelayed(ServiceUtils::checkGlyphService, 250);
                } else {
                    ServiceUtils.checkGlyphService();
                }
                return true;
            }
            case Constants.GLYPH_PROGRESS_MEDIA_ENABLE -> {
                mHandler.postDelayed(ServiceUtils::checkGlyphService, 100);
                return true;

            }
            case Constants.GLYPH_BATTERY_SAVER_ENABLE -> {
                updateBatterySaver((Boolean) newValue);
            }
            case Constants.GLYPH_MUSIC_VISUALIZER_ENABLE -> {
                if ((Boolean) newValue && SettingsManager.Pulse.isAnyPulseEnabled()) {
                    showDialog(
                            requireActivity(),
                            R.string.glyph_settings_music_visualizer_warning_title,
                            R.string.glyph_settings_music_visualizer_warning_message,
                            R.string.glyph_settings_music_visualizer_disable_pulse,
                            () -> {
                                mMusicVisualizerPreference.setOnPreferenceChangeListener(null);
                                mMusicVisualizerPreference.setChecked(true);
                                mMusicVisualizerPreference.setOnPreferenceChangeListener(this);
                                mHandler.post(ServiceUtils::checkGlyphService);
                            },
                            android.R.string.cancel, null);
                    return false;
                }
            }
        }

        mHandler.post(ServiceUtils::checkGlyphService);

        return true;
    }

    private List<Preference> getAllPreferences(PreferenceGroup group) {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            preferences.add(pref);
            if (pref instanceof PreferenceGroup) {
                preferences.addAll(getAllPreferences((PreferenceGroup) pref));
            }
        }
        return preferences;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SettingsManager.enableGlyph(isChecked);

        List<Preference> allPrefs = getAllPreferences(getPreferenceScreen());

        for (Preference pref : allPrefs) {

            if (pref instanceof PrimarySwitchPreference p) {
                p.setSwitchEnabled(isChecked);
                p.setEnabled(isChecked);
            } else if (pref == (Preference) mBrightnessPreference) {
                pref.setEnabled(!mAutoBrightnessPreference.isChecked() && isChecked);
            } else if (pref == (Preference) mBatterySaverPreference || pref == mSchedulePreference
                    || pref == mUtilitiesPreference) {
                ; // skip
            } else if (pref instanceof MainSwitchPreference m) {
                ; // skip
            } else if (pref instanceof PreferenceCategory c) {
                ; // skip
            } else {
                pref.setEnabled(isChecked);
            }
        }

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
        if (Constants.GLYPH_NOTIFS_ENABLE.equals(preference.getKey())) {
            if (!checkNotificationService(true, preference, true)) return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean checkNotificationService(boolean showDialog, Preference targetPref,
                                             boolean treeClick) {
        if (!ServiceUtils.isNotificationServiceEnabled(context)) {
           if (showDialog) {
               showDialog(
                   requireActivity(),
                   R.string.glyph_settings_notifs_permission_dialog_title,
                   R.string.glyph_settings_notifs_permission_dialog_message,
                   android.R.string.ok, () -> {
                       Intent intent
                               = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                       requireContext().startActivity(intent);
                       pendingConfirmation = new CompletableFuture<>();
                       pendingConfirmation.thenAccept(confirmed -> {
                           if (confirmed) {
                               if (treeClick) {
                                   mHandler.post(() -> {
                                       Intent treeIntent = targetPref.getIntent();
                                       if (treeIntent != null) requireContext().startActivity(treeIntent);
                                   });
                               } else {
                                   targetPref.getSharedPreferences()
                                           .edit()
                                           .putBoolean(targetPref.getKey(), true)
                                           .apply();
                                   if (targetPref instanceof TwoStatePreference p) {
                                       p.setChecked(true);
                                   }
                               }
                               if (!treeClick) mHandler.post(ServiceUtils::checkGlyphService);
                           }
                       });
                   },
                   android.R.string.cancel, null);
           }
            return false;
        }
        return true;
    }

    private boolean checkNotificationService(boolean showDialog, Preference targetPref) {
        return checkNotificationService(showDialog, targetPref, false);
    }

    @Override
    public void onDestroy() {
        mSettingObserver.unregister(mContentResolver);
        try {
            requireContext().unregisterReceiver(mScheduleUpdateReceiver);
        } catch (Exception e) {
            // Receiver not registered
        }
        if (mHandler.hasCallbacks(mStopBrightnessPreview)) {
            mHandler.removeCallbacks(mStopBrightnessPreview);
            mStopBrightnessPreview.run();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mNotifsPreference.setSwitchEnabled(mSwitchBar.isChecked()
                && checkNotificationService(false, null));
        if (pendingConfirmation != null && !pendingConfirmation.isDone()) {
            if (checkNotificationService(false, null)) {
                pendingConfirmation.complete(true);
                pendingConfirmation = null;
            }
        }
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
                mSwitchBar.setSummary(getString(R.string.glyph_settings_summary_schedule));
            } else if (batterySavingActive) {
                mSwitchBar.setSummary(getString(R.string.glyph_settings_summary_battery_saving));
            } else {
                 mSwitchBar.setSummary("");
            }
        }
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_CALL_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_NOTIFS_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_FLIP_ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_ENABLE))
                    && mSwitchBar != null) {
                mSwitchBar.setChecked(SettingsManager.isGlyphEnabledIgnoreSchedule());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_FLIP_ENABLE))
                    && mFlipPreference != null) {
                mFlipPreference.setChecked(SettingsManager.isGlyphFlipEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_CALL_ENABLE))
                    && mCallPreference != null) {
                mCallPreference.setChecked(SettingsManager.isGlyphCallEnabled());
            }
            if (uri.equals(Settings.Secure.getUriFor(Constants.GLYPH_NOTIFS_ENABLE))
                    && mNotifsPreference != null) {
                mNotifsPreference.setChecked(SettingsManager.isGlyphNotifsEnabled());
            }
        }
    }
}