package co.aospa.glyph.Settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.CardPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.aospa.glyph.Data;
import co.aospa.glyph.R;

public class EssentialRulesFragment extends SettingsBasePreferenceFragment {

    private Context prefContext;

    private CardPreference emptyMessage;
    private PreferenceScreen mScreen;
    private PreferenceCategory mCategory;

    private static final String KEY_RULES_CATEGORY = "rules_category";
    private static final String KEY_EMPTY_MESSAGE = "rules_empty_message";

    private final Runnable mRulesListener = () -> initScreen();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefContext = getPreferenceManager().getContext();
        mScreen = getPreferenceManager().createPreferenceScreen(prefContext);
        setPreferenceScreen(mScreen);

        requireActivity().setTitle(R.string.glyph_settings_notifs_sub_essential_title);
        initScreen();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                MenuItem addRule = menu.add(Menu.NONE, 0, 0, "Add rule");
                addRule.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                addRule.setIcon(R.drawable.ic_add);
                MenuItemCompat.setIconTintList(addRule, ColorStateList.valueOf(Color.WHITE));
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == 0) {
                    startActivity(new Intent(requireContext(), EssentialRuleEditorActivity.class));
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void initScreen() {
        if (!Data.EssentialRule.getRules().isEmpty()) {
            if (mScreen.findPreference(KEY_RULES_CATEGORY) == null) {
                if (mCategory == null) {
                    mCategory = new PreferenceCategory(prefContext);
                    mCategory.setKey(KEY_RULES_CATEGORY);
                    mCategory.setTitle(getString(
                            R.string.glyph_settings_essential_rules_category_title));
                    mCategory.setOrderingAsAdded(false);
                }
                mScreen.addPreference(mCategory);
            }
            syncRules(Data.EssentialRule.getRules());

            Preference empty = mScreen.findPreference(KEY_EMPTY_MESSAGE);
            if (empty != null) mScreen.removePreference(empty);
        } else {
            if (mScreen.findPreference(KEY_EMPTY_MESSAGE) == null) {
                if (emptyMessage == null) {
                    emptyMessage = new CardPreference(prefContext);
                    emptyMessage.setKey(KEY_EMPTY_MESSAGE);
                    emptyMessage.setTitle(getString(
                            R.string.glyph_settings_essential_rule_list_no_rules_title));
                    emptyMessage.setSummary(getString(
                            R.string.glyph_settings_essential_rule_list_no_rules_summary));
                    emptyMessage.setSelectable(false);
                }
                mScreen.addPreference(emptyMessage);
            }

            Preference category = mScreen.findPreference(KEY_RULES_CATEGORY);
            if (category != null) mScreen.removePreference(category);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Data.EssentialRule.addListener(mRulesListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        initScreen();
    }

    @Override
    public void onStop() {
        super.onStop();
        Data.EssentialRule.removeListener(mRulesListener);
    }


    private void syncRules(List<Data.EssentialRule> rules) {
        Set<String> newKeys = new HashSet<>();
        for (Data.EssentialRule data : rules) newKeys.add(data.ruleId());

        List<Preference> toRemove = new ArrayList<>();
        for (int i = 0; i < mCategory.getPreferenceCount(); i++) {
            Preference p = mCategory.getPreference(i);
            if (!newKeys.contains(p.getKey())) toRemove.add(p);
        }
        for (Preference p : toRemove) mCategory.removePreference(p);

        int order = 0;
        for (Data.EssentialRule data : rules) {
            Preference pref = mCategory.findPreference(data.ruleId());
            if (pref == null) {
                pref = new Preference(requireContext());
                pref.setKey(data.ruleId());
                mCategory.addPreference(pref);
            }
            bindRulePref(pref, data);
            pref.setOrder(order++);
        }
    }

    private String evalSummary(Data.EssentialRule data) {
        StringBuilder summaryString = new StringBuilder();

        summaryString.append(!data.packages().isEmpty() ?
                data.packages().size()
                : getString(R.string.glyph_settings_essential_rule_editor_any_summary))
                .append(" ");
        summaryString.append(data.packages().size() == 1
                ? getString(R.string.glyph_settings_essential_rule_application_summary)
                : getString(R.string.glyph_settings_essential_rule_application_summary_plural));
        summaryString.append(", ");
        summaryString.append(!data.keywords().isEmpty() ?
                data.keywords().size()
                : getString(R.string.glyph_settings_essential_rule_editor_any_summary)).append(" ");
        summaryString.append(data.keywords().size() == 1
                ? getString(R.string.glyph_settings_essential_rule_keyword_summary)
                : getString(R.string.glyph_settings_essential_rule_keyword_summary_plural));
        return summaryString.toString();
    }

    private void bindRulePref(Preference pref, Data.EssentialRule data) {
        pref.setTitle(data.name());
        pref.setSummary(evalSummary(data));
        pref.setOnPreferenceClickListener(p -> {
            Intent intent = new Intent(requireContext(), EssentialRuleEditorActivity.class);
            intent.putExtra("ruleId", data.ruleId());
            startActivity(intent);
            return true;
        });
    }

}
