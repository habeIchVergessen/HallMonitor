package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class ComponentTestActivity extends ComponentFramework.Activity implements ComponentFramework.OnScreenOffTimerListener, ComponentFramework.OnWakeUpScreenListener, ComponentFramework.OnKeepOnScreen {

    private final String LOG_TAG = "ComponentTestActivity";

    private boolean mIsActivityPaused = true;

    @Override
    protected void onCreate(Bundle saveInstanceState) {
        super.onCreate(saveInstanceState);

        //Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.component_container_test);

        if (getContainer() != null && getMenuController() != null)
            getMenuController().registerOnOpenListener(getContainer());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // register receivers
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);

        registerReceiver(receiver, intentFilter);
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
        mIsActivityPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log_d(LOG_TAG, "onResume");
        mIsActivityPaused = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log_d(LOG_TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        Log_d(LOG_TAG, "onDestroy");

        // unregister receivers
        unregisterReceiver(receiver);

        super.onDestroy();
    }

    /**
     * implement abstract members
     */
    public ComponentFramework.Container getContainer() {
        return (ComponentFramework.Container)findViewById(R.id.componentContainer);
    }

    public ComponentFramework.MenuController getMenuController() {
        return (ComponentFramework.MenuController)findViewById(R.id.componentMenu);
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

    /**
     * implement OnScreenOffTimerListener
     */
    public boolean onStartScreenOffTimer() {
        Log_d(LOG_TAG, "onStartScreenOffTimer");

        Functions.Actions.rearmScreenOffTimer(getApplicationContext());
        return true;
    }

    public boolean onStopScreenOffTimer() {
        Log_d(LOG_TAG, "onStopScreenOffTimer");

        Functions.Actions.stopScreenOffTimer();
        return true;
    }

    /**
     * implement OnWakeUpScreenListener
     */
    public void onWakeUpScreen() {
        PowerManager pm  = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (!pm.isScreenOn()) {
            Log.d(LOG_TAG, "wakeUpScreen");
            //FIXME Would be nice to remove the deprecated FULL_WAKE_LOCK if possible
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, getApplicationContext().getString(R.string.app_name_framework));
            wl.acquire();
            wl.release();
        }
    }

    /**
     * implement OnKeepOnScreen
     */
    public void onKeepOnScreen(final Bundle extras) {
        onKeepOnScreen(extras, 0);
    }

    public void onKeepOnScreen(final Bundle extras, int delay) {
        Log_d(LOG_TAG, "onKeepOnScreen: " + extras);

        Intent intent = new Intent();
        intent.setAction(getString(R.string.ACTION_RESTART_FRAMEWORK_TEST));
        if (extras != null)
            intent.putExtras(extras);
        if (delay > 0)
            intent.putExtra("restartDelay", delay);
        sendBroadcast(intent);
    }

    /**
     * receiver
     */
    // we need to kill this activity when the screen opens
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                Log_d(LOG_TAG + ".onReceive", "ACTION_SCREEN_ON = " + mIsActivityPaused);

                if (mIsActivityPaused)
                    onKeepOnScreen(getContainer().getApplicationState());
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String phoneExtraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

                Log_d(LOG_TAG + ".onReceive", "ACTION_PHONE_STATE_CHANGED = " + mIsActivityPaused + ", " + phoneExtraState);
                // give ComponentPhone a chance to handle the call
                if (mIsActivityPaused && phoneExtraState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                    onKeepOnScreen(getContainer().getApplicationState(), 500);
                }
            }
        }
    };

}