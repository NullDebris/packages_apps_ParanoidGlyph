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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceGroup;
import android.telecom.Call;
import android.util.Log;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.util.Set;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Preference.GlyphAnimationPreference;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.Utils.ServiceUtils;

public class CallSettingsFragment extends SettingsBasePreferenceFragment implements OnPreferenceChangeListener,
        OnCheckedChangeListener {

    private PreferenceScreen mScreen;

    private MainSwitchPreference mSwitchBar;

    private ListPreference mListPreference;
    private Preference mLivePreviewPreference;
    private SwitchPreferenceCompat mReverseCallAnimationSwitch;

    private GlyphAnimationPreference mGlyphAnimationPreference;

    private Handler mHandler = new Handler();
    private String mSubId = "0";
    private volatile String prefSuffix = "";

    private Thread livePreviewThread;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Bundle args = getArguments();
        mSubId = args != null ? args.getString("subId") : "0";
        int xmlRes;

        switch (mSubId) {
            case "1" -> xmlRes = R.xml.glyph_call_settings_sim1;
            case "2" -> xmlRes = R.xml.glyph_call_settings_sim2;
            case null,default -> xmlRes = R.xml.glyph_call_settings;
        }

        addPreferencesFromResource(xmlRes);

        mScreen = this.getPreferenceScreen();
        if (!mSubId.equals("0")) {
            getActivity().setTitle(ResourceUtils.getString("glyph_settings_call_toggle_title") + " (SIM " + mSubId + ")" );
            prefSuffix = "_sim" + mSubId;
            //  namespaceKeys(mScreen, prefSuffix);
        } else {
            getActivity().setTitle(R.string.glyph_settings_call_toggle_title);
        }

        mSwitchBar = (MainSwitchPreference) findPreference(Constants.GLYPH_CALL_SUB_ENABLE + prefSuffix);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(SettingsManager.isGlyphCallEnabled(Integer.parseInt(mSubId)));

        mListPreference = (ListPreference) findPreference(Constants.GLYPH_CALL_SUB_ANIMATIONS + prefSuffix);
        mListPreference.setOnPreferenceChangeListener(this);
        mListPreference.setEntries(ResourceUtils.getCallAnimations());
        mListPreference.setEntryValues(ResourceUtils.getCallAnimations());
        if (!ArrayUtils.contains(ResourceUtils.getCallAnimations(), mListPreference.getValue())) {
            mListPreference.setValue(ResourceUtils.getString("glyph_settings_call_animations_default"));
        }

        mLivePreviewPreference = (Preference) findPreference(Constants.GLYPH_CALL_SUB_LIVE_PREVIEW + prefSuffix);

        mReverseCallAnimationSwitch = findPreference(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE + prefSuffix);
        mReverseCallAnimationSwitch.setOnPreferenceChangeListener(this);

        mGlyphAnimationPreference = (GlyphAnimationPreference) findPreference(Constants.GLYPH_CALL_SUB_PREVIEW);
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGlyphAnimationPreference.updateAnimation(SettingsManager.isGlyphCallEnabled(Integer.parseInt(mSubId)),
                SettingsManager.getGlyphCallAnimation(Integer.parseInt(mSubId)), mReverseCallAnimationSwitch.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String preferenceKey = preference.getKey();

        if (preferenceKey.equals(Constants.GLYPH_CALL_SUB_ANIMATIONS + prefSuffix)) {
            mGlyphAnimationPreference.updateAnimation(SettingsManager.isGlyphCallEnabled(Integer.parseInt(mSubId)),
                newValue.toString());
        }

        if (preferenceKey.equals(Constants.GLYPH_CALL_REVERSE_ANIMATION_ENABLE + prefSuffix)) {
            mGlyphAnimationPreference.updateAnimation(SettingsManager.isGlyphCallEnabled(Integer.parseInt(mSubId)),
                    1500, (Boolean) newValue);
        }

        //mHandler.post(() -> ServiceUtils.checkGlyphService());

        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if ((Constants.GLYPH_CALL_SUB_LIVE_PREVIEW + prefSuffix).equals(preference.getKey())) {
            mLivePreviewPreference.setEnabled(false);
            mLivePreviewPreference.setSummary(R.string.glyph_settings_animations_live_preview_summary_playing);
            livePreviewThread = new Thread(() -> {
                Activity activity = getActivity();
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    resetLivePreview();
                    return;
                }
                AnimationManager.playCsv(
                        requireContext(),
                        SettingsManager.getGlyphCallAnimation(Integer.parseInt(mSubId)),
                        false,
                        mReverseCallAnimationSwitch.isChecked()
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
        SettingsManager.setGlyphCallEnabled(isChecked);
        ServiceUtils.checkGlyphService();
        mGlyphAnimationPreference.updateAnimation(isChecked,
                SettingsManager.getGlyphCallAnimation(Integer.parseInt(mSubId)));
    }

    @Override
    public void onResume() {
        super.onResume();
        resetLivePreview();
    }

    private void resetLivePreview() {
        mLivePreviewPreference.setEnabled(true);
        mLivePreviewPreference.setSummary(
                R.string.glyph_settings_animations_live_preview_summary
        );
    }

    private void namespaceKeys(PreferenceScreen screen, String suffix) {
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference p = screen.getPreference(i);

            Set<String> exclude = Set.of(Constants.GLYPH_CALL_SUB_PREVIEW);
            if (exclude.contains(p.getKey())) continue;

            if (p.getKey() != null) {
                p.setKey(p.getKey() + suffix);
            }

        }
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
