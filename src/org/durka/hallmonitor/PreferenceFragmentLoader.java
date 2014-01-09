package org.durka.hallmonitor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by ladmin on 07.01.14.
 */
public class PreferenceFragmentLoader extends PreferenceFragment  implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final String LOG_TAG = "PreferenceFragmentLoader";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            final String resourceName = getArguments().getString("resource", "");

            Context context = getActivity().getApplicationContext();
            final int resourceId = context.getResources().getIdentifier(resourceName, "xml", context.getPackageName());

            PreferenceManager.setDefaultValues(getActivity(), resourceId, false);
            addPreferencesFromResource(resourceId);
        } catch (Exception e) {
            Log.d(LOG_TAG, "onCreate: exception occurred! " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume: ");
        // close pref_phone_screen preferenceScreen
//        try {
//            ((PreferenceScreen)getPreferenceScreen().findPreference("pref_phone_screen")).getDialog().dismiss();
//        } catch (Exception e) {
//            ;
//        }

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        prefs.edit()
            .putBoolean("pref_enabled", Functions.Is.service_running(getActivity()))
            .putBoolean("pref_default_widget_enabled", Functions.Is.widget_enabled(getActivity(), "default"))
            .putBoolean("pref_media_widget_enabled", Functions.Is.widget_enabled(getActivity(), "media"))
            .commit();

        prefs.registerOnSharedPreferenceChangeListener(this);

        // phone control
        enablePhoneScreen(prefs);
        updatePhoneControlTtsDelay(prefs);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause: ");
        // don't unregister, because we still want to receive the notification when
        // pref_enabled is changed in onActivityResult
        // FIXME is it okay to just never unregister??
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.d(LOG_TAG + "-oSPC", "changed key " + key);

        // update display
        if (findPreference(key) instanceof CheckBoxPreference) {
            ((CheckBoxPreference)findPreference(key)).setChecked(prefs.getBoolean(key, false));
        }

        // if the service is being enabled/disabled the key will be pref_enabled
        if (key.equals("pref_enabled")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.start_service(getActivity());
            } else {
                Functions.Actions.stop_service(getActivity());
            }

            // if the default screen widget is being enabled/disabled the key will be pref_default_widget
        } else if (key.equals("pref_default_widget")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.register_widget(getActivity(), "default");
            } else {
                Functions.Actions.unregister_widget(getActivity(), "default");
            }

            // if the media screen widget is being enabled/disabled the key will be pref_media_widget
        } else if (key.equals("pref_media_widget")) {

            if (prefs.getBoolean(key, false)) {
                Functions.Actions.register_widget(getActivity(), "media");
            } else {
                Functions.Actions.unregister_widget(getActivity(), "media");
            }

            // if the default screen widget is being enabled/disabled the key will be pref_widget
        } else if (key.equals("pref_runasroot")) {

            if (prefs.getBoolean(key, false)) {
                if (!Functions.Actions.run_commands_as_root(new String[]{"whoami"}).equals("root")) {
                    // if "whoami" doesn't work, refuse to set preference
                    Toast.makeText(getActivity(), "Root access not granted - cannot enable root features!", Toast.LENGTH_SHORT).show();
                    prefs.edit().putBoolean(key, false).commit();
                }
            }

        } else if (key.equals("pref_do_notifications")) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            if (prefs.getBoolean(key, false)) {
                Toast.makeText(getActivity(), "check this box then", Toast.LENGTH_SHORT).show();
                //getActivity().startService(new Intent(getActivity(), NotificationService.class));
            } else {
                Toast.makeText(getActivity(), "okay uncheck the box", Toast.LENGTH_SHORT).show();
                //getActivity().startService(new Intent(getActivity(), NotificationService.class));
            }
            // if the flash controls are being enabled/disabled the key will be pref_widget
        } else if (key.equals("pref_flash_controls")) {

                if (prefs.getBoolean(key, false) ) {
                    try {
                        PackageManager packageManager = getActivity().getPackageManager();
                        packageManager.getApplicationLogo("net.cactii.flash2");
                    } catch (PackageManager.NameNotFoundException nfne) {
                        // if the app isn't installed, just refuse to set the preference
                        Toast.makeText(getActivity(), "Default torch application is not installed - cannot enable torch button!", Toast.LENGTH_SHORT).show();
                        prefs.edit().putBoolean(key, false).commit();
                    }
                }
            // preferences_phone
        } else if (key.equals("pref_phone_controls_tts_delay")) {
            updatePhoneControlTtsDelay(prefs);
        }

        // phone control
        enablePhoneScreen(prefs);
    }

    private void updatePhoneControlTtsDelay(SharedPreferences prefs) {
        Preference preference = findPreference("pref_phone_controls_tts_delay");

        if (preference != null && (preference instanceof ListPreference)) {
            preference.setSummary(((ListPreference)preference).getEntry());
        }
    }

    private void enablePhoneScreen(SharedPreferences prefs) {
        boolean phoneControlState = prefs.getBoolean("pref_enabled", false) && prefs.getBoolean("pref_runasroot", false) && prefs.getBoolean("pref_phone_controls_user", false);
        boolean phoneControlConfig = prefs.getBoolean("pref_phone_controls", false);
        Preference phoneControl = findPreference("pref_phone_controls_user");

        if (phoneControlConfig != phoneControlState) {
            if (phoneControl != null)
                phoneControl.setEnabled(phoneControlState);
            prefs.edit().putBoolean("pref_phone_controls", phoneControlState).commit();
        }
    }

}
