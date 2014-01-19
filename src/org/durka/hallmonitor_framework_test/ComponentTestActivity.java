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

public class ComponentTestActivity extends Activity implements ComponentFramework.OnScreenOffTimerListener {

    private final String LOG_TAG = "ComponentTestActivity";

    private boolean mDebug = false;

    private HashSet<Integer> mTrackedTouchEvent = new HashSet<Integer>();
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

        // propagate to layout's
        mComponentContainer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log_d(LOG_TAG, "onResume");
        mDebug = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_dev_opts_debug", false);

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

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        final int actionIndex = motionEvent.getActionIndex(), actionMasked = motionEvent.getActionMasked();
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        motionEvent.getPointerCoords(actionIndex, pointerCoords);

        Rect visibleRect = new Rect();
        mComponentContainer.getGlobalVisibleRect(visibleRect);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                Log_d(LOG_TAG, "dispatchTouchEvent: DOWN, " + actionIndex + ", " + pointerCoords.x + ":" + pointerCoords.y + ", " + visibleRect.contains((int)pointerCoords.x, (int)pointerCoords.y));
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                Log_d(LOG_TAG, "dispatchTouchEvent: POINTER_DOWN, " + actionIndex + ", " + pointerCoords.x + ":" + pointerCoords.y + ", " + visibleRect.contains((int)pointerCoords.x, (int)pointerCoords.y));
                break;
            case MotionEvent.ACTION_MOVE:
//                Log_d(LOG_TAG, "dispatchTouchEvent: MOVE, " + actionIndex);
                break;
            case MotionEvent.ACTION_UP:
                Log_d(LOG_TAG, "dispatchTouchEvent: UP, " + actionIndex + ", " + pointerCoords.x + ":" + pointerCoords.y);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                Log_d(LOG_TAG, "dispatchTouchEvent: POINTER_UP, " + actionIndex + ", " + pointerCoords.x + ":" + pointerCoords.y);
                break;
            case MotionEvent.ACTION_CANCEL:
                Log_d(LOG_TAG, "dispatchTouchEvent: CANCEL, " + actionIndex);
                break;
            default:
                Log_d(LOG_TAG, "dispatchTouchEvent: " + actionMasked + ", " + actionIndex);
                break;
        }

        // container
        if ( // start new tracking when down and matches container
             (visibleRect.contains((int)pointerCoords.x, (int)pointerCoords.y) && (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN)) ||
            // or already tracked and not down
            (mTrackedTouchEvent.contains(actionIndex) && actionMasked != MotionEvent.ACTION_DOWN && actionMasked != MotionEvent.ACTION_POINTER_DOWN)
           ) {
            // calc relative coordinates (action bar & notification bar!!!) and dispatch
            MotionEvent dispatch = MotionEvent.obtain(motionEvent);
            dispatch.setLocation(pointerCoords.x - visibleRect.left + mComponentContainer.getLeft(), pointerCoords.y - visibleRect.top + mComponentContainer.getTop());
            boolean result = false;
            result = mComponentContainer.dispatchTouchEvent(dispatch);
            dispatch.recycle();

            // start tracking
            if (result && (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN)) {
                Log_d(LOG_TAG, "dispatchTouchEvent: start tracking #" + actionIndex);
                mTrackedTouchEvent.add(actionIndex);
            }
        }

        // enable touch events outside container
        if (!mTrackedTouchEvent.contains(actionIndex))
            super.dispatchTouchEvent(motionEvent);

        // stop tracking
        if (mTrackedTouchEvent.contains(actionIndex) && (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_POINTER_UP || actionMasked == MotionEvent.ACTION_CANCEL)) {
            Log_d(LOG_TAG, "dispatchTouchEvent: stop tracking #" + motionEvent.getActionIndex());
            mTrackedTouchEvent.remove(motionEvent.getActionIndex());
        }

        // enable processing of all touch events
        return true;
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
        Log_d(LOG_TAG, "onStartScreenOffTimer");

        return true;
    }

    public boolean onStopScreenOffTimer() {
        Log_d(LOG_TAG, "onStopScreenOffTimer");

        return true;
    }

    // helper
    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }
}
