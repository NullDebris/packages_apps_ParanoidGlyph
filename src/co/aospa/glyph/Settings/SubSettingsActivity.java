package co.aospa.glyph.Settings;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import androidx.fragment.app.Fragment;

import co.aospa.glyph.Constants.Constants;


public class SubSettingsActivity extends CollapsingToolbarBaseActivity {

    public static final String ARGS_FRAGMENT = "fragment";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Constants.CONTEXT == null) {
            Constants.CONTEXT = getApplicationContext();
        }

        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            String fragmentClass = getIntent().getStringExtra(ARGS_FRAGMENT);
            if (fragmentClass == null) {
                finish();
            }
            if (fragmentClass.startsWith(".")) {
                fragmentClass = getPackageName() + fragmentClass;
            }
            Bundle args = getIntent().getBundleExtra("args");
            Fragment fragment = getSupportFragmentManager()
                    .getFragmentFactory()
                    .instantiate(getClassLoader(), fragmentClass);
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, fragment)
                    .commit();
        }
    }
}
