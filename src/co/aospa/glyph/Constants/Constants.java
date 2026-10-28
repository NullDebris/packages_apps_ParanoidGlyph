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
    public static final String GLYPH_FLIP_SUB_ENABLE = "glyph_settings_flip_sub_toggle";
    public static final String GLYPH_FLIP_SUB_PREVIEW = "glyph_settings_flip_sub_preview";
    public static final String GLYPH_FLIP_SUB_ANIMATIONS = "glyph_settings_flip_sub_animations";
    public static final String GLYPH_FLIP_SUB_ANIMATION_ENABLE = "glyph_settings_flip_sub_animation_toggle";
    public static final String GLYPH_FLIP_SUB_LIVE_PREVIEW = "glyph_settings_flip_sub_live_preview";

    public static final String GLYPH_FLIP_SUB_RINGER_MODE = "glyph_settings_flip_sub_ringer_mode";
    public static final String GLYPH_FLIP_REVERSE_ANIMATION_ENABLE = "glyph_settings_flip_sub_animations_reverse_toggle";

    public static final String GLYPH_BRIGHTNESS = "glyph_settings_brightness";
    public static final String GLYPH_BATTERY_SAVER_ENABLE = "glyph_settings_battery_saver_toggle";
    public static final String GLYPH_CHARGING_CATEGORY = "glyph_settings_charging";
    public static final String GLYPH_CHARGING_LEVEL_ENABLE = "glyph_settings_charging_level";
    public static final String GLYPH_CHARGING_POWERSHARE_ENABLE = "glyph_settings_charging_powershare";
    public static final String GLYPH_CALL_CATEGORY = "glyph_settings_call";
    // public static final String GLYPH_CALL_TONE_SYNC = "glyph_settings_call_ogg_sync_toggle";
    public static final String GLYPH_CALL_ENABLE = "glyph_settings_call_toggle";
    public static final String GLYPH_CALL_REVERSE_ANIMATION_ENABLE = "glyph_settings_call_sub_animations_reverse_toggle";
    public static final String GLYPH_CALL_SUB_PREVIEW = "glyph_settings_call_sub_preview";
    public static final String GLYPH_CALL_SUB_ANIMATIONS = "glyph_settings_call_sub_animations";
    public static final String GLYPH_CALL_SUB_CONTACT_SELECT = "glyph_settings_call_sub_contact_select";
    public static final String GLYPH_CALL_SUB_LIVE_PREVIEW = "glyph_settings_call_sub_animations_live_preview";
    public static final String GLYPH_CALL_SUB_ENABLE = "glyph_settings_call_sub_toggle";
    public static final String GLYPH_CALL_SUB_CATEGORY = "glyph_settings_call_sub";
    public static final String GLYPH_MUSIC_VISUALIZER_ENABLE = "glyph_settings_music_visualizer_toggle";
    public static final String GLYPH_MUSIC_VISUALIZER_MODE = "glyph_settings_music_visualizer_mode";
    public static final String GLYPH_NOTIFS_ENABLE = "glyph_settings_notifs_toggle";
    public static final String GLYPH_NOTIFS_TONE_SYNC = "glyph_settings_notifs_ogg_sync_toggle";
    public static final String GLYPH_NOTIFS_SUB_PREVIEW = "glyph_settings_notifs_sub_preview";
    public static final String GLYPH_NOTIFS_SUB_ANIMATIONS = "glyph_settings_notifs_sub_animations";
    public static final String GLYPH_NOTIFS_SUB_LIVE_PREVIEW = "glyph_settings_notifs_sub_animations_live_preview";
    public static final String GLYPH_NOTIFS_SUB_CATEGORY = "glyph_settings_notifs_sub";
    public static final String GLYPH_NOTIFS_SUB_ENABLE = "glyph_settings_notifs_sub_toggle";
    public static final String GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE = "glyph_settings_notifs_sub_animations_reverse_toggle";
    public static final String GLYPH_VOLUME_CATEGORY = "glyph_settings_volume";
    public static final String GLYPH_VOLUME_LEVEL_ENABLE = "glyph_settings_volume_level_toggle";
    public static final String GLYPH_AUTO_BRIGHTNESS_ENABLE = "glyph_settings_auto_brightness_toggle";
    public static final String GLYPH_SCHEDULE = "glyph_settings_schedule";
    public static final String GLYPH_PROGRESS_CATEGORY = "glyph_settings_progress";
    public static final String GLYPH_PROGRESS_ENABLE = "glyph_settings_progress_toggle";
    public static final String GLYPH_PROGRESS_MEDIA_ENABLE = "glyph_settings_progress_media_toggle";
    public static final String GLYPH_PROGRESS_MEDIA_WHITELIST = "glyph_settings_progress_media_app_whitelist";


    public static final String GLYPH_RED_LED_CATEGORY = "glyph_settings_red_led";
    public static final String GLYPH_RED_LED_MODE = "glyph_settings_red_led_mode";
    public static final String GLYPH_MIC_ACTIVITY_ENABLE = "glyph_settings_mic_activity_toggle";
    public static final String GLYPH_MIC_ACTIVITY_WHITELIST = "glyph_settings_mic_activity_whitelist";

    public static final String ACTION_TORCH_ENABLE = "torch_enable";
    public static final String ACTION_TORCH_DISABLE = "torch_disable";

    public static final String PULSE_LOCKSCREEN_ENABLED_SETTING = "lockscreen_pulse_enabled";
    public static final String PULSE_ENABLED_SETTING = "visualizer_pulse_enabled";

    public static final String GLYPH_USER_NOTIF_CSV_PATH = "Glyph/Notifications";
    public static final String GLYPH_USER_CALL_CSV_PATH = "Glyph/Call";

    public static final String GLYPH_USER_CALL_CSV_PREFIX = "user_call_";
    public static final String GLYPH_USER_NOTIF_CSV_PREFIX = "user_notif_";

    public static final String GLYPH_NOTIF_ANIMATION_ALTERNATE = "notif_alternate";

    public static final String GLYPH_CALL_CONTACT_PREF_PREFIX = "call_contact_";
    public static final String GLYPH_CALL_APP_PREF_PREFIX = "call_";
    public static final String GLYPH_NOTIF_APP_PREF_PREFIX = "notif_";

    public static final String GLYPH_OGG_LIVE_PREVIEW = "glyph_utilities_ogg_live_preview";

    public static final String GLYPH_UTILITIES = "glyph_settings_utilities";

    public static final String GLYPH_UTILITIES_VALIDATE_CSV = "glyph_utilities_validate_csv";
    public static final String GLYPH_UTILITIES_READ_OGG = "glyph_utilities_read_ogg";
    public static final String GLYPH_UTILITIES_OGG_EXPORT_CSV = "glyph_utilities_export_csv_from_ogg";

    public static final String GLYPH_NOTIFS_APP_GLOBAL_ANIMATION_ENABLE = "glyph_settings_notifications_global_animation";

    public static class Essential {
        public static final String GLYPH_ESSENTIAL_NOTIFICATIONS = "glyph_settings_essential_notifications";

        public static final String GLYPH_ESSENTIAL_RULE_PREFIX = "essential_";
        public static final String GLYPH_ESSENTIAL_RULE_NAME = "name";
        public static final String GLYPH_ESSENTIAL_RULE_PACKAGES = "packages";
        public static final String GLYPH_ESSENTIAL_RULE_CONTACTS = "contacts";
        public static final String GLYPH_ESSENTIAL_RULE_KEYWORDS = "keywords";
    }

    public static class Device {

        public static final String PHONE1 = "phone1";
        public static final String PHONE2 = "phone2";
        public static final String PHONE2A = "phone2a";
        public static final String PHONE3A = "phone3a";

        public static String getDevice() {
            if (device == null)
                device = ResourceUtils.getString("glyph_settings_device");

            return device;
        }

        public static boolean isPhone1() {
            return getDevice().equals(PHONE1);
        }

        public static boolean isPhone2() {
            return getDevice().equals(PHONE2);
        }

        public static boolean isPhone2a() {
            return getDevice().equals(PHONE2A);
        }

        public static boolean isPhone3a() {
            return getDevice().equals(PHONE3A);
        }
    }
    

    public static final String[] APPS_TO_IGNORE = {
        "android",
        "com.android.traceur",
        "com.google.android.setupwizard",
        "dev.kdrag0n.dyntheme.privileged.sys"
    };
    public static final String[] NOTIFS_TO_IGNORE = {
        "com.google.android.dialer:phone_incoming_call",
        "com.google.android.dialer:phone_ongoing_call",
        "com.android.systemui:BAT"
    };

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
            supportedAnimationPatternLengths = ResourceUtils.getIntArray(
                    "glyph_settings_animations_supported_pattern_lengths");

        return supportedAnimationPatternLengths;
    }

}
