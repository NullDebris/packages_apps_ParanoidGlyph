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

package co.aospa.glyph.Settings;

import static co.aospa.glyph.Utils.InterfaceUtils.showDialog;
import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import android.app.Activity;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import co.aospa.glyph.Data;
import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Preference.GlyphAnimationPreference;
import co.aospa.glyph.Utils.AnimationUtils;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class AnimationSettingsFragment
        extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private String fragmentType = null;
    private String targetPkg = null;
    private String notificationChannelId = null;

    PackageManager mPackageManager;

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;

    private SwitchPreferenceCompat mToneSyncSwitch;

    private List<String> userAnimationList = new ArrayList<>();
    private List<String> bundledAnimationList = new ArrayList<>();

    private ListPreference mListPreference;
    private Preference mLivePreviewPreference;
    private SwitchPreferenceCompat mReverseAnimationSwitch;
    private PreferenceCategory appListCategory;

    private SwitchPreferenceCompat mGlyphFlipAnimationSwitch;

    private GlyphAnimationPreference mGlyphAnimationPreference;

    private String fragmentTitle = null;

    private Thread livePreviewThread;

    private String animationPreviewKey;

    private String enableKey;

    private String animationListKey;

    private String livePreviewKey;

    private String defaultAnimation;

    private String userAnimationPrefix;
    private String reverseAnimationKey;

    private boolean shouldAlternate = false;

    private Consumer<Uri> mContactPickerAction;
    private boolean isContactSpecific = false;
    private String contactId;
    private String contactName;

    private boolean isAppSpecific = false;
    private boolean isChannelSpecific = false;

    public static final String ARG_FRAGMENT_TYPE = "type";
    public static final String ARG_CONTACT_ID = "contact_id";
    public static final String ARG_PACKAGE = "package";
    public static final String ARG_CHANNEL_ID = "channel_id";

    public static final String FRAGMENT_TYPE_NOTIF = "NOTIFS";
    public static final String FRAGMENT_TYPE_CALL = "CALL";
    public static final String FRAGMENT_TYPE_FLIP = "FLIP";


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Bundle args = getArguments();
        fragmentType = args.getString(ARG_FRAGMENT_TYPE, "").toUpperCase();
        contactId = args.getString(ARG_CONTACT_ID, "");
        targetPkg = args.getString(ARG_PACKAGE, "");
        notificationChannelId = args.getString(ARG_CHANNEL_ID, "");

        isContactSpecific = !contactId.isEmpty();
        isAppSpecific = !targetPkg.isEmpty();
        isChannelSpecific = (isAppSpecific && !notificationChannelId.isEmpty());

        if (isContactSpecific) {
            contactName = ResourceUtils.getContactName(requireContext(), contactId);
            if (contactName == null) {
                requireContext().getSharedPreferences(Constants.GLYPH_CALL_CONTACT_PREF_PREFIX
                                + contactId, Context.MODE_PRIVATE)
                        .edit().clear().apply();
                showToast("Contact does not exist!");
            }
        }

        if (fragmentType.isEmpty() || !fragmentType.equals(FRAGMENT_TYPE_NOTIF)
                && !fragmentType.equals(FRAGMENT_TYPE_CALL)
                && !fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment type is invalid!");
            return;
        }

        if (isContactSpecific && !fragmentType.equals(FRAGMENT_TYPE_CALL)
                || isChannelSpecific && !isAppSpecific) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment parameters are invalid!");
            return;
        }

        mPackageManager = getActivity().getPackageManager();

        evaluatePrefs();

    }

    private void evaluatePrefs() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (!isAppSpecific) {
                    addPreferencesFromResource(R.xml.glyph_notifs_settings);
                    mScreen = getPreferenceScreen();
                    fragmentTitle = requireContext().getString(R.string.glyph_settings_notifs_toggle_title);

                    appListCategory = findPreference(Constants.GLYPH_NOTIFS_SUB_CATEGORY);
                    inflateAppLists();

                    mToneSyncSwitch = findPreference(Constants.GLYPH_NOTIFS_TONE_SYNC);
                    mToneSyncSwitch.setOnPreferenceChangeListener(this);
                } else {
                    String pkgLabel =
                            Data.ApplicationData.getApplication(requireContext(), targetPkg).label();
                    String prefName;
                    if (isChannelSpecific) {
                        prefName = SettingsManager.buildAppChannelPrefName(targetPkg, notificationChannelId);
                            getPreferenceManager().setSharedPreferencesName(prefName);
                            String channelName = Data.ApplicationData.Notification.getChannelName(
                                    requireContext(),
                                    targetPkg,
                                    notificationChannelId
                            );
                            if (channelName.isEmpty()) {
                                getParentFragmentManager().popBackStack();
                                return;
                            }
                            fragmentTitle = channelName + " (" + pkgLabel + ")";
                    } else {
                        prefName = SettingsManager.buildAppNotifsPrefName(targetPkg);
                            getPreferenceManager().setSharedPreferencesName(prefName);
                            fragmentTitle =
                                    requireContext().getString(R.string.glyph_settings_notifs_toggle_title)
                                    + " (" + pkgLabel + ")";
                    }
                    addPreferencesFromResource(R.xml.glyph_notifs_settings_app);
                    mScreen = getPreferenceScreen();
                    addDeletePref(prefName, pkgLabel);
                }

                enableKey = Constants.GLYPH_NOTIFS_SUB_ENABLE;
                animationListKey = Constants.GLYPH_NOTIFS_SUB_ANIMATIONS;
                reverseAnimationKey = Constants.GLYPH_NOTIFS_REVERSE_ANIMATION_ENABLE;

                animationPreviewKey = Constants.GLYPH_NOTIFS_SUB_PREVIEW;
                livePreviewKey = Constants.GLYPH_NOTIFS_SUB_LIVE_PREVIEW;

                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;

                defaultAnimation = "glyph_settings_notifs_animations_default";
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    getPreferenceManager().setSharedPreferencesName(Constants.GLYPH_CALL_APP_PREF_PREFIX
                            + targetPkg);
                    addPreferencesFromResource(R.xml.glyph_call_settings_app);
                    mScreen = getPreferenceScreen();
                    String pkgLabel = Data.ApplicationData.getApplication(requireContext(), targetPkg).label();
                    fragmentTitle
                            = requireContext().getString(R.string.glyph_settings_call_toggle_title)
                            + " (" + pkgLabel + ")";

                    PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
                    Preference mDeletePreferences = new Preference(mScreen.getContext());
                    mDeletePreferences.setTitle(R.string.glyph_settings_delete_title);
                    mDeletePreferences.setOnPreferenceClickListener(pref -> {
                        showDialog(requireActivity(),
                                getString(R.string.glyph_settings_delete_title) + "?",
                                getString(R.string.glyph_settings_delete_confirm_message_start)
                                        + " " + pkgLabel + "?",
                                android.R.string.ok,
                                () -> {
                                    requireContext().deleteSharedPreferences(
                                            Constants.GLYPH_CALL_APP_PREF_PREFIX + targetPkg);
                                    getActivity().finish();
                                },
                                android.R.string.cancel, null);
                        return true;
                    });
                    mDeletePreferences.setIcon(R.drawable.ic_delete_forever);
                    mScreen.addPreference(mCategory);
                    mCategory.addPreference(mDeletePreferences);

                } else if (isContactSpecific) {
                    getPreferenceManager().setSharedPreferencesName(
                            Constants.GLYPH_CALL_CONTACT_PREF_PREFIX + contactId);
                    addPreferencesFromResource(R.xml.glyph_call_settings_generic);
                    mScreen = getPreferenceScreen();

                    fragmentTitle
                            = requireContext().getString(R.string.glyph_settings_call_toggle_title)
                                    + " (" + contactName + ")";

                    addDeletePref(Constants.GLYPH_CALL_CONTACT_PREF_PREFIX + contactId, contactName);
                } else {
                    addPreferencesFromResource(R.xml.glyph_call_settings);
                    fragmentTitle =
                            requireContext().getString(R.string.glyph_settings_call_toggle_title);

                    Preference mContactSelectPreference =
                            findPreference(Constants.GLYPH_CALL_SUB_CONTACT_SELECT);

                    mContactSelectPreference.setOnPreferenceClickListener(pref -> {
                           mContactPickerAction = uri -> {
                                if (uri != null) {
                                    Cursor cursor = requireContext().getContentResolver().query(
                                            uri,
                                            new String[]{ContactsContract.Contacts._ID},
                                            null, null, null);

                                    if (cursor != null && cursor.moveToFirst()) {
                                        String contact = cursor.getString(0);
                                        cursor.close();
                                        Intent intent = new Intent(requireContext(),
                                                AnimationSettingsActivity.class);
                                        intent.putExtra("type", fragmentType);
                                        intent.putExtra("contact_id", contact);
                                        startActivity(intent);
                                    } else {
                                        if (cursor != null) cursor.close();
                                    }
                                }
                           };
                           mContactPicker.launch(null);
                           return true;
                    });
                    appListCategory = findPreference(Constants.GLYPH_CALL_SUB_CATEGORY);
                    inflateAppLists();

                    // mToneSyncSwitch = findPreference(Constants.GLYPH_CALL_TONE_SYNC);
                    // mToneSyncSwitch.setOnPreferenceChangeListener(this);

                }

                animationPreviewKey = Constants.GLYPH_CALL_SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_CALL_CSV_PREFIX;
                enableKey = Constants.GLYPH_CALL_SUB_ENABLE;
                animationListKey = Constants.GLYPH_CALL_SUB_ANIMATIONS;
                defaultAnimation = "glyph_settings_call_animations_default";
                livePreviewKey = Constants.GLYPH_CALL_SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE;

            }
            case FRAGMENT_TYPE_FLIP -> {
                addPreferencesFromResource(R.xml.glyph_flip_settings);
                fragmentTitle =
                        requireContext().getString(R.string.glyph_settings_flip_toggle_title);
                animationPreviewKey = Constants.GLYPH_FLIP_SUB_PREVIEW;
                userAnimationPrefix = Constants.GLYPH_USER_NOTIF_CSV_PREFIX;
                animationListKey = Constants.GLYPH_FLIP_SUB_ANIMATIONS;
                enableKey = Constants.GLYPH_FLIP_SUB_ENABLE;
                livePreviewKey = Constants.GLYPH_FLIP_SUB_LIVE_PREVIEW;
                reverseAnimationKey = Constants.GLYPH_FLIP_REVERSE_ANIMATION_ENABLE;

                mGlyphFlipAnimationSwitch = findPreference(Constants.GLYPH_FLIP_SUB_ANIMATION_ENABLE);
                mGlyphFlipAnimationSwitch.setOnPreferenceChangeListener(this);

            }
        }

        if (!isContactSpecific) {
            mSwitchBar = findPreference(enableKey);
            mSwitchBar.addOnSwitchChangeListener(this);
            if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
                mSwitchBar.setChecked(SettingsManager.isGlyphFlipEnabled());
            } else {
                mSwitchBar.setChecked(isAnimationEnabled());
            }
        }

        mListPreference = findPreference(animationListKey);
        mListPreference.setOnPreferenceChangeListener(this);

        if (mToneSyncSwitch != null) {
            if (mToneSyncSwitch.isChecked()) {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title_sync_enabled);
            } else {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title);
            }
        }

        bundledAnimationList = getAnimations(0);
        userAnimationList = getAnimations(1);

        List<String[]> paired = new ArrayList<>();
        for (String name : bundledAnimationList) {
            paired.add(new String[]{name, name});
        }
        for (String name : userAnimationList) {
            paired.add(new String[]{name, userAnimationPrefix + name});
        }

        paired.sort(Comparator.comparing(p -> p[0]));

        List<String> animationEntryList
                = paired.stream().map(p -> p[0]).collect(Collectors.toList());
        List<String> animationEntryValues
                = paired.stream().map(p -> p[1]).collect(Collectors.toList());

        if (fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
            boolean hasFlipCsv = ResourceUtils.hasFlipCsv();
            animationEntryList.addFirst(
                    getString(R.string.glyph_settings_flip_animation_option_follow_notification)
            );
            if (hasFlipCsv) {
                animationEntryList.addFirst(getString(R.string.glyph_settings_default_option));
            }
            animationEntryValues.addFirst(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
            if (hasFlipCsv) animationEntryValues.addFirst("flip");
        }
        mListPreference.setEntries(animationEntryList.toArray(new String[0]));
        mListPreference.setEntryValues(animationEntryValues.toArray(new String[0]));
        if (!animationEntryValues.contains(mListPreference.getValue())) {
            if (!fragmentType.equals(FRAGMENT_TYPE_FLIP)) {
                mListPreference.setValue(ResourceUtils.getString(defaultAnimation));
            } else {
                mListPreference.setValue(SettingsManager.getGlyphFlipAnimation());
            }
        }

        mLivePreviewPreference = findPreference(livePreviewKey);
        mGlyphAnimationPreference = findPreference(animationPreviewKey);

        mReverseAnimationSwitch = findPreference(reverseAnimationKey);
        mReverseAnimationSwitch.setOnPreferenceChangeListener(this);

        getActivity().setTitle(fragmentTitle);
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean isPlayable = true;

        shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                && mListPreference.getValue().equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
        boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

        mReverseAnimationSwitch.setVisible(!shouldAlternate);

        if (mListPreference.getValue().startsWith(userAnimationPrefix)) {
            String animationName = mListPreference.getValue();
            AnimationUtils.checkUserAnimation(animationName);
            try {
                String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                        StandardCharsets.UTF_8);
                isPlayable = !AnimationUtils.isAnimationComplex(csv);
            } catch (Exception e) {

            }
        }
        if (isPlayable) {
            mGlyphAnimationPreference.updateAnimation(
                    isAnimationEnabled(),
                    getGlyphAnimation(),
                    1500,
                    shouldReverse,
                    shouldAlternate
            );
        }
        mGlyphAnimationPreference.setVisible(isPlayable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(animationListKey)) {
            String animationName = newValue.toString();
            boolean isPlayable = true;

            shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && animationName.equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
            boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

            mReverseAnimationSwitch.setVisible(!shouldAlternate);

            if (shouldAlternate) animationName = SettingsManager.getGlyphNotifsAnimation();
            if (animationName.startsWith(userAnimationPrefix)) {
                if (!AnimationUtils.checkUserAnimation(animationName)) return false;
                try {
                    String csv = new String(ResourceUtils.getAnimation(animationName).readAllBytes(),
                            StandardCharsets.UTF_8);
                    isPlayable = !AnimationUtils.isAnimationComplex(csv);
                } catch (Exception e) {

                }
            }
            mGlyphAnimationPreference.updateAnimation(
                    isAnimationEnabled(),
                    animationName,
                    1500,
                    shouldReverse,
                    shouldAlternate
            );
            mGlyphAnimationPreference.setVisible(isPlayable);
            if (livePreviewThread != null && livePreviewThread.isAlive()) {
                livePreviewThread.interrupt();
            }
            if (!isPlayable) {
                showToast(R.string.glyph_settings_user_animation_is_complex);
            }

            resolveAppSummaries(animationName);

            return true;
        }

        if (preferenceKey.equals(Constants.GLYPH_FLIP_SUB_ANIMATION_ENABLE)) {
            shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                    && getGlyphAnimation().equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
            boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;

            mGlyphAnimationPreference.updateAnimation((Boolean) newValue, 1500, shouldReverse);
        }

        if (preferenceKey.equals(reverseAnimationKey)) {
            mGlyphAnimationPreference.updateAnimation(isAnimationEnabled(), 1500, (Boolean) newValue);
        }

        if (preferenceKey.equals(Constants.GLYPH_NOTIFS_TONE_SYNC)) {
            if ((Boolean) newValue) {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title_sync_enabled);
            } else {
                mListPreference.setTitle(R.string.glyph_settings_sub_animations_title);
            }
            mHandler.post(ServiceUtils::checkGlyphService);

        }

        return true;
    }

    private final ActivityResultLauncher<Void> mContactPicker
            = registerForActivityResult(
                    new ActivityResultContracts.PickContact(), uri -> {
                if (uri != null && mContactPickerAction != null) {
                    mContactPickerAction.accept(uri);
                }
            }
    );

    private void inflateAppLists() {
        if (fragmentType.equals(FRAGMENT_TYPE_CALL)) {
            Map<String, Data.ApplicationData> callApps =
                    Data.ApplicationData.getCallApps(Constants.CONTEXT);

            callApps.remove(getDefaultDialer());

            if (callApps.isEmpty()) {
                getPreferenceScreen().removePreference(appListCategory);
                return;
            }
            addAppPreferences(callApps);
        }

        if (fragmentType.equals(FRAGMENT_TYPE_NOTIF)) {
            Map<String, Data.ApplicationData> notificationApps =
                    Data.ApplicationData.getNotificationApps(Constants.CONTEXT);
                        addAppPreferences(notificationApps);
        }
    }

    private void addAppPreferences(Map<String, Data.ApplicationData> appData) {
        appData.forEach((pkg, data) -> {
            String label = data.label();
            PrimarySwitchPreference mSwitchPreference
                    = new PrimarySwitchPreference(getPreferenceScreen().getContext());
            mSwitchPreference.setKey(pkg);
            mSwitchPreference.setTitle(" " + label);
            mSwitchPreference.setIcon(data.icon());
            mSwitchPreference.setChecked(isAnimationEnabled(pkg));
            if (fragmentType.equals(FRAGMENT_TYPE_NOTIF)) {
                mSwitchPreference.setOnPreferenceClickListener(preference -> {
                    String key = preference.getKey();
                    Bundle b = new Bundle();
                    b.putString(ARG_PACKAGE, key);
                    b.putString(ARG_CHANNEL_ID, notificationChannelId);
                    Intent intent = new Intent(requireContext(),
                            SubSettingsActivity.class);
                    intent.putExtra(SubSettingsActivity.ARGS_FRAGMENT,
                            NotificationChannelsFragment.class.getName());
                    intent.putExtra("args", b);
                    startActivity(intent);
                    return true;
                });
            } else {
                mSwitchPreference.setOnPreferenceClickListener(preference -> {
                    String key = preference.getKey();
                    Intent intent = new Intent(requireContext(),
                            AnimationSettingsActivity.class);
                    intent.putExtra(ARG_FRAGMENT_TYPE, fragmentType);
                    intent.putExtra(ARG_PACKAGE, key);
                    startActivity(intent);
                    return true;
                });
            }
            mSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String key = preference.getKey();
                setAnimationEnabled(key, (Boolean) newValue);
                return true;
            });
            resolveAppSummary(mSwitchPreference, pkg);
            appListCategory.addPreference(mSwitchPreference);
        });
    }

    private void addDeletePref(String sharedPref, String msgLabel) {
        PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
        Preference mDeletePreferences = new Preference(mScreen.getContext());
        mDeletePreferences.setTitle(R.string.glyph_settings_delete_title);
        mDeletePreferences.setOnPreferenceClickListener(pref -> {
            showDialog(requireActivity(),
                    getString(R.string.glyph_settings_delete_title) + "?",
                    getString(R.string.glyph_settings_delete_confirm_message_start)
                            + " " + msgLabel + "?",
                    android.R.string.ok,
                    () -> {
                        requireContext().deleteSharedPreferences(
                                sharedPref);
                        getActivity().finish();
                    },
                    android.R.string.cancel, null);
            return true;
        });
        mDeletePreferences.setIcon(R.drawable.ic_delete_forever);
        mScreen.addPreference(mCategory);
        mCategory.addPreference(mDeletePreferences);
    }

    private void resolveAppSummary(PrimarySwitchPreference pref, String pkg, String comp) {
        boolean isReversed = isAppAnimationReversed(pkg);
        boolean hasConfig = comp == null ? appHasConfig(pkg) : appHasConfig(pkg, comp);
        if (hasConfig) {
            if (isReversed) {
                pref.setSummary(" " + getGlyphAnimation(pkg, false)
                        + " (" + getString(R.string.glyph_settings_animation_is_reversed) + ")");
            } else {
                pref.setSummary(" " + getGlyphAnimation(pkg, false));
            }
        } else if (!TextUtils.isEmpty(pref.getSummary())) {
            pref.setSummary(null);
        }
    }

    private void resolveAppSummary(PrimarySwitchPreference pref, String pkg) {
       resolveAppSummary(pref, pkg, null);
    }


    private String getDefaultDialer() {
        String packageName = "";
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(dialerIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null) {
            packageName = resolveInfo.activityInfo.packageName;
        }
        return packageName;
    }

    private List<String> getAnimations(int domain) {
        switch (domain) {
            case 0 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getBundledNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getBundledCallAnimations();
                    }
                }
            }
            case 1 -> {
                switch (fragmentType) {
                    case FRAGMENT_TYPE_NOTIF, FRAGMENT_TYPE_FLIP -> {
                        return ResourceUtils.getUserNotificationAnimations();
                    }
                    case FRAGMENT_TYPE_CALL -> {
                        return ResourceUtils.getUserCallAnimations();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private String getGlyphAnimation() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    if (isChannelSpecific) {
                        return SettingsManager.Notification.getChannelAnimation(
                                targetPkg, notificationChannelId);
                    } else {
                        return SettingsManager.getGlyphNotifsAnimation(targetPkg);
                    }
                } else {
                    return SettingsManager.getGlyphNotifsAnimation();
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isContactSpecific) {
                    return SettingsManager.getGlyphCallAnimation(Integer.parseInt(contactId));
                } else if (isAppSpecific) {
                    return SettingsManager.getGlyphCallAnimation(targetPkg);
                } else {
                    return SettingsManager.getGlyphCallAnimation();
                }
            }

            case FRAGMENT_TYPE_FLIP -> {
                String value = SettingsManager.getGlyphFlipAnimation();
                if (value.equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE)) {
                    return SettingsManager.getGlyphNotifsAnimation();
                } else {
                    return value;
                }
            }
        }
        return "";
    }

    private String getGlyphAnimation(String pkg, boolean internal) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                String anim = SettingsManager.getGlyphNotifsAnimation(pkg);
                if (internal) {
                    return anim;
                } else {
                    return anim.replace(Constants.GLYPH_USER_NOTIF_CSV_PREFIX, "");
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                String anim = SettingsManager.getGlyphCallAnimation(pkg);
                if (internal) {
                    return anim;
                } else {
                    return anim.replace(Constants.GLYPH_USER_CALL_CSV_PREFIX, "");
                }
            }
        }
        return "";
    }


    private boolean isAnimationEnabled() {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    if (isChannelSpecific) {
                        return SettingsManager.Notification.isChannelAnimationEnabled(
                                targetPkg, notificationChannelId);
                    } else {
                        return SettingsManager.isGlyphNotifsEnabled(targetPkg);
                    }
                } else {
                    return SettingsManager.isGlyphNotifsEnabled();
                }
            }

            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    return SettingsManager.isGlyphCallEnabled(targetPkg);
                } else {
                    return SettingsManager.isGlyphCallEnabled();
                }
            }

            case FRAGMENT_TYPE_FLIP -> {
                return SettingsManager.isGlyphFlipEnabled()
                        && SettingsManager.isGlyphFlipAnimationEnabled();
            }
        }
        return false;
    }

    private boolean isAnimationEnabled(String pkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                    return SettingsManager.isGlyphNotifsEnabled(pkg);
            }

            case FRAGMENT_TYPE_CALL -> {
                    return SettingsManager.isGlyphCallEnabled(pkg);
            }
        }
        return false;
    }

    private void setAnimationEnabled(boolean state) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                if (isAppSpecific) {
                    if (isChannelSpecific) {
                        SettingsManager.Notification.setChannelAnimationEnabled(
                                state, targetPkg, notificationChannelId);
                    } else {
                        SettingsManager.setGlyphNotifsEnabled(targetPkg, state);
                    }
                } else {
                    SettingsManager.setGlyphNotifsEnabled(state);
                }
            }
            case FRAGMENT_TYPE_CALL -> {
                if (isAppSpecific) {
                    SettingsManager.setGlyphCallEnabled(targetPkg, state);
                } else {
                    SettingsManager.setGlyphCallEnabled(state);
                }
            }
            case FRAGMENT_TYPE_FLIP -> {
                SettingsManager.setGlyphFlipEnabled(state);
            }
        }
    }

    private void setAnimationEnabled(String pkg, boolean state) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                    SettingsManager.setGlyphNotifsEnabled(pkg, state);
            }
            case FRAGMENT_TYPE_CALL -> {
                    SettingsManager.setGlyphCallEnabled(pkg, state);
            }
        }
    }

    private boolean appHasConfig(String pkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.appHasGlyphNotifsConfig(pkg);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.appHasGlyphCallConfig(pkg);
            }
        }
        return false;
    }

    private boolean appHasConfig(String pkg, String comp) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.appHasGlyphNotifsConfig(pkg, comp);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.appHasGlyphCallConfig(pkg, comp);
            }
        }
        return false;
    }

    private boolean isAppAnimationReversed(String pkg) {
        switch (fragmentType) {
            case FRAGMENT_TYPE_NOTIF -> {
                return SettingsManager.isGlyphNotifsAnimationReversed(pkg);
            }
            case FRAGMENT_TYPE_CALL -> {
                return SettingsManager.isGlyphCallAnimationReversed(pkg);
            }
        }
        return false;
    }


    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (livePreviewKey.equals(preference.getKey())) {
            mLivePreviewPreference.setEnabled(false);
            mLivePreviewPreference.setSummary(
                    R.string.glyph_settings_animations_live_preview_summary_playing
            );
            livePreviewThread = new Thread(() -> {
                Activity activity = getActivity();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    resetLivePreview();
                    return;
                }
                shouldAlternate = fragmentType.equals(FRAGMENT_TYPE_FLIP)
                        && mListPreference.getValue().equals(Constants.GLYPH_NOTIF_ANIMATION_ALTERNATE);
                boolean shouldReverse = mReverseAnimationSwitch.isChecked() && !shouldAlternate;
                AnimationManager.playCsv(
                        requireContext(),
                        getGlyphAnimation(),
                        false,
                        shouldReverse,
                        shouldAlternate
                );

                if (activity != null) {
                    activity.runOnUiThread(this::resetLivePreview);
                }
            });
            livePreviewThread.start();
        }
        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setAnimationEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mGlyphAnimationPreference.updateAnimation(isChecked, getGlyphAnimation(), 1500);
    }


    @Override
    public void onResume() {
        super.onResume();
        updatePrimarySwitches();
        resetLivePreview();
    }

    private void updatePrimarySwitches() {
        if ((fragmentType.equals(FRAGMENT_TYPE_CALL)
                || fragmentType.equals(FRAGMENT_TYPE_NOTIF))
                && !isAppSpecific
                && !isContactSpecific) {
            for (int i = 0; i < appListCategory.getPreferenceCount(); i++) {
                Preference pref = appListCategory.getPreference(i);
                if (pref instanceof PrimarySwitchPreference) {
                    PrimarySwitchPreference switchPref = (PrimarySwitchPreference) pref;
                    switch (fragmentType) {
                        case FRAGMENT_TYPE_NOTIF ->
                                switchPref.setChecked(SettingsManager.isGlyphNotifsEnabled(pref.getKey()));
                        case FRAGMENT_TYPE_CALL ->
                                switchPref.setChecked(SettingsManager.isGlyphCallEnabled(pref.getKey()));
                    }
                    resolveAppSummary(switchPref, pref.getKey());
                }
            }
        }
    }

    private void resolveAppSummaries(String comp) {
        if ((fragmentType.equals(FRAGMENT_TYPE_CALL)
                || fragmentType.equals(FRAGMENT_TYPE_NOTIF))
                && !isAppSpecific
                && !isContactSpecific) {
            for (int i = 0; i < appListCategory.getPreferenceCount(); i++) {
                Preference pref = appListCategory.getPreference(i);
                if (pref instanceof PrimarySwitchPreference) {
                    PrimarySwitchPreference switchPref = (PrimarySwitchPreference) pref;
                    resolveAppSummary(switchPref, pref.getKey(), comp);
                }
            }
        }
    }

    private void resetLivePreview() {
        mLivePreviewPreference.setEnabled(true);
        mLivePreviewPreference.setSummary(
                R.string.glyph_settings_animations_live_preview_summary
        );
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (livePreviewThread != null && livePreviewThread.isAlive()) {
            livePreviewThread.interrupt();
            livePreviewThread = null;
        }
    }

}
