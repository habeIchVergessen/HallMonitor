package org.durka.hallmonitor_framework_test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class ComponentTestActivity extends ComponentFramework.Activity
        implements ComponentFramework.OnScreenOffTimerListener , ComponentFramework.OnWakeUpScreenListener , ComponentFramework.OnKeepOnScreen
        , ComponentFramework.OnCoverStateChangedListener {

    private final String LOG_TAG = "ComponentTestActivity";

    private boolean mIsActivityPaused = true;
    private boolean mOnWakeUpScreenCalled = true;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // register receivers
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);

        registerReceiver(receiver, intentFilter);
        ViewCoverService.registerOnCoverStateChangedListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log_d(LOG_TAG, "onStart");
    }

    @Override
    protected void onResume() {
        Log_d(LOG_TAG, "onResume");
        mIsActivityPaused = false;
        onStartScreenOffTimer();

        super.onResume();
    }

    @Override
    protected void onPause() {
        Log_d(LOG_TAG, "onPause");
        super.onPause();

        onStopScreenOffTimer();
        mIsActivityPaused = true;
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
        ViewCoverService.unregisterOnCoverStateChangedListener(this);

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

        TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);

        // phone handles screen off timer
        if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
            Functions.Actions.rearmScreenOffTimer(getApplicationContext());
            return true;
        }

        return false;
    }

    public boolean onStopScreenOffTimer() {
        Log_d(LOG_TAG, "onStopScreenOffTimer");

        Functions.Actions.stopScreenOffTimer();
        return true;
    }

    /**
     * implement OnWakeUpScreenListener
     */
    @SuppressWarnings("deprecation")
    public void onWakeUpScreen() {
        PowerManager pm  = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        boolean isScreenOn = pm.isScreenOn();
        Log_d(LOG_TAG, "wakeUpScreen: " + isScreenOn);

        if (!isScreenOn) {
            //FIXME Would be nice to remove the deprecated FULL_WAKE_LOCK if possible
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, getApplicationContext().getString(R.string.app_name_framework));
            wl.acquire();
            wl.release();

            mOnWakeUpScreenCalled = true;
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
     * implement OnCoverStateChangedListener
     */
    public void onCoverStateChanged(boolean coverClosed) {
        Log_d(LOG_TAG, "onCoverStateChangedListener: " + coverClosed);

        if (coverClosed) {
            if (mIsActivityPaused)
                onKeepOnScreen(getContainer().getApplicationState());
        } else {
            finish();   //moveTaskToBack(true);
        }
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

                // don't handle software driven screen on actions
                if (mOnWakeUpScreenCalled) {
                    mOnWakeUpScreenCalled = false;
                    return;
                }

                // seems user has turned on the screen
                if (ViewCoverService.isCoverClosed()) {
                    if (mIsActivityPaused)
                        onKeepOnScreen(getContainer().getApplicationState());
                    else
                        onStartScreenOffTimer();
                } else {
                    onCoverStateChanged(false);
                }
            } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String phoneExtraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

                Log_d(LOG_TAG + ".onReceive", "ACTION_PHONE_STATE_CHANGED = " + mIsActivityPaused + ", " + phoneExtraState);
                // give ComponentPhone a chance to handle the call
                if (mIsActivityPaused && phoneExtraState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
//                    onKeepOnScreen(getContainer().getApplicationState(), 500);
                    onWakeUpScreen();
                }
            }
        }
    };
}
