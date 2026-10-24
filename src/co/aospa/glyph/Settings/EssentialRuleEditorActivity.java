package co.aospa.glyph.Settings;

import androidx.fragment.app.Fragment;
import android.os.Bundle;

import co.aospa.glyph.Constants.Constants;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class EssentialRuleEditorActivity extends CollapsingToolbarBaseActivity {

    private EssentialRuleEditorFragment mEssentialRuleEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }

        String ruleId = getIntent().getStringExtra("ruleId");

        Bundle args = new Bundle();
        args.putString("ruleId", ruleId);

        Fragment fragment = getSupportFragmentManager().findFragmentById(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame
        );
        if (fragment == null) {
            mEssentialRuleEditor = new EssentialRuleEditorFragment();
            mEssentialRuleEditor.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .add(
                            com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            mEssentialRuleEditor
                    )
                    .commit();
        } else {
            mEssentialRuleEditor = (EssentialRuleEditorFragment) fragment;
        }
    }
}
