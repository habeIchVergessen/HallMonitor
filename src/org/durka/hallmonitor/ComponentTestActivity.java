package org.durka.hallmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class ComponentTestActivity extends Activity {

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log_d(LOG_TAG, "onResume");

        // set debug to all child views
        //setDebugMode(PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("pref_dev_opts_debug", false));
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

    // helper
    private void setDebugMode(boolean debugMode) {
        mDebug = debugMode;
        // provide to all child views
        if (mComponentContainer != null)
            mComponentContainer.setDebugMode(mDebug);
    }

    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }
}
