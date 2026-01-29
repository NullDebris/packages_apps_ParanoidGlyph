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

package co.aospa.glyph.Constants;

import android.content.Context;

import co.aospa.glyph.Utils.ResourceUtils;

public final class Constants {

    private static final String TAG = "GlyphConstants";
    private static final boolean DEBUG = true;

    public static Context CONTEXT;
    public static final int MAX_PATTERN_BRIGHTNESS = 4095;

    private static String device = null;

    private static int brightness = -1;
    private static int brightnessMax = -1;
    private static int[] brightnessLevels = null;
    private static int[] supportedAnimationPatternLengths = null;

    public static final String GLYPH_ENABLE = "glyph_enable";
    public static final String GLYPH_FLIP_ENABLE = "glyph_settings_flip_toggle";
    public static final String GLYPH_FLIP_ANIMATION_ENABLE = "glyph_settings_flip_animation_toggle";
    public static final String GLYPH_BRIGHTNESS = "glyph_settings_brightness";
    public static final String GLYPH_BATTERY_SAVER_ENABLE = "glyph_settings_battery_saver_toggle";
    public static final String GLYPH_CHARGING_CATEGORY = "glyph_settings_charging";
    public static final String GLYPH_CHARGING_LEVEL_ENABLE = "glyph_settings_charging_level";
    public static final String GLYPH_CHARGING_POWERSHARE_ENABLE = "glyph_settings_charging_powershare";
    public static final String GLYPH_CALL_CATEGORY = "glyph_settings_call";
    public static final String GLYPH_CALL_ENABLE = "glyph_settings_call_toggle";
    public static final String GLYPH_CALL_SIM_MERGE_ENABLE = "glyph_settings_call_sim_merge_toggle";

    public static final String GLYPH_CALL_ENABLE_SIM1 = "glyph_settings_call_toggle_sim1";
    public static final String GLYPH_CALL_ENABLE_SIM2 = "glyph_settings_call_toggle_sim2";
    public static final String GLYPH_CALL_REVERSE_ANIMATION_ENABLE = "glyph_settings_call_sub_animations_reverse_toggle";
    public static final String GLYPH_CALL_SUB_PREVIEW = "glyph_settings_call_sub_preview";
    public static final String GLYPH_CALL_SUB_ANIMATIONS = "glyph_settings_call_sub_animations";
    public static final String GLYPH_CALL_SUB_ANIMATIONS_SIM1 = "glyph_settings_call_sub_animations_sim1";
    public static final String GLYPH_CALL_SUB_ANIMATIONS_SIM2 = "glyph_settings_call_sub_animations_sim2";
    public static final String GLYPH_CALL_SUB_LIVE_PREVIEW = "glyph_settings_call_sub_animations_live_preview";
    public static final String GLYPH_CALL_SUB_ENABLE = "glyph_settings_call_sub_toggle";
    public static final String GLYPH_CALL_SUB_ENABLE_SIM1 = "glyph_settings_call_sub_toggle";
    public static final String GLYPH_CALL_SUB_ENABLE_SIM2 = "glyph_settings_call_sub_toggle";
    public static final String GLYPH_MUSIC_VISUALIZER_ENABLE = "glyph_settings_music_visualizer_toggle";
    public static final String GLYPH_NOTIFS_ENABLE = "glyph_settings_notifs_toggle";
    public static final String GLYPH_NOTIFS_SUB_PREVIEW = "glyph_settings_notifs_sub_preview";
    public static final String GLYPH_NOTIFS_SUB_ANIMATIONS = "glyph_settings_notifs_sub_animations";
    public static final String GLYPH_NOTIFS_SUB_LIVE_PREVIEW = "glyph_settings_notifs_sub_animations_live_preview";
    public static final String GLYPH_NOTIFS_SUB_ESSENTIAL = "glyph_settings_notifs_sub_essential";
    public static final String GLYPH_NOTIFS_SUB_CATEGORY = "glyph_settings_notifs_sub";
    public static final String GLYPH_NOTIFS_SUB_ENABLE = "glyph_settings_notifs_sub_toggle";
    public static final String GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE = "glyph_settings_notifs_sub_animations_reverse_toggle";
    public static final String GLYPH_VOLUME_LEVEL_ENABLE = "glyph_settings_volume_level_toggle";
    public static final String GLYPH_AUTO_BRIGHTNESS_ENABLE = "glyph_settings_auto_brightness_toggle";
    public static final String GLYPH_FLIP_RINGER_MODE = "glyph_settings_flip_ringer_mode";
    public static final String GLYPH_SCHEDULE = "glyph_settings_schedule";
    public static final String GLYPH_PROGRESS_ENABLE = "glyph_settings_progress_toggle";
    public static final String GLYPH_PROGRESS_MUSIC_ENABLE = "glyph_settings_progress_music_toggle";

    public static final String[] APPS_TO_IGNORE = {
        "android",
        "com.android.traceur",
        //"com.google.android.dialer",
        "com.google.android.setupwizard",
        "dev.kdrag0n.dyntheme.privileged.sys"
    };
    public static final String[] NOTIFS_TO_IGNORE = {
        "com.google.android.dialer:phone_incoming_call",
        "com.google.android.dialer:phone_ongoing_call",
        "com.android.systemui:BAT"
    };

    public static String getDevice() {
        if (device == null)
            device = ResourceUtils.getString("glyph_settings_device");

        return device;
    }

    public static boolean isPowershareSupported() {
       return !ResourceUtils.getString("glyph_settings_paths_powershare_active_absolute").isEmpty();
    }

    public static boolean setBrightness(int b) {
        if (b > ResourceUtils.getInteger("glyph_settings_brightness_max"))
            return false;

        brightness = b;
        return true;
    }

    public static int getBrightness() {
        if (brightness == -1)
            brightness = ResourceUtils.getInteger("glyph_settings_brightness_max");

        return brightness;
    }

    public static int getMaxBrightness() {
        if (brightnessMax == -1)
            brightnessMax = ResourceUtils.getInteger("glyph_settings_brightness_max");

        return brightnessMax;
    }

    public static int[] getBrightnessLevels() {
        if (brightnessLevels == null)
            brightnessLevels = ResourceUtils.getIntArray("glyph_settings_brightness_levels");

        return brightnessLevels;
    }

    public static int[] getSupportedAnimationPatternLengths() {
        if (supportedAnimationPatternLengths == null)
            supportedAnimationPatternLengths = ResourceUtils.getIntArray("glyph_settings_animations_supported_pattern_lengths");

        return supportedAnimationPatternLengths;
    }

}
