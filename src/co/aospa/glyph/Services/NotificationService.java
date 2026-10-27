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

package co.aospa.glyph.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Data;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Utils.AnimationUtils;

public class NotificationService extends NotificationListenerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "GlyphNotification";
    private static final boolean DEBUG = true;

    private Context mContext;

    private NotificationManager mNotificationManager;

    private HandlerThread thread;
    private Handler mThreadHandler;

    private ContentResolver mContentResolver;
    private SettingObserver mSettingObserver;

    private SharedPreferences mSharedPreferences;

    private final Map<String, Runnable> pendingCallbacks = new HashMap<>();
    private final Map<String, Future<?>> pendingFutures = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ObservableSet<String> essentials = new ObservableSet<>();

    private int SYNC_DELAY = 345;

    public static class ObservableSet<T> extends LinkedHashSet<T> {
        public interface Listener {
            void onChanged(int size);
        }

        private Listener listener;

        public void setListener(Listener l) {
            listener = l;
        }

        private void notifyChanged() {
            if (listener != null) listener.onChanged(size());
        }

        @Override
        public boolean add(T t) {
            boolean r = super.add(t);
            if (r) notifyChanged();
            return r;
        }

        @Override
        public boolean remove(Object o) {
            boolean r = super.remove(o);
            if (r) notifyChanged();
            return r;
        }

        @Override
        public void clear() {
            boolean wasEmpty = isEmpty();
            super.clear();
            if (!wasEmpty) notifyChanged();
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            boolean changed = false;
            for (T t : c) changed |= super.add(t);
            if (changed) notifyChanged();
            return changed;
        }
    }


    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        // Add a handler thread
        thread = new HandlerThread("NotificationService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        mContext = this;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mContentResolver = getContentResolver();
        mSettingObserver = new SettingObserver();
        mSettingObserver.register(mContentResolver);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        essentials.setListener(size -> {
            if (size > 0) {
                AnimationManager.playEssential();
            } else {
                AnimationManager.stopEssential();
            }
        });
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        onNotificationUpdated();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        AnimationManager.stopEssential();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mSettingObserver.unregister(mContentResolver);
        essentials.setListener(null);
        thread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        if (Constants.CONTEXT == null) return;
        if (DEBUG) Log.d(TAG, "onNotificationPosted: " + sbn.getPackageName());
        
        if (SettingsManager.isGlyphProgressEnabled()) {
            checkProgressNotification(sbn);
        }
        
        if (!SettingsManager.isGlyphNotifsEnabled()) return;
        
        String packageName = sbn.getPackageName();
        String packageChannelID = sbn.getNotification().getChannelId();
        
        if (hasProgressBar(sbn.getNotification())) {
            if (DEBUG) Log.d(TAG, "Skipping progress notification for regular glyph: " + packageName);
            return;
        }
        
        int packageImportance = -1;
        boolean packageCanBypassDnd = false;
        int interruptionFilter = mNotificationManager.getCurrentInterruptionFilter();
        
        try {
            Context packageContext = createPackageContext(packageName, 0);
            NotificationManager packageNotificationManager =
                    (NotificationManager) packageContext.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel packageChannel =
                    packageNotificationManager.getNotificationChannel(packageChannelID);
            if (packageChannel != null) {
                packageImportance = packageChannel.getImportance();
                packageCanBypassDnd = packageChannel.canBypassDnd();
            }
        } catch (PackageManager.NameNotFoundException ignored) {}
        
        if (DEBUG) Log.d(TAG, "onNotificationPosted: package:" + packageName
                + " | channel id: " + packageChannelID
                + " | importance: " + packageImportance
                + " | can bypass dnd: " + packageCanBypassDnd);
        
        if (SettingsManager.isGlyphNotifsEnabled(packageName)
                        && !sbn.isOngoing() 
                        && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                        && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                        && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                        && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)) {
            String notifKey = sbn.getKey();
            Runnable runnable = () -> {
                if (SettingsManager.appHasGlyphNotifsConfig(packageName)) {
                    if (SettingsManager.isGlyphNotifsAnimationReversed(packageName)) {
                        AnimationManager.playCsvReverse(
                                mContext,
                                SettingsManager.getGlyphNotifsAnimation(packageName))
                        ;
                    } else {
                        AnimationManager.playCsv(
                                mContext,
                                SettingsManager.getGlyphNotifsAnimation(packageName)
                        );
                    }
                } else {
                    if (SettingsManager.isGlyphNotifsSyncEnabled()
                            && AnimationUtils.Holder.Notification.isAvailable()) {
                        AnimationManager.playExternalCsv(
                                AnimationUtils.Holder.Notification.getCsv(),
                                "notification"
                        );
                    } else {
                        if (SettingsManager.isGlyphNotifsAnimationReversed()) {
                            AnimationManager.playCsvReverse(
                                    mContext,
                                    SettingsManager.getGlyphNotifsAnimation()
                            );
                        } else {
                            AnimationManager.playCsv(
                                    mContext,
                                    SettingsManager.getGlyphNotifsAnimation()
                            );
                        }
                    }
                }
            };
            pendingCallbacks.put(notifKey, runnable);
            mThreadHandler.postDelayed(() -> {
                Future<?> future = executor.submit(runnable);
                pendingFutures.put(notifKey, future);
            }, SYNC_DELAY);
        }
        
        if (SettingsManager.isGlyphNotifsEnabled(packageName)
                && !Data.EssentialRule.getMatchingRules(new Data.EssentialRule.RuleContext(
                        packageName, getAllText(sbn))).isEmpty()
                        && !sbn.isOngoing()
                        && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                        && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                        && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                        && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)
                        && mNotificationManager.isNotificationPolicyAccessGranted()) {
           essentials.add(sbn.getKey());
        }
    }

    @Override
    public void onListenerConnected() {
        UserHandle user = Process.myUserHandle();
        for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            String pkg = app.packageName;
            try {
                Data.ApplicationData.Notification.setData(
                        getApplicationContext(),
                        pkg,
                        getNotificationChannels(pkg, user),
                        getNotificationChannelGroups(pkg, user));
            } catch (SecurityException ignored) { }
        }
    }

    @Override
    public void onNotificationChannelModified(String pkg, UserHandle user,
                                              NotificationChannel channel, int modificationType) {
        if (!Process.myUserHandle().equals(user)) return;
        switch (modificationType) {
            case NOTIFICATION_CHANNEL_OR_GROUP_ADDED -> {
                Data.ApplicationData.Notification.addChannel(getApplicationContext(), pkg, channel);
            }
            case NOTIFICATION_CHANNEL_OR_GROUP_DELETED -> {
                Data.ApplicationData.Notification.removeChannel(getApplicationContext(), pkg, channel);
            }
        }
    }

    @Override
    public void onNotificationChannelGroupModified(String pkg, UserHandle user,
                                                   NotificationChannelGroup group, int modificationType) {
        if (!Process.myUserHandle().equals(user)) return;
        switch (modificationType) {
            case NOTIFICATION_CHANNEL_OR_GROUP_ADDED -> {
                Data.ApplicationData.Notification.addChannelGroup(getApplicationContext(), pkg, group);
            }
            case NOTIFICATION_CHANNEL_OR_GROUP_DELETED -> {
                Data.ApplicationData.Notification.removeChannelGroup(getApplicationContext(), pkg, group);
            }
        }
    }


    private boolean hasProgressBar(Notification notification) {
        if (notification == null || notification.extras == null) return false;
        
        int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
        int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
        
        return progress >= 0 && maxProgress > 0;
    }

    private void checkProgressNotification(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) return;

            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
            boolean indeterminate = notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, true);

            if (DEBUG) {
                Log.d(TAG, "Checking progress: pkg=" + sbn.getPackageName() + 
                        ", progress=" + progress + "/" + maxProgress + 
                        ", indeterminate=" + indeterminate + 
                        ", ongoing=" + sbn.isOngoing());
            }

            if (progress >= 0 && maxProgress > 0 && !indeterminate) {
                Intent intent = new Intent(ProgressService.ACTION_PROGRESS_NOTIFICATION);
                intent.putExtra(ProgressService.EXTRA_PACKAGE_NAME, sbn.getPackageName());
                intent.putExtra(ProgressService.EXTRA_NOTIFICATION_ID, sbn.getId());
                intent.putExtra(ProgressService.EXTRA_PROGRESS, progress);
                intent.putExtra(ProgressService.EXTRA_MAX, maxProgress);
                sendBroadcast(intent);

                if (DEBUG) {
                    Log.d(TAG, ">>> BROADCAST SENT: Progress notification detected: " + 
                            sbn.getPackageName() + " - " + progress + "/" + maxProgress);
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Not broadcasting: invalid progress or indeterminate");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking progress notification", e);
        }
    }

    private void checkProgressNotificationRemoved(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            if (notification == null || notification.extras == null) return;

            int progress = notification.extras.getInt(Notification.EXTRA_PROGRESS, -1);
            int maxProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);

            if (progress >= 0 && maxProgress > 0) {
                Intent intent = new Intent(ProgressService.ACTION_PROGRESS_REMOVED);
                intent.putExtra(ProgressService.EXTRA_PACKAGE_NAME, sbn.getPackageName());
                intent.putExtra(ProgressService.EXTRA_NOTIFICATION_ID, sbn.getId());
                sendBroadcast(intent);

                if (DEBUG) {
                    Log.d(TAG, ">>> BROADCAST SENT: Progress notification removed: " + sbn.getPackageName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking progress notification removal", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        if (DEBUG) Log.d(TAG, "onNotificationRemoved: package:" + sbn.getPackageName()
                + " | channel id: " + sbn.getNotification().getChannelId());
        
        if (SettingsManager.isGlyphProgressEnabled()) {
            checkProgressNotificationRemoved(sbn);
        }
        String key = sbn.getKey();
        Runnable runnable = pendingCallbacks.remove(key);
        if (runnable != null) {
            mThreadHandler.removeCallbacks(runnable);
        }

        Future<?> future = pendingFutures.remove(key);
        if (future != null) future.cancel(true);

        essentials.remove(sbn.getKey());
        
        onNotificationUpdated();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preference, String key) {
        if (key.equals("glyph_settings_notifs_sub_essential")) {
            if (DEBUG) Log.d(TAG, "onSharedPreferenceChanged: glyph_settings_notifs_sub_essential");
            onNotificationUpdated();
        }
    }

    private void onNotificationUpdated() {
        if (DEBUG) Log.d(TAG, "onNotificationUpdated");
        boolean playEssential = false;
        if (SettingsManager.isGlyphNotifsEnabled()) {
            if (!mNotificationManager.isNotificationPolicyAccessGranted()) return;
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            for (StatusBarNotification sbn : activeNotifications) {
                String packageName = sbn.getPackageName();
                String packageChannelID = sbn.getNotification().getChannelId();
                int packageImportance = -1;
                boolean packageCanBypassDnd = false;
                int interruptionFilter = mNotificationManager.getCurrentInterruptionFilter();
                try {
                    Context packageContext = createPackageContext(packageName, 0);
                    NotificationManager packageNotificationManager = (NotificationManager) packageContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationChannel packageChannel = packageNotificationManager.getNotificationChannel(packageChannelID);
                    if (packageChannel != null) {
                        packageImportance = packageChannel.getImportance();
                        packageCanBypassDnd = packageChannel.canBypassDnd();
                    }
                } catch (PackageManager.NameNotFoundException e) {}
                if (DEBUG) Log.d(TAG, "onNotificationUpdated: package:" + packageName
                        + " | channel id: " + packageChannelID
                        + " | importance: " + packageImportance
                        + " | can bypass dnd: " + packageCanBypassDnd);
                if (!Data.EssentialRule.getMatchingRules(new Data.EssentialRule.RuleContext(
                        packageName, getAllText(sbn))).isEmpty()
                                && !sbn.isOngoing()
                                && !ArrayUtils.contains(Constants.APPS_TO_IGNORE, packageName)
                                && !ArrayUtils.contains(Constants.NOTIFS_TO_IGNORE, packageName + ":" + packageChannelID)
                                && (packageImportance >= NotificationManager.IMPORTANCE_DEFAULT || packageImportance == -1)
                                && (interruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL || packageCanBypassDnd)) {
                    if (DEBUG) Log.d(TAG, "onNotificationUpdated: found essential notification | package:" + packageName);
                    essentials.add(sbn.getKey());
                }
            }
        }
    }

    private String getAllText(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        StringBuilder sb = new StringBuilder();

        appendIfPresent(sb, extras.getCharSequence(Notification.EXTRA_TITLE));
        appendIfPresent(sb, extras.getCharSequence(Notification.EXTRA_TEXT));
        appendIfPresent(sb, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        appendIfPresent(sb, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                appendIfPresent(sb, line);
            }
        }

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable p : messages) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                CharSequence sender = b.getCharSequence("sender");
                CharSequence text = b.getCharSequence("text");
                if (text == null || text.isEmpty()) continue;

                if (sender != null && !sender.isEmpty()) {
                    appendIfPresent(sb, sender + ": " + text);
                } else {
                    appendIfPresent(sb, text);
                }
            }
        }

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, CharSequence cs) {
        if (cs == null || cs.length() == 0) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(cs);
    }

    private class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register(ContentResolver cr) {
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_ENABLE), false, this);
            cr.registerContentObserver(Settings.Secure.getUriFor(
                Constants.GLYPH_NOTIFS_ENABLE), false, this);
        }

        public void unregister(ContentResolver cr) {
            cr.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.d(TAG, "SettingObserver: onChange");
            onNotificationUpdated();
            super.onChange(selfChange);
        }
    }
}
