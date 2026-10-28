package co.aospa.glyph.Settings;

import static co.aospa.glyph.Utils.InterfaceUtils.Preferences.getAllPreferences;
import static co.aospa.glyph.Utils.InterfaceUtils.showToast;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.ExpandablePreference;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Data;
import co.aospa.glyph.Manager.SettingsManager;

import co.aospa.glyph.R;

public class NotificationChannelsFragment extends SettingsBasePreferenceFragment {

    private static final Comparator<NotificationChannel> BY_NAME =
            Comparator.comparing(c -> c.getName().toString());

    public static final String ARG_PACKAGE = "package";

    private String targetPkg = "";
    private String appLabel;

    private LayoutPreference mHeaderPref;
    private MainSwitchPreference appToggle;
    private PrimarySwitchPreference globalToggle;
    private PreferenceCategory channelsCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

        Bundle args = getArguments();
        targetPkg = args.getString(ARG_PACKAGE,"");

        if (targetPkg.isEmpty()) {
            getParentFragmentManager().popBackStack();
            showToast("Fragment missing package argument!");
        }

        appLabel = Data.ApplicationData.getApplication(requireContext(), targetPkg).label();
        getActivity().setTitle(appLabel);

        getPreferenceManager().setSharedPreferencesName(Constants.GLYPH_NOTIF_APP_PREF_PREFIX
                + targetPkg);

        buildHeader(requireContext());
        addAppToggle();
        addGlobalToggle();
        buildList();
    }

    private void buildHeader(Context context) {
        float d = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, (int) (24 * d), 0, (int) (16 * d));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(Data.ApplicationData.getApplication(context, targetPkg).icon());
        root.addView(icon, new LinearLayout.LayoutParams((int) (48 * d), (int) (48 * d)));

        LayoutPreference pref = new LayoutPreference(context, root);
        pref.setKey("pref_app_header");
        pref.setSelectable(false);
        mHeaderPref = pref;
        getPreferenceScreen().addPreference(mHeaderPref);
    }

    private void addAppToggle() {
        PreferenceScreen screen = getPreferenceScreen();
        appToggle = new MainSwitchPreference(screen.getContext());
        appToggle.setKey(Constants.GLYPH_NOTIFS_SUB_ENABLE);
        appToggle.setTitle(getString(R.string.glyph_settings_notifs_sub_toggle_title));
        appToggle.setChecked(isNotifsAnimationEnabledForApp());
        appToggle.addOnSwitchChangeListener((buttonView, isChecked) -> {
            setNotifsAnimationEnabledForApp(isChecked);
            calcVisibility(isChecked);
        });
        appToggle.setPersistent(false);
        screen.addPreference(appToggle);
    }

    private void addGlobalToggle() {
        PreferenceScreen screen = getPreferenceScreen();
        globalToggle = new PrimarySwitchPreference(screen.getContext());
        globalToggle.setKey(Constants.GLYPH_NOTIFS_APP_GLOBAL_ANIMATION_ENABLE);
        globalToggle.setTitle(getString(R.string.glyph_settings_notifs_global_animation_toggle_title));
        globalToggle.setSummary(getString(R.string.glyph_settings_notifs_global_animation_toggle_summary)
                + " " + "(" + SettingsManager.getGlyphNotifsAnimation(targetPkg) + ")");
        Bundle args = new Bundle();
        args.putString(AnimationSettingsFragment.ARG_FRAGMENT_TYPE,
                AnimationSettingsFragment.FRAGMENT_TYPE_NOTIF);
        args.putString(AnimationSettingsFragment.ARG_PACKAGE, targetPkg);
        Intent i = new Intent(requireContext(), SubSettingsActivity.class);
        i.putExtra("fragment", AnimationSettingsFragment.class.getName());
        i.putExtra("args", args);
        globalToggle.setIntent(i);
        globalToggle.setChecked(isGlobalAppAnimationEnabled());
        globalToggle.setPersistent(false);
        globalToggle.setOnPreferenceChangeListener((pref, newValue) -> {
            setGlobalAppAnimationEnabled((Boolean) newValue);
            updatePrimarySwitches();
            return true;
        });
        screen.addPreference(globalToggle);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePrimarySwitches();
        calcVisibility(isNotifsAnimationEnabledForApp());
    }

    private void buildList() {
        Context context = requireContext();
        PreferenceScreen screen = getPreferenceScreen();

        List<NotificationChannelGroup> groups = new ArrayList<>(
                Data.ApplicationData.getApplication(context, targetPkg)
                .channelGroups());
        List<NotificationChannel> allChannels = Data.ApplicationData.getApplication(context, targetPkg)
                .channels();

        groups.sort(Comparator.comparing(g -> g.getName().toString()));
        Set<String> knownGroupIds = new HashSet<>();
        for (NotificationChannelGroup g : groups) knownGroupIds.add(g.getId());

        channelsCategory = new PreferenceCategory(context);
        channelsCategory.setTitle("Notification channels");
        screen.addPreference(channelsCategory);

        Map<String, List<NotificationChannel>> byGroup = new HashMap<>();
        List<NotificationChannel> other = new ArrayList<>();
        for (NotificationChannel ch : allChannels) {
            String groupId = ch.getGroup();
            if (groupId == null || !knownGroupIds.contains(groupId)) {
                other.add(ch);
            } else {
                byGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(ch);
            }
        }

        for (NotificationChannelGroup group : groups) {
            List<NotificationChannel> channels = byGroup.get(group.getId());
            if (channels == null || channels.isEmpty()) continue;
            channels.sort(BY_NAME);
            addSection(context, "group_" + group.getId(),
                    group.getName(), channels);
        }

        if (!other.isEmpty()) {
            other.sort(BY_NAME);
            addSection(context, "group_other", "Other",  other);
        }
    }

    private void addSection(Context context, String key, CharSequence title,
                            List<NotificationChannel> channels) {
        ExpandablePreference header = new ExpandablePreference(context);
        header.setKey(key);
        header.setTitle(title);
        channelsCategory.addPreference(header);

        for (NotificationChannel ch : channels) {
            PrimarySwitchPreference pref = new PrimarySwitchPreference(context);
            pref.setKey(ch.getId());
            pref.setTitle(ch.getName());
            pref.setSummary(resolveSummary(
                    ch.getId(), SettingsManager.getGlyphNotifsAnimation(targetPkg)));
            pref.setChecked(isChannelAnimationEnabled(ch.getId()));

            pref.setOnPreferenceClickListener(p -> {
                openChannelSettings(ch.getId());
                return true;
            });
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                setChannelAnimationEnabled((Boolean) newValue, ch.getId());
                return false;
            });

            header.addPreference(pref);
        }
    }

    private void openChannelSettings(String channelId) {
        Bundle args = new Bundle();
        args.putString(AnimationSettingsFragment.ARG_FRAGMENT_TYPE,
                AnimationSettingsFragment.FRAGMENT_TYPE_NOTIF);
        args.putString(AnimationSettingsFragment.ARG_PACKAGE, targetPkg);
        args.putString(AnimationSettingsFragment.ARG_CHANNEL_ID, channelId);
        Intent i = new Intent(requireContext(), SubSettingsActivity.class);
        i.putExtra("fragment", AnimationSettingsFragment.class.getName());
        i.putExtra("args", args);
        requireContext().startActivity(i);
    }
    
    private boolean isNotifsAnimationEnabledForApp() {
        return SettingsManager.isGlyphNotifsEnabled(targetPkg);
    }

    private void setNotifsAnimationEnabledForApp(boolean state) {
        SettingsManager.setGlyphNotifsEnabled(targetPkg, state);
    }

    private void setChannelAnimationEnabled(boolean state, String channelId) {
        SettingsManager.Notification.setChannelAnimationEnabled(state, targetPkg, channelId);
    }

    private boolean isChannelAnimationEnabled(String channelId) {
        return SettingsManager.Notification.isChannelAnimationEnabled(targetPkg, channelId);
    }

    private boolean isChannelAnimationReversed(String channelId) {
        return SettingsManager.Notification.isChannelAnimationReversed(targetPkg, channelId);
    }

    private boolean channelHasConfig(String channelId, String currentAnim) {
        return SettingsManager.Notification.channelHasGlyphNotifsConfig(targetPkg, channelId, currentAnim);
    }

    private boolean channelHasConfig(String channelId) {
        return SettingsManager.Notification.channelHasGlyphNotifsConfig(targetPkg, channelId);
    }

    private String getChannelAnimation(String channelId) {
        return SettingsManager.Notification.getChannelAnimation(targetPkg, channelId);
    }

    private boolean isGlobalAppAnimationEnabled() {
        return SettingsManager.Notification.isGlobalAppAnimationEnabled(targetPkg);
    }

    private void setGlobalAppAnimationEnabled(boolean state) {
        SettingsManager.Notification.setGlobalAppAnimationEnabled(targetPkg, state);
    }

    private String resolveSummary(String channelId, String comp) {
        boolean isReversed = isChannelAnimationReversed(channelId);
        boolean hasConfig = comp == null ? channelHasConfig(channelId) : channelHasConfig(channelId, comp);
        if (hasConfig && !isGlobalAppAnimationEnabled()) {
            if (isReversed) {
                return getChannelAnimation(channelId).replace(Constants.GLYPH_USER_NOTIF_CSV_PREFIX, "")
                        + " (" + getString(R.string.glyph_settings_animation_is_reversed) + ")";
            } else {
                return getChannelAnimation(channelId)
                         .replace(Constants.GLYPH_USER_NOTIF_CSV_PREFIX, "");
            }
        }
        return null;
    }

    private void updatePrimarySwitches() {
        for (Preference p : getAllPreferences(getPreferenceScreen())) {
            if (p instanceof PrimarySwitchPreference pref) {
                if (pref == globalToggle) {
                    pref.setChecked(isGlobalAppAnimationEnabled());
                    pref.setSummary(getString(R.string.glyph_settings_notifs_global_animation_toggle_summary)
                            + " " + "(" + SettingsManager.getGlyphNotifsAnimation(targetPkg) + ")");
                } else {
                    pref.setChecked(isChannelAnimationEnabled(pref.getKey()));
                    pref.setSummary(resolveSummary(
                            pref.getKey(),
                            SettingsManager.getGlyphNotifsAnimation(targetPkg))
                    );
                    pref.setEnabled(!isGlobalAppAnimationEnabled());
                }
            }
        }
    }

    private void calcVisibility(boolean appState) {
        channelsCategory.setVisible(appState);
        globalToggle.setVisible(appState);
    }


}
