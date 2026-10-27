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

import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import com.android.internal.util.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Data;
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

    public static String buildAppNotifsPrefName(String pkg) {
        return Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg;
    }

    public static String buildAppChannelPrefName(String pkg, String channelId) {
        return buildAppNotifsPrefName(pkg) + "_" + channelId;
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
                Constants.GLYPH_ENABLE, 0) != 0
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
                Constants.GLYPH_ENABLE, 0) != 0
            || PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_ENABLE, false));
    }

    public static boolean isGlyphFlipEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_FLIP_ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static void setGlyphFlipEnabled(boolean enable) {
        Context ctx = getContext();
        Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_FLIP_ENABLE, enable ? 1 : 0);
    }

    public static boolean isGlyphFlipAnimationEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_FLIP_SUB_ANIMATION_ENABLE, true) && isGlyphEnabled();
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

    public static boolean isGlyphCallEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_CALL_ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static boolean isGlyphCallEnabled(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_CALL_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.GLYPH_CALL_ENABLE, true);
    }

    public static boolean setGlyphCallEnabled(boolean enable) {
        Context ctx = getContext();
        return Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_CALL_ENABLE, enable ? 1 : 0);
    }

    public static void setGlyphCallEnabled(String pkg, boolean enable) {
        Context ctx = getContext();
        ctx.getSharedPreferences(Constants.GLYPH_CALL_APP_PREF_PREFIX + pkg
                        , Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.GLYPH_CALL_ENABLE, enable)
                .apply();
    }

    public static boolean contactHasGlyphCallConfig(int contactId) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(Constants.GLYPH_CALL_CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getAll().isEmpty();
    }

    public static String getGlyphCallAnimation() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_CALL_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static String getGlyphCallAnimation(int contactId) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_CALL_CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getString(Constants.GLYPH_CALL_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

    public static boolean appHasGlyphCallConfig(String pkg) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.GLYPH_CALL_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphCallAnimationReversed(pkg)
                || !getGlyphCallAnimation(pkg).equals(getGlyphCallAnimation()));
    }

    public static boolean appHasGlyphCallConfig(String pkg, String comp) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.GLYPH_CALL_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphCallAnimationReversed(pkg)
                || !getGlyphCallAnimation(pkg).equals(comp));
    }


    public static String getGlyphCallAnimation(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_CALL_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getString(Constants.GLYPH_CALL_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_call_animations_default"));
    }

//    public static boolean isGlyphRingtoneSyncEnabled() {
//        Context ctx = getContext();
//        return PreferenceManager.getDefaultSharedPreferences(ctx)
//                .getBoolean(Constants.GLYPH_CALL_TONE_SYNC, false);
//    }

    public static boolean isGlyphNotifsSyncEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_NOTIFS_TONE_SYNC, false);
    }

    public static String getGlyphFlipAnimation() {
        Context ctx = getContext();
        String defaultValue = ResourceUtils.hasFlipCsv() ? "flip" : Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE;

        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_FLIP_SUB_ANIMATIONS,
                        defaultValue);
    }

    public static boolean isGlyphFlipAnimationReversed() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_FLIP_REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed() {
        Context ctx = getContext();
         return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed(int contactId) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_CALL_CONTACT_PREF_PREFIX
                        + contactId, Context.MODE_PRIVATE)
                .getBoolean(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphCallAnimationReversed(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_CALL_APP_PREF_PREFIX
                        + pkg, Context.MODE_PRIVATE)
                .getBoolean(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE,
                        false);
    }

    public static boolean isGlyphMusicVisualizerEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE, false) && isGlyphEnabled();
    }

    public static int getGlyphMusicVisualizerMode() {
        Context ctx = getContext();
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_MUSIC_VISUALIZER_MODE, "0"));
    }

    public static void setGlyphMusicVisualizer(boolean state) {
        Context ctx = getContext();
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putBoolean(Constants.GLYPH_MUSIC_VISUALIZER_ENABLE, state).apply();
    }

    public static boolean isGlyphVolumeLevelEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_VOLUME_LEVEL_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphNotifsEnabled() {
        Context ctx = getContext();
        return Settings.Secure.getInt(ctx.getContentResolver(),
                Constants.GLYPH_NOTIFS_ENABLE, 0) != 0 && isGlyphEnabled();
    }

    public static boolean appHasGlyphNotifsConfig(String pkg) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphNotifsAnimationReversed(pkg)
                || !getGlyphNotifsAnimation(pkg).equals(getGlyphNotifsAnimation()));
    }

    public static boolean appHasGlyphNotifsConfig(String pkg, String comp) {
        Context ctx = getContext();
        return !ctx.getSharedPreferences(
                        Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getAll()
                .isEmpty()
                && (isGlyphNotifsAnimationReversed(pkg)
                || !getGlyphNotifsAnimation(pkg).equals(comp));
    }

    public static boolean isGlyphNotifsEnabled(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.GLYPH_NOTIFS_SUB_ENABLE, true);
    }

    public static void setGlyphNotifsEnabled(boolean enable) {
        Context ctx = getContext();
        Settings.Secure.putInt(ctx.getContentResolver(),
                Constants.GLYPH_NOTIFS_ENABLE, enable ? 1 : 0);
    }

    public static void setGlyphNotifsEnabled(String pkg, boolean enable) {
        Context ctx = getContext();
        ctx.getSharedPreferences(Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg
                        , Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.GLYPH_NOTIFS_SUB_ENABLE, enable)
                .apply();
    }

    public static String getGlyphNotifsAnimation() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static String getGlyphNotifsAnimation(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getString(Constants.GLYPH_NOTIFS_SUB_ANIMATIONS,
                        ResourceUtils.getString("glyph_settings_notifs_animations_default"));
    }

    public static boolean isGlyphNotifsAnimationReversed(String pkg) {
        Context ctx = getContext();
        return ctx.getSharedPreferences(Constants.GLYPH_NOTIF_APP_PREF_PREFIX + pkg,
                        Context.MODE_PRIVATE)
                .getBoolean(Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE, false);
    }

    public static boolean isGlyphNotifsAnimationReversed() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE, false);
    }

    public static boolean isGlyphAutoBrightnessEnabled() {
        Context ctx = getContext();
        return !ResourceUtils.getString("glyph_light_sensor").isBlank() 
            && PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(Constants.GLYPH_AUTO_BRIGHTNESS_ENABLE, false) 
            && isGlyphEnabled();
    }

    public static int getFlipRingerMode() {
        Context ctx = getContext();
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_FLIP_SUB_RINGER_MODE,
                        String.valueOf(AudioManager.RINGER_MODE_VIBRATE)));
    }

    public static boolean isGlyphProgressEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_PROGRESS_ENABLE, false) && isGlyphEnabled();
    }

    public static boolean isGlyphProgressMediaEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_PROGRESS_MEDIA_ENABLE, false) && isGlyphProgressEnabled();
    }

    public static boolean isMediaPackageWhitelisted(String name) {
        Context ctx = getContext();
        Set<String> pkgList = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.GLYPH_PROGRESS_MEDIA_WHITELIST, new HashSet<>());
        return pkgList.contains(name);
    }

    public static boolean isGlyphMicActivityEnabled() {
        Context ctx = getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(Constants.GLYPH_MIC_ACTIVITY_ENABLE, false) && isGlyphEnabled();
    }

    public static int getGlyphRedLedMode() {
        Context ctx = getContext();
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(Constants.GLYPH_RED_LED_MODE, "0"));
    }

    public static Set<String> getMonitoredMicApps() {
        Context ctx = getContext();
        Set<String> pkgList = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(Constants.GLYPH_MIC_ACTIVITY_WHITELIST, new HashSet<>());
        return pkgList;
    }

    public static void setIntSecure(String key, boolean state) {
        Context ctx = getContext();
        int currentUser = ActivityManager.getCurrentUser();
        Settings.Secure.putIntForUser(ctx.getContentResolver(),
                key, state ? 1 : 0, currentUser);
    }

    public static class Essential {

        public static boolean deleteRule(String ruleId) {
            Context ctx = getContext();
            return ctx.deleteSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId);
        }

        public static Data.EssentialRule getRule(String ruleId) {
            Context ctx = getContext();
            Map<String, ?> rulePref = ctx.getSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId,
                    Context.MODE_PRIVATE).getAll();

            if (!rulePref.isEmpty()) {
                String ruleName = null;
                HashSet<String> packages = new HashSet<>();
                HashSet<String> keywords = new HashSet<>();
                for (String key : rulePref.keySet()) {
                    Object value = rulePref.get(key);
                    switch (key) {
                        case Constants.Essential.GLYPH_ESSENTIAL_RULE_NAME -> ruleName = (String) value;
                        case Constants.Essential.GLYPH_ESSENTIAL_RULE_KEYWORDS -> keywords.addAll((Set) value);
                        case Constants.Essential.GLYPH_ESSENTIAL_RULE_PACKAGES -> packages.addAll((Set) value);
                    }
                }
                return new Data.EssentialRule(ruleName, ruleId, packages, keywords);
            }
            return null;
        }

        public static List<Data.EssentialRule> getRules() {
            Context ctx = getContext();
            List<String> names = findSharedPreferenceNames(ctx, Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX);
            if (names.isEmpty()) return Collections.emptyList();

            String prefix = Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX;
            List<Data.EssentialRule> rules = new ArrayList<>();

            for (String n : names) {
                String ruleId = n.substring(prefix.length());
                Data.EssentialRule rule = getRule(ruleId);
                if (rule != null) rules.add(rule);
            }
            return rules;
        }

        public static void setRuleName(String ruleId, String name) {
            Context ctx = getContext();
            ctx.getSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId
                            , Context.MODE_PRIVATE)
                    .edit()
                    .putString(Constants.Essential.GLYPH_ESSENTIAL_RULE_NAME, name)
                    .apply();
        }

        public static void setKeywords(String ruleId, Set<String> keywords) {
            Context ctx = getContext();
            ctx.getSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId
                                , Context.MODE_PRIVATE)
                        .edit()
                        .putStringSet(Constants.Essential.GLYPH_ESSENTIAL_RULE_KEYWORDS, keywords)
                        .apply();
        }

        public static void setPackages(String ruleId, Set<String> packages) {
            Context ctx = getContext();
            ctx.getSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId
                            , Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(Constants.Essential.GLYPH_ESSENTIAL_RULE_PACKAGES, packages)
                    .apply();
        }

        public static void setContacts(String ruleId, Set<String> contacts) {
            Context ctx = getContext();
            ctx.getSharedPreferences(Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId
                            , Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(Constants.Essential.GLYPH_ESSENTIAL_RULE_CONTACTS, contacts)
                    .apply();
        }

    }

    private static List<String> findSharedPreferenceNames(Context ctx, String prefix) {
        File dir = new File(ctx.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".xml") && n.startsWith(prefix));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().substring(0, f.getName().length() - 4));
            }
        }
        Collections.sort(names);
        return names;
    }


    public static class Pulse {

        public static boolean isLockscreenPulseEnabled() {
            Context ctx = getContext();
            int currentUser = ActivityManager.getCurrentUser();
            return Settings.Secure.getIntForUser(ctx.getContentResolver(),
                    Constants.PULSE_LOCKSCREEN_ENABLED_SETTING, 0, currentUser) == 1;
        }

        public static boolean isPulseEnabled() {
            Context ctx = getContext();
            int currentUser = ActivityManager.getCurrentUser();
            return Settings.Secure.getIntForUser(ctx.getContentResolver(),
                    Constants.PULSE_ENABLED_SETTING, 0, currentUser) == 1;
        }

        public static boolean isAnyPulseEnabled() {
            return isLockscreenPulseEnabled() || isPulseEnabled();
        }

    }
}
