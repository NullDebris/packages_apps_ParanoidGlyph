package co.aospa.glyph.Settings;

import static co.aospa.glyph.Utils.InterfaceUtils.showDialog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Data;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Preference.AppPickerFragment;
import co.aospa.glyph.R;

public class EssentialRuleEditorFragment extends SettingsBasePreferenceFragment {

    public static class RuleEditViewModel extends ViewModel {
        boolean initialized = false;
        String name = "";
        Set<String> packages = new HashSet<>();
        String keywordsRaw = "";
    }

    private PreferenceScreen mScreen;

    private String ruleId = "";
    private boolean isExistingRule = false;

    private RuleEditViewModel vm;

    private EditTextPreference namePref;
    private Preference appListPref;
    private EditTextPreference keywordPref;
    
    private Preference mDeletePref;
    private Preference mSavePref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Bundle args = getArguments();
        String argId = args != null ? args.getString("ruleId", "") : "";
        ruleId = "0".equals(argId) ? "" : argId;
        isExistingRule = !ruleId.isEmpty();


        requireActivity().setTitle(isExistingRule
                ? R.string.glyph_settings_essential_rule_editor_edit_rule_title
                : R.string.glyph_settings_essential_rule_editor_new_rule_title);

        if (isExistingRule) {
            String prefName = Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId;
            getPreferenceManager().setSharedPreferencesName(prefName);
        }

        addPreferencesFromResource(R.xml.glyph_essential_settings_rule_editor);

        mScreen = getPreferenceScreen();

        if (isExistingRule) {
            addSavePref();
            addDeletePref(getPreferenceManager().getSharedPreferencesName());
        } else {
            addSavePref();
        }

        namePref = findPreference(Constants.Essential.GLYPH_ESSENTIAL_RULE_NAME);
        appListPref = findPreference(Constants.Essential.GLYPH_ESSENTIAL_RULE_PACKAGES);
        keywordPref = findPreference(Constants.Essential.GLYPH_ESSENTIAL_RULE_KEYWORDS);

        vm = new ViewModelProvider(this).get(RuleEditViewModel.class);
        if (!vm.initialized) {
            if (isExistingRule) {
                SharedPreferences sp = getPreferenceManager().getSharedPreferences();
                vm.name = sp.getString(Constants.Essential.GLYPH_ESSENTIAL_RULE_NAME, "");
                vm.packages = new HashSet<>(sp.getStringSet(
                        Constants.Essential.GLYPH_ESSENTIAL_RULE_PACKAGES, Collections.emptySet()));
                vm.keywordsRaw = String.join(", ", sp.getStringSet(
                        Constants.Essential.GLYPH_ESSENTIAL_RULE_KEYWORDS, Collections.emptySet()));
            }
            vm.initialized = true;
            setCommitAllowed();
        }

        namePref.setText(vm.name);
        if (isExistingRule) namePref.setSummary(vm.name);
        keywordPref.setText(vm.keywordsRaw);
        refreshAppSummary();
        refreshKeywordSummary();

        namePref.setOnPreferenceChangeListener((pref, newValue) -> {
            String val = (String) newValue;
            vm.name = val;
            namePref.setSummary(val);
            setCommitAllowed();
            return true;
        });
        keywordPref.setOnPreferenceChangeListener((pref, newValue) -> {
            vm.keywordsRaw = (String) newValue;
            keywordPref.setText(vm.keywordsRaw);
            refreshKeywordSummary();
            setCommitAllowed();
            return true;
        });

        appListPref.setOnPreferenceClickListener(pref -> {
            openAppPicker(vm.packages);
            return true;
        });

    }

    private void refreshAppSummary() {
        if (vm.packages.isEmpty()) {
            appListPref.setSummary(R.string.glyph_settings_essential_rule_editor_any_summary);
            return;
        }
        appListPref.setSummary(vm.packages.stream()
                .map(this::labelFor)
                .collect(Collectors.joining(", ")));
    }

    private String labelFor(String pkg) {
        var app = Data.ApplicationData.getApplication(requireContext(), pkg);
        return (app != null && app.label() != null) ? app.label() : pkg;
    }

    private void refreshKeywordSummary() {
        Set<String> keywords = parseKeywords(vm.keywordsRaw);
        if (keywords.isEmpty()) {
            keywordPref.setSummary(R.string.glyph_settings_essential_rule_editor_any_summary);
        } else {
            keywordPref.setSummary(String.join(", ", keywords));
        }
    }

    private static Set<String> parseKeywords(@Nullable String raw) {
        Set<String> keywords = new LinkedHashSet<>();
        if (raw == null) return keywords;
        for (String part : raw.split(",")) {
            String kw = part.trim();
            if (!kw.isEmpty()) keywords.add(kw);
        }
        return keywords;
    }

    private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                ArrayList<String> pkgs = result.getData()
                        .getStringArrayListExtra(AppPickerFragment.RESULT_PACKAGES);
                if (pkgs == null) return;

                vm.packages = new HashSet<>(pkgs);
                refreshAppSummary();
                setCommitAllowed();
            });

    private void openAppPicker(Set<String> preselected) {
        Bundle args = new Bundle();
        args.putStringArrayList(AppPickerFragment.ARG_SELECTED, new ArrayList<>(preselected));
        args.putBoolean(AppPickerFragment.ARG_FOR_ACTIVITY_RESULT, true);
        Intent i = new Intent(requireContext(), SubSettingsActivity.class);
        i.putExtra("fragment", AppPickerFragment.class.getName());
        i.putExtra("args", args);
        pickerLauncher.launch(i);
    }

    private boolean isCommitAllowed() {
        if (vm.name.trim().isEmpty()) return false;
        return !vm.packages.isEmpty() || !parseKeywords(vm.keywordsRaw).isEmpty();
    }

    private void setCommitAllowed() {
        mSavePref.setEnabled(isCommitAllowed());
    }

    private void commitScreen() {
        if (!isCommitAllowed()) return;
        if (ruleId.isEmpty()) ruleId = String.valueOf(System.currentTimeMillis());

        SharedPreferences sp = requireContext().getSharedPreferences(
                Constants.Essential.GLYPH_ESSENTIAL_RULE_PREFIX + ruleId, Context.MODE_PRIVATE);
        sp.edit()
                .putString(Constants.Essential.GLYPH_ESSENTIAL_RULE_NAME, vm.name.trim())
                .putStringSet(Constants.Essential.GLYPH_ESSENTIAL_RULE_PACKAGES, vm.packages)
                .putStringSet(Constants.Essential.GLYPH_ESSENTIAL_RULE_KEYWORDS, parseKeywords(vm.keywordsRaw))
                .commit();
    }
    
    private void addSavePref() {
        PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
        mSavePref = new Preference(mScreen.getContext());
        mSavePref.setTitle(R.string.glyph_settings_essential_rule_editor_save_rule_title);
        mSavePref.setOnPreferenceClickListener(pref -> {
            commitScreen();
            Data.EssentialRule.reloadRules();
            getActivity().finish();
            return true;
        });
        mSavePref.setIcon(R.drawable.ic_file_save);
        mScreen.addPreference(mCategory);
        mCategory.addPreference(mSavePref);
    }

    private void addDeletePref(String sharedPref) {
        PreferenceCategory mCategory = new PreferenceCategory(mScreen.getContext());
        mDeletePref = new Preference(mScreen.getContext());
        mDeletePref.setTitle(R.string.glyph_settings_essential_rule_editor_delete_rule_title);
        mDeletePref.setOnPreferenceClickListener(pref -> {
            showDialog(requireActivity(),
                    getString(R.string.glyph_settings_essential_rule_editor_delete_rule_title) + "?",
                    getString(R.string.glyph_settings_essential_rule_editor_delete_dialog_message),
                    android.R.string.ok,
                    () -> {
                        requireContext().deleteSharedPreferences(
                                sharedPref);
                        Data.EssentialRule.reloadRules();
                        getActivity().finish();
                    },
                    android.R.string.cancel, null);
            return true;
        });
        mDeletePref.setIcon(R.drawable.ic_delete_forever);
        mScreen.addPreference(mCategory);
        mCategory.addPreference(mDeletePref);
    }
}
