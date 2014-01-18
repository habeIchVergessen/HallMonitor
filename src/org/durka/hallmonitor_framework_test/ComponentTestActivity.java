package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

public class ComponentTestActivity extends Activity implements ComponentFramework.OnScreenOffTimerListener {

    private final String LOG_TAG = "ComponentTestActivity";

    private boolean mDebug = false;

    private ComponentFramework.Container mComponentContainer = null;
    private ComponentFramework.Layout mComponentDefault = null;
    private ComponentPhone mComponentPhone = null;

    @Override
    protected void onCreate(Bundle saveInstanceState) {
        super.onCreate(saveInstanceState);

        setContentView(R.layout.component_container_test);

        mComponentContainer = (ComponentFramework.Container)findViewById(R.id.componentContainer);
        mComponentDefault = mComponentContainer.getDefaultLayout();

        // turn debug on
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit().putBoolean("pref_dev_opts_debug", true).commit();

        Log_d(LOG_TAG, "onCreate: " + (mComponentContainer == null ? "null" : getResources().getResourceName(mComponentContainer.getId())) + ", " + (mComponentPhone == null ? "null" : getResources().getResourceName(mComponentPhone.getId())));
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log_d(LOG_TAG, "onStart");
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log_d(LOG_TAG, "onPause");

        // propagate to layout's
        mComponentContainer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log_d(LOG_TAG, "onResume");

        // propagate to layout's
        mComponentContainer.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log_d(LOG_TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        Log_d(LOG_TAG, "onDestroy");

        super.onDestroy();
    }

    public void onShowComponentPhone(View view) {
        ComponentFramework.Layout componentPhone = mComponentContainer.getLayoutByResId(R.id.componentPhone);

        if (componentPhone != null) {
            if (!componentPhone.isShown())
                componentPhone.setVisibility(View.VISIBLE);
            else
                componentPhone.setVisibility(View.INVISIBLE);
        }
    }

    public void onShowComponentCamera(View view) {
        ComponentFramework.Layout componentCamera = mComponentContainer.getLayoutByResId(R.id.componentCamera);

        if (componentCamera != null) {
            if (!componentCamera.isShown())
                componentCamera.setVisibility(View.VISIBLE);
            else
                componentCamera.setVisibility(View.INVISIBLE);
        }
    }

    public boolean onStartScreenOffTimer() {
        Log.d(LOG_TAG, "onStartScreenOffTimer");

        return true;
    }

    public boolean onStopScreenOffTimer() {
        Log.d(LOG_TAG, "onStopScreenOffTimer");

        return true;
    }

    // helper
    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }
}
