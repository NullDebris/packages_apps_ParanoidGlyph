package co.aospa.glyph;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import co.aospa.glyph.Manager.SettingsManager;

public class Data {

    private static final String callPermission = Manifest.permission.MANAGE_OWN_CALLS;
    private static final String micFgPermission = Manifest.permission.FOREGROUND_SERVICE_MICROPHONE;
    private static final String audioRecordPermission = Manifest.permission.RECORD_AUDIO;
    private static final String mediaPermission = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK;
    private static final String notificationPermission = Manifest.permission.POST_NOTIFICATIONS;

    private static PackageChangeReceiver receiver;
    private static HandlerThread handlerThread;
    private static Handler backgroundHandler;

    private static final String TAG = "GlyphData";

    public record ApplicationData (
            String label,
            Drawable icon,
            List<NotificationChannel> channels,
            List<NotificationChannelGroup> channelGroups,
            boolean canManageOwnCalls,
            boolean canUseMic,
            boolean canBindMedia,
            boolean canNotify) {

        private static final Map<String, ApplicationData> appDataCache = new HashMap<>();

        static final AtomicBoolean updatePending = new AtomicBoolean(false);

        public static synchronized void init(Context ctx) {

            if (receiver != null) return;

            handlerThread = new HandlerThread("PackageCache");
            handlerThread.start();
            backgroundHandler = new Handler(handlerThread.getLooper());

            receiver = new PackageChangeReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addDataScheme("package");

            ctx.getApplicationContext().registerReceiver(receiver, filter, null, backgroundHandler);

            backgroundHandler.post(() -> {
                updatePending.set(true);

                PackageManager pm = ctx.getPackageManager();
                List<PackageInfo> allApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                for (PackageInfo pkg : allApps) {
                    String pkgName = pkg.packageName;
                    Drawable icon = null;
                    boolean canManageOwnCalls = false;
                    boolean canUseMic = false;
                    boolean canBindMedia = false;
                    boolean canNotify = false;
                    String label = pkg.applicationInfo.loadLabel(pm).toString();


                    int pkgFlags = pkg.applicationInfo.flags;
                    if (pkg.requestedPermissions == null) continue;
                    if (pm.getLaunchIntentForPackage(pkg.packageName) == null) continue;
                    if ((pkgFlags & ApplicationInfo.FLAG_INSTALLED) == 0
                            || (pkgFlags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                    for (String perm : pkg.requestedPermissions) {
                        switch (perm) {
                            case callPermission -> canManageOwnCalls = true;
                            case micFgPermission, audioRecordPermission -> canUseMic = true;
                            case mediaPermission -> canBindMedia = true;
                            case notificationPermission -> {
                                if (pkg.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
                                    canNotify = true;
                                }
                            }
                        }
                    }

                    try {
                        icon = pm.getApplicationIcon(pkgName);
                    } catch (PackageManager.NameNotFoundException e) {
                        icon = pm.getDefaultActivityIcon();
                    }

                    if (pkg.applicationInfo.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
                        canNotify = true;
                    }

                    appDataCache.put(pkgName, new ApplicationData(
                            label,
                            icon,
                            Collections.emptyList(),
                            Collections.emptyList(),
                            canManageOwnCalls,
                            canUseMic,
                            canBindMedia,
                            canNotify)
                    );
                }

                updatePending.compareAndSet(true, false);
            });
        }


        public static Map<String, ApplicationData> get(Context ctx) {
            if (appDataCache.isEmpty()) init(ctx);
            return sortByLabel(appDataCache);
        }

        public static Map<String, ApplicationData> getMediaApps(Context ctx) {
            if (appDataCache.isEmpty()) init(ctx);
            return sortByLabel(appDataCache.entrySet().stream()
                    .filter(e -> e.getValue().canBindMedia())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        public static Map<String, ApplicationData> getMicApps(Context ctx) {
            if (appDataCache.isEmpty()) init(ctx);
            return sortByLabel(appDataCache.entrySet().stream()
                    .filter(e -> e.getValue().canUseMic())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        public static Map<String, ApplicationData> getCallApps(Context ctx) {
            if (appDataCache.isEmpty()) init(ctx);
            return sortByLabel(appDataCache.entrySet().stream()
                    .filter(e -> e.getValue().canManageOwnCalls())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        public static Map<String, ApplicationData> getNotificationApps(Context ctx) {
            if (appDataCache.isEmpty()) init(ctx);
            return sortByLabel(appDataCache.entrySet().stream()
                    .filter(e -> e.getValue().canNotify())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        public static ApplicationData getApplication(Context ctx, String pkg) {
            if (appDataCache.isEmpty()) init(ctx);
            return appDataCache.get(pkg);
        }

        public static void remove(String pkg) {
            appDataCache.remove(pkg);
        }

        private static Map<String, ApplicationData> sortByLabel(Map<String, ApplicationData> apps) {
            return apps.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(
                            Comparator.comparing(ApplicationData::label, String.CASE_INSENSITIVE_ORDER)))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new));
        }

        public static void fillPackageInfo(Context ctx, String pkg) throws PackageManager.NameNotFoundException {
            updatePending.set(true);

            PackageManager pm = ctx.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            ApplicationInfo info = pkgInfo.applicationInfo;

            String pkgName = info.packageName;
            Drawable icon = null;
            boolean canManageOwnCalls = false;
            boolean canUseMic = false;
            boolean canBindMedia = false;
            boolean canNotify = false;
            String label = info.loadLabel(pm).toString();

            int pkgFlags = info.flags;
            if (pkgInfo.requestedPermissions == null) return;
            if (pm.getLaunchIntentForPackage(pkgName) == null) return;
            if ((pkgFlags & ApplicationInfo.FLAG_INSTALLED) == 0
                    || (pkgFlags & ApplicationInfo.FLAG_PERSISTENT) != 0) return;
            for (String perm : pkgInfo.requestedPermissions) {
                switch (perm) {
                    case callPermission -> canManageOwnCalls = true;
                    case micFgPermission, audioRecordPermission -> canUseMic = true;
                    case mediaPermission -> canBindMedia = true;
                    case notificationPermission -> {
                        if (info.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
                            canNotify = true;
                        }
                    }
                }
            }

            try {
                icon = pm.getApplicationIcon(pkgName);
            } catch (PackageManager.NameNotFoundException e) {
                icon = pm.getDefaultActivityIcon();
            }

            if (info.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
                canNotify = true;
            }

            appDataCache.put(pkgName, new ApplicationData(
                    label,
                    icon,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    canManageOwnCalls,
                    canUseMic,
                    canBindMedia,
                    canNotify)
            );

            updatePending.compareAndSet(true, false);
        }

        public static class Notification {

            private static void tryToFillPkg(Context ctx, String pkg, Consumer<ApplicationData> action) {
                updatePending.set(true);
                try {
                    if (appDataCache.isEmpty()) init(ctx);
                    if (!appDataCache.containsKey(pkg)) {
                        try {
                            fillPackageInfo(ctx, pkg);
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.w(TAG, "Data found but application cannot be found: " + pkg);
                            return;
                        }
                    }
                    ApplicationData data = appDataCache.get(pkg);
                    if (data == null) {
                        Log.w(TAG, "Data found but application is excluded from cache: " + pkg);
                        return;
                    }
                    action.accept(data);
                } finally {
                    updatePending.set(false);
                }
            }

            public static void setData(Context ctx, String pkg,
                                       List<NotificationChannel> channels,
                                       List<NotificationChannelGroup> channelGroups) {
                tryToFillPkg(ctx, pkg, d -> {
                    d.channels.clear();
                    d.channels.addAll(channels);
                    d.channelGroups.clear();
                    d.channelGroups.addAll(channelGroups);
                });
            }

            public static void addChannel(Context ctx, String pkg, NotificationChannel channel) {
                tryToFillPkg(ctx, pkg, d -> d.channels.add(channel));
            }

            public static void addChannelGroup(Context ctx, String pkg, NotificationChannelGroup group) {
                tryToFillPkg(ctx, pkg, d -> d.channelGroups.add(group));
            }

            public static void removeChannel(Context ctx, String pkg, NotificationChannel channel) {
                tryToFillPkg(ctx, pkg, d -> d.channels.remove(channel));
            }

            public static void removeChannelGroup(Context ctx, String pkg, NotificationChannelGroup group) {
                tryToFillPkg(ctx, pkg, d -> d.channelGroups.remove(group));
            }

        }

    }

    private static class PackageChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            if (data == null) return;
            String pkg = data.getSchemeSpecificPart();

            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_REPLACED:
                    try {
                        ApplicationData.fillPackageInfo(context, pkg);
                    } catch (PackageManager.NameNotFoundException ignored) {}
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        ApplicationData.remove(pkg);
                    }
                    break;
            }
        }
    }

    public record EssentialRule(
            String name,
            String ruleId,
            HashSet<String> packages,
            HashSet<String> keywords) {

        public record RuleContext(String pkg, String text) {}

        private static List<EssentialRule> rules = new CopyOnWriteArrayList<>();
        private static final ExecutorService IO = Executors.newSingleThreadExecutor();

        public static List<EssentialRule> getRules() {
            if (rules.isEmpty()) reloadRules();
            return rules;
        }

        public static void reloadRules() {
            IO.execute(() -> rules = List.copyOf(SettingsManager.Essential.getRules()));
        }

        private boolean isBlank() {
            return packages.isEmpty() && keywords.isEmpty();
        }

        private enum MatchType {
            // CONTACT,
            PACKAGE,
            KEYWORD
        }

        public static List<EssentialRule> getMatchingRules(RuleContext rc) {
            List<EssentialRule> matchingRules = new ArrayList<>();
            for (EssentialRule rule : getRules()) {
                if (rule.evaluateMatch(rc)) matchingRules.add(rule);
            }

            return matchingRules;
        }

        private static <T> boolean satisfied(Set<T> allowed, T value) {
            return allowed.isEmpty() || (value != null && allowed.contains(value));
        }

        public boolean evaluateMatch(RuleContext ctx) {
            return !isBlank()
                    && satisfied(packages, ctx.pkg())
                    && (keywords.isEmpty() || (ctx.text() != null && matchesAnyKeyword(ctx.text())));
        }

//        public boolean matchesContact(String contactId) {
//            return contacts.contains(contactId);
//        }

        public HashSet<String> matchesKeywords(String text) {
            HashSet<String> matches = new HashSet<>();
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    matches.add(keyword);
                }
            }
            return matches;
        }

        public boolean matchesAnyKeyword(String text) {
            return keywords.stream().anyMatch(text::contains);
        }

        public boolean matchesPackage(String pkg) {
            return packages.contains(pkg);
        }

        private Set<MatchType> getRequiredMatches() {
            EnumSet<MatchType> required = EnumSet.noneOf(MatchType.class);
            // if (!contacts.isEmpty()) required.add(MatchType.CONTACT);
            if (!packages.isEmpty()) required.add(MatchType.PACKAGE);
            if (!keywords.isEmpty()) required.add(MatchType.KEYWORD);
            return required;
        }

    }

}
