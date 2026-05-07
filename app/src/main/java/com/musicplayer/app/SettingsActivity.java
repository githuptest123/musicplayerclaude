package com.musicplayer.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.musicplayer.app.utils.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settingsContainer, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            SwitchPreferenceCompat themeSwitch = findPreference("dark_theme");
            if (themeSwitch != null) {
                PreferenceManager prefManager = new PreferenceManager(requireContext());
                themeSwitch.setChecked(prefManager.isDarkTheme());
                themeSwitch.setOnPreferenceChangeListener((pref, newVal) -> {
                    boolean dark = (Boolean) newVal;
                    prefManager.setDarkTheme(dark);
                    AppCompatDelegate.setDefaultNightMode(
                            dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                    requireActivity().recreate();
                    return true;
                });
            }

            EditTextPreference apiKeyPref = findPreference("openai_api_key");
            if (apiKeyPref != null) {
                apiKeyPref.setSummaryProvider(pref -> {
                    String key = ((EditTextPreference) pref).getText();
                    if (key == null || key.isEmpty()) return "Not set (required for auto lyrics)";
                    return "sk-..." + key.substring(Math.max(0, key.length() - 4));
                });
                apiKeyPref.setOnBindEditTextListener(editText ->
                    editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD));
            }
        }
    }
}
