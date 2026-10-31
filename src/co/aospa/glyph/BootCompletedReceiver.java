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

package co.aospa.glyph;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.GlyphScheduleManager;
import co.aospa.glyph.Settings.SettingsActivity;
import co.aospa.glyph.Utils.ServiceUtils;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = true;
    private static final String TAG = "ParanoidGlyph";

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (DEBUG) Log.d(TAG, "Received boot completed intent");
        Constants.CONTEXT = context.getApplicationContext();
        
        if (GlyphScheduleManager.isScheduleEnabled(context)) {
            GlyphScheduleManager.setupScheduleAlarms(context);
            if (DEBUG) Log.d(TAG, "Schedule alarms restored on boot");
        }
        
        ServiceUtils.checkGlyphService();

        if (action != null && action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Data.ApplicationData.init(context.getApplicationContext());
            Data.EssentialRule.reloadRules();
            setupShortcut(context.getApplicationContext());
        }
    }

    private void setupShortcut(Context ctx) {
        String axPkg = "com.android.axion.axionparts";

        if (Data.ApplicationData.isInstalled(ctx, axPkg)
                && Data.ApplicationData.isSystemApp(ctx, axPkg)) {
            switchMainShortcut(ctx, false);
        } else {
            switchMainShortcut(ctx, true);
        }
    }

    private static void switchMainShortcut(Context context, boolean mainState) {
        ComponentName mainComponent = new ComponentName(
                context,
                SettingsActivity.class.getName() + "MainEntry"
        );
        ComponentName systemComponent = new ComponentName(
                context,
                SettingsActivity.class.getName() + "SystemEntry"
        );
        context.getPackageManager().setComponentEnabledSetting(
                mainComponent,
                mainState
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        context.getPackageManager().setComponentEnabledSetting(
                systemComponent,
                !mainState
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }
}
