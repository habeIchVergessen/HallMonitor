package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;

public class ComponentTestActivity extends ComponentFramework.Activity implements ComponentFramework.OnScreenOffTimerListener {

    private final String LOG_TAG = "ComponentTestActivity";

    @Override
    protected void onCreate(Bundle saveInstanceState) {
        super.onCreate(saveInstanceState);

        setContentView(R.layout.component_container_test);

        if (getContainer() != null && getMenu() != null)
            getMenu().registerOnMenuOpenListener(getContainer());
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

    public ComponentFramework.Container getContainer() {
        return (ComponentFramework.Container)findViewById(R.id.componentContainer);
    }

    public ComponentFramework.Menu getMenu() {
        return (ComponentFramework.Menu)findViewById(R.id.componentMenu);
    }

    public void onShowComponentPhone(View view) {
        ComponentFramework.Layout componentPhone = getContainer().getLayoutByResId(R.id.componentPhone);

        if (componentPhone != null) {
            if (!componentPhone.isShown())
                componentPhone.setVisibility(View.VISIBLE);
            else
                componentPhone.setVisibility(View.INVISIBLE);
        }
    }

    public void onShowComponentCamera(View view) {
        ComponentFramework.Layout componentCamera = getContainer().getLayoutByResId(R.id.componentCamera);

        if (componentCamera != null) {
            if (!componentCamera.isShown())
                componentCamera.setVisibility(View.VISIBLE);
            else
                componentCamera.setVisibility(View.INVISIBLE);
        }
    }

    public boolean onStartScreenOffTimer() {
        Log_d(LOG_TAG, "onStartScreenOffTimer");

        return true;
    }

    public boolean onStopScreenOffTimer() {
        Log_d(LOG_TAG, "onStopScreenOffTimer");

        return true;
    }

}
