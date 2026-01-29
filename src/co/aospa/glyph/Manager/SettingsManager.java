/*
 * Copyright (C) 2022-2024 Paranoid Android
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

package co.aospa.glyph.Manager;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.android.internal.util.ArrayUtils;

import java.util.HashSet;
import java.util.Set;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public final class SettingsManager {

    private static final String TAG = "GlyphSettingsManager";
    private static final boolean DEBUG = true;
    private static Context context;

    private static Context getContext() {
        if (context == null) {
            if (Constants.CONTEXT == null) {
                throw new IllegalStateException("Constants.CONTEXT is not initialized");
            }
            context = Constants.CONTEXT;
        }
        return context;
    }

    public static boolean enableGlyph(boolean enable) {
        Context ctx = getContext();
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putBoolean(Constants.GLYPH_ENABLE, enable).apply();

        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_ENABLE, enable ? 1 : 0);
    }

    public static boolean isGlyphEnabled() {
        Context ctx = getContext();
        boolean baseEnabled = (Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_ENABLE, 1) != 0 
            || PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_ENABLE, false));
        
        if (GlyphScheduleManager.isScheduleEnabled(ctx) && 
            GlyphScheduleManager.isScheduleCurrentlyActive(ctx)) {
            return false;
        }
        
        return baseEnabled;
    }

    public static boolean isGlyphEnabledIgnoreSchedule() {
        Context ctx = getContext();
        return (Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_ENABLE, 1) != 0 
            || PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_ENABLE, false));
    }

    public static boolean isGlyphFlipEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_FLIP_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphFlipAnimationEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_FLIP_ANIMATION_ENABLE, true) && isGlyphEnabled();
    }

    public static int getGlyphBrightness() {
        int[] levels = Constants.getBrightnessLevels();
        int brightnessSetting = getGlyphBrightnessSetting();
        return levels[brightnessSetting - 1];
    }

    public static int getGlyphBrightnessSetting() {
        Context ctx = getContext();
        int d = 3; if (FileUtils.readLine("/mnt/vendor/persist/color") == "white") d = 2;
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getInt(Constants.GLYPH_BRIGHTNESS, d);
    }

    public static boolean isGlyphBatterySaverEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_BATTERY_SAVER_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphChargingEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_CHARGING_LEVEL_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphPowershareEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_CHARGING_POWERSHARE_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphCallEnabled(int idx) {
        String suffix = "";
        switch (idx) {
            case -1 -> suffix = "";
            case 0 -> suffix = "_sim1";
            case 1 -> suffix = "_sim2";
        }

        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_CALL_ENABLE + suffix, 1) != 0 && isGlyphEnabled();
    }

    public static boolean isGlyphCallAnimationMerged() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_CALL_SIM_MERGE_ENABLE, 1) != 0 && isGlyphEnabled();
    }

    public static boolean setGlyphCallEnabled(boolean enable) {
        Context ctx = getContext();
        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_CALL_ENABLE, enable ? 1 : 0);
    }

    public static String getGlyphCallAnimation(int idx) {
        String suffix = "";
        switch (idx) {
            case -1 -> suffix = "";
            case 0 -> suffix = "_sim1";
            case 1 -> suffix = "_sim2";
        }
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_CALL_SUB_ANIMATIONS + suffix,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static boolean isGlyphCallAnimationReversed(int idx) {
        String suffix = "";
        switch (idx) {
            case -1 -> suffix = "";
            case 0 -> suffix = "_sim1";
            case 1 -> suffix = "_sim2";
        }
        Context ctx = getContext();
         return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE + suffix,
                        false);
    }

    public static boolean isGlyphMusicVisualizerEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphVolumeLevelEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_VOLUME_LEVEL_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphNotifsEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_NOTIFS_ENABLE, 1) != 0 && isGlyphEnabled();
    }

    public static boolean setGlyphNotifsEnabled(boolean enable) {
        Context ctx = getContext();
        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_NOTIFS_ENABLE, enable ? 1 : 0);
    }

    public static String getGlyphNotifsAnimation() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static boolean isGlyphNotifsAnimationReversed() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphNotifsAppEnabled(String app) {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(app, true) && isGlyphNotifsEnabled();
    }

    public static boolean isGlyphNotifsAppEssential(String app) {
        Context ctx = getContext();
        Set<String> selectedValues = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.GLYPH_NOTIFS_SUB_ESSENTIAL , new HashSet<String>());
        return selectedValues.contains(app) && isGlyphNotifsEnabled();
    }

    public static boolean isGlyphAutoBrightnessEnabled() {
        Context ctx = getContext();
        return !ResourceUtils.getString("glyph_light_sensor").isBlank() 
            && PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE, false) 
            && isGlyphEnabled();
    }

    public static int getFlipRingerMode() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                Constants.GLYPH_FLIP_RINGER_MODE, AudioManager.RINGER_MODE_VIBRATE);
    }

    public static boolean isGlyphProgressEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_PROGRESS_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphProgressMusicEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_PROGRESS_MUSIC_ENABLE, false) && isGlyphProgressEnabled();
    }
}
