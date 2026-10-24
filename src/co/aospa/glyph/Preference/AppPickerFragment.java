package co.aospa.glyph.Preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import android.widget.SearchView;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.aospa.glyph.Data;

import co.aospa.glyph.R;

import co.aospa.glyph.Utils.ResourceUtils;

public class AppPickerFragment extends PreferenceFragmentCompat {

    public static final String ARG_FOR_ACTIVITY_RESULT = "for_activity_result";
    public static final String ARG_SELECTED = "selected";

    public static final String REQUEST_KEY = "app_picker_result";
    public static final String RESULT_PACKAGES = "packages";

    private static final String STATE_SELECTED = "state_selected";
    private static final String STATE_QUERY = "state_query";

        public record AppEntry(String packageName, String label, @Nullable Drawable icon) {
            public AppEntry(@NonNull String packageName, @NonNull String label, @Nullable Drawable icon) {
                this.packageName = packageName;
                this.label = label;
                this.icon = icon;
            }
        }

    // ------------------------------------------------------------------------

    private final Set<String> selected = new LinkedHashSet<>();
    private final List<CheckBoxPreference> rows = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private PreferenceCategory category;
    private String query = "";
    private Resources res;

    public static AppPickerFragment newInstance(@Nullable Set<String> preselected) {
        AppPickerFragment f = new AppPickerFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_SELECTED,
                preselected == null ? new ArrayList<>() : new ArrayList<>(preselected));
        f.setArguments(args);
        return f;
    }


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(STATE_SELECTED);
            if (saved != null) selected.addAll(saved);
            query = savedInstanceState.getString(STATE_QUERY, "");
        } else {
            ArrayList<String> initial = null;
            if (getArguments() != null) {
                initial = getArguments().getStringArrayList(ARG_SELECTED);
            }
            if (initial == null && getActivity() != null && getActivity().getIntent() != null) {
                initial = getActivity().getIntent().getStringArrayListExtra(ARG_SELECTED);
            }
            if (initial != null) selected.addAll(initial);
        }
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        category = new PreferenceCategory(requireContext());
        requireActivity().setTitle(R.string.glyph_settings_app_list_title);
        screen.addPreference(category);

        loadApps();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                MenuItem searchItem = menu.add(Menu.NONE, 1, 0,
                        getString(R.string.glyph_settings_app_list_search_hint));
                searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
                        | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                searchItem.setIcon(R.drawable.ic_search);
                SearchView searchView = new SearchView(requireContext());
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setQueryHint(getString(R.string.glyph_settings_app_list_search_hint));
                searchItem.setActionView(searchView);

                if (!query.isEmpty()) {
                    searchItem.expandActionView();
                    searchView.setQuery(query, false);
                }

                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String q) {
                        searchView.clearFocus();
                        return true;
                    }
                    @Override public boolean onQueryTextChange(String q) {
                        query = q == null ? "" : q;
                        applyFilter();
                        return true;
                    }
                });

                MenuItem done = menu.add(Menu.NONE, 2, 1,
                        getString(R.string.glyph_settings_menu_done_button_title));
                done.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                done.setIcon(R.drawable.ic_check);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == 2) {
                    finishSelection();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void loadApps() {
        Context appCtx = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<AppEntry> apps = new ArrayList<>();
            for (Map.Entry<String, Data.ApplicationData> e : Data.ApplicationData.get(appCtx).entrySet()) {
                Data.ApplicationData d = e.getValue();
                apps.add(new AppEntry(e.getKey(), d.label(), d.icon()));
            }
            main.post(() -> {
                if (isAdded() && category != null) populate(apps);
            });
        });
    }

    private void populate(List<AppEntry> apps) {
        category.removeAll();
        rows.clear();

        for (AppEntry app : apps) {
            CheckBoxPreference pref = new CheckBoxPreference(requireContext());
            pref.setPersistent(false);
            pref.setKey(app.packageName);
            pref.setTitle(app.label);
            pref.setSummary(app.packageName);
            pref.setIcon(app.icon);
            pref.setIconSpaceReserved(true);
            pref.setChecked(selected.contains(app.packageName));
            pref.setOnPreferenceChangeListener((Preference p, Object newValue) -> {
                if ((Boolean) newValue) selected.add(app.packageName);
                else selected.remove(app.packageName);
                return true;
            });
            category.addPreference(pref);
            rows.add(pref);
        }
        applyFilter();
    }

    private void applyFilter() {
        String q = query.trim().toLowerCase(Locale.getDefault());
        for (CheckBoxPreference row : rows) {
            boolean match = q.isEmpty()
                    || String.valueOf(row.getTitle()).toLowerCase(Locale.getDefault()).contains(q)
                    || row.getKey().toLowerCase(Locale.getDefault()).contains(q);
            row.setVisible(match);
        }
    }

    private void finishSelection() {
        ArrayList<String> picked = new ArrayList<>(selected);

        Bundle args = getArguments();
        if (args != null && args.getBoolean(ARG_FOR_ACTIVITY_RESULT, false)) {
            Intent data = new Intent();
            data.putStringArrayListExtra(RESULT_PACKAGES, picked);
            requireActivity().setResult(Activity.RESULT_OK, data);
            requireActivity().finish();
        } else {
            Bundle result = new Bundle();
            result.putStringArrayList(RESULT_PACKAGES, picked);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selected));
        outState.putString(STATE_QUERY, query);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
