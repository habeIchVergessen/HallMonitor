package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class PhoneWidget extends RelativeLayout {
    private final String LOG_TAG = "PhoneWidget";

    public final static String INTENT_phoneWidgetShow = "phoneWidgetShow";
    public final static String INTENT_phoneWidgetIncomingNumber = "phoneWidgetIncomingNumber";
    public final static String INTENT_phoneWidgetInitialized = "phoneWidgetInitialized";
    public final static String INTENT_phoneWidgetTtsNotified = "phoneWidgetTtsNotified";

    private int mPhoneResourceId = R.layout.component_phone_habe_ich_vergessen_layout;
    private Context mContext = null;

    private OnScreenActionListener mLockScreenListener = null;

    private boolean mPreviewMode = false;

    /**
     *  phone widget
     */
    private GridLayout mPhoneWidget = null;
    protected TextView mCallerName;
    protected TextView mCallerNumber;
    protected View mAcceptButton;
    protected View mAcceptSlide;
    protected View mRejectButton;
    protected View mRejectSlide;
    protected MyPhoneStateListener mMyPhoneStateListener = null;

    private boolean mShowPhoneWidget = false;

    private boolean mViewNeedsReset = false;

    // drawing stuff
    private final static int mButtonMargin = 15; // designer use just 10dp; ode rendering issue
    private final static int mRedrawOffset = 10; // move min 10dp before redraw

    private int mActivePointerId = -1;

    // debug
    private boolean mDebug = DefaultActivity.isDebug();

    public PhoneWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        Log_d(LOG_TAG, "PhoneWidget: ");
        if (!isInEditMode())
            mDebug = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_dev_opts_debug", false);

        mContext = context;
        setId(R.id.phone_widget);

        setupLayout();
    }

    private boolean setupLayout() {
        Log_d(LOG_TAG, "setupLayout: ");
        if (mPhoneResourceId != -1 && !isInEditMode()) {
            // options
            mPhoneWidget = (GridLayout)getActivity().getLayoutInflater().inflate(R.layout.component_phone_habe_ich_vergessen_layout, this, false);
            mPhoneWidget.setBackgroundColor(0x00000000); // clear background color
            this.addView(mPhoneWidget);

            mCallerName = (TextView)findViewById(R.id.caller_name);
            mCallerNumber = (TextView)findViewById(R.id.caller_number);
            mAcceptButton = findViewById(R.id.call_accept_button);
            mAcceptSlide = findViewById(R.id.call_accept_slide);
            mRejectButton = findViewById(R.id.call_reject_button);
            mRejectSlide = findViewById(R.id.call_reject_slide);

            // init defaults
            setVisibility(INVISIBLE);
            resetPhoneWidgetMakeVisible();

            // load preferences
            mPhoneWidget.setBackgroundColor(getPrefBackgroundColor());
            mCallerName.setTextColor(getPrefForegroundColor());
            mCallerNumber.setTextColor(getPrefForegroundColor());
        }

        return (mPhoneWidget != null);
    }

    public void preview() {
        Log_d(LOG_TAG, "preview: ");
        mPreviewMode = true;

        // ask for number
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

        alert.setTitle(getPreviewTitle());
        alert.setMessage(getPreviewMessage());

        // Set an EditText view to get user input
        final EditText input = new EditText(getContext());
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                Log_d(LOG_TAG, "preview: value = '" + value + "'");

                getIntent().putExtra(INTENT_phoneWidgetIncomingNumber, value);
                open();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                getActivity().finish();
            }
        });

        alert.show();
    }

    public void open() {
        // refresh debug
        mDebug = DefaultActivity.isDebug();

        Log_d(LOG_TAG, "open: " + isInitialized() + ", " + (mPhoneWidget != null));
        if (mPhoneWidget == null)
            setupLayout();

        if (isInitialized())
            return;

        wakeUpScreen();
        stopScreenOffTimer();

        refreshDisplay();
    }

    public boolean isShowPhoneWidget() {
        return mShowPhoneWidget;
    }

    public void close() {
        // refresh debug
        mDebug = DefaultActivity.isDebug();

        Log_d(LOG_TAG, "close: ");

        if (!mShowPhoneWidget)
            return;

        setShowPhoneWidget(false);
        setVisibility(View.INVISIBLE);
        startScreenOffTimer();

        if (mPreviewMode)
            preview();
    }

    public void refreshDisplay() {
        // refresh debug
        mDebug = DefaultActivity.isDebug();

        Log_d(LOG_TAG, "refreshDisplay: " + mShowPhoneWidget);

        if (!mShowPhoneWidget) {
            resetPhoneWidgetMakeVisible();
            // parse parameter
            setIncomingNumber(getIntent().getStringExtra(PhoneWidget.INTENT_phoneWidgetIncomingNumber));
            setInitialized(true);

            // show phone widget
            setVisibility(View.VISIBLE);
            setShowPhoneWidget(true);

            ViewParent parent = getParent();
            parent.bringChildToFront(this);
            parent.requestLayout();
        }
    }

    public void registerOnLockScreenListener(OnScreenActionListener lockScreenListener) {
        Log_d(LOG_TAG, "registerOnLockScreenListener: " + lockScreenListener);
        mLockScreenListener = lockScreenListener;
    }

    public void unregisterOnLockScreenListener() {
        Log_d(LOG_TAG, "unregisterOnLockScreenListener: " + mLockScreenListener);
        mLockScreenListener = null;
    }

    public void registerPhoneStateListener() {
        Log_d(LOG_TAG, "registerPhoneStateListener: " + (mMyPhoneStateListener == null));
        if (mMyPhoneStateListener == null) {
            TelephonyManager telephonyManager = (TelephonyManager)getContext().getSystemService(getActivity().TELEPHONY_SERVICE);
            mMyPhoneStateListener = new MyPhoneStateListener();
            telephonyManager.listen(mMyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        }
    }

    public void unregisterPhoneStateListener() {
        Log_d(LOG_TAG, "unregisterPhoneStateListener: " + (mMyPhoneStateListener != null));
        if (mMyPhoneStateListener != null) {
            TelephonyManager telephonyManager = (TelephonyManager)getContext().getSystemService(getActivity().TELEPHONY_SERVICE);
            telephonyManager.listen(mMyPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    /*
     * internal helpers
     */
    private Intent getIntent() {
        return getActivity().getIntent();
    }

    private Activity getActivity() {
        return (Activity)mContext;
    }

    private int getPrefForegroundColor() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getInt("pref_default_fgcolor", 0xFFFFFFFF);
    }

    private int getPrefBackgroundColor() {
        return PreferenceManager.getDefaultSharedPreferences(getContext()).getInt("pref_default_bgcolor", 0xFF000000);
    }

    private String getPreviewTitle() {
        return getResources().getString(R.string.pref_preview) + " " + getResources().getString(R.string.pref_phone_screen);
    }

    private String getPreviewMessage() {
        return getResources().getString(R.string.pref_phone_preview_input_message);
    }

    private void wakeUpScreen() {
        Log_d(LOG_TAG, "wakeUpScreen: " + mLockScreenListener);
        if (mLockScreenListener != null)
            mLockScreenListener.onWakeUpScreen();
    }

    private void stopScreenOffTimer() {
        Log_d(LOG_TAG, "stopScreenOffTimer: " + mLockScreenListener);
        if (mLockScreenListener != null)
            mLockScreenListener.onWakeUpScreen();
    }

    private void startScreenOffTimer() {
        Log_d(LOG_TAG, "startScreenOffTimer: " + mLockScreenListener);
        if (mLockScreenListener != null)
            mLockScreenListener.onStartScreenOffTimer();
    }

    private boolean isInitialized() {
        return getIntent().getBooleanExtra(INTENT_phoneWidgetInitialized, false);
    }

    private void setInitialized(boolean initialized) {
        if (initialized)
            getIntent().putExtra(INTENT_phoneWidgetInitialized, true);
        else
            getIntent().removeExtra(INTENT_phoneWidgetInitialized);
    }

    private boolean isTtsNotified() {
        return getIntent().getBooleanExtra(INTENT_phoneWidgetTtsNotified, false);
    }

    private void setTtsNotified(boolean ttsNotified) {
        if (ttsNotified)
            getIntent().putExtra(INTENT_phoneWidgetTtsNotified, true);
        else
            getIntent().removeExtra(INTENT_phoneWidgetTtsNotified);
    }

    /**
     * Text-To-Speech
     */

    private void sendTextToSpeech(String text) {
        Log_d(LOG_TAG, "sendTextToSpeech: ");
        Intent intent = new Intent();
        intent.setAction(mContext.getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE));
        intent.putExtra("sendTextToSpeech", text);
        mContext.sendBroadcast(intent);
    }

    private void stopTextToSpeech() {
        Log_d(LOG_TAG, "stopTextToSpeech: ");
        Intent intent = new Intent();
        intent.setAction(mContext.getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE));
        mContext.sendBroadcast(intent);
    }

    /**
     * Text-To-Speech (end)
     */

    /**
     * phone widget stuff
     */

    public void initPhoneWidget() {
        Log_d(LOG_TAG, "initPhoneWidget: ");
        final TelephonyManager telephonyManager = (TelephonyManager)mContext.getSystemService(getActivity().TELEPHONY_SERVICE);

        // check phone state (if not invoked by intent)
        if (!getIntent().getBooleanExtra(INTENT_phoneWidgetShow, false)) {
            int callState = telephonyManager.getCallState();

            if (callState == TelephonyManager.CALL_STATE_RINGING || callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log_d(LOG_TAG, "initPhoneWidget: ringing/off hook detected");
                final String incomingNumber = "";
                //telephonyManager.notify();

                Log_d(LOG_TAG, "initPhoneWidget: number = '" + incomingNumber + "'");
                getIntent().putExtra(INTENT_phoneWidgetShow, true);
                getIntent().putExtra(INTENT_phoneWidgetIncomingNumber, incomingNumber);
                setTtsNotified(true);
                resetPhoneWidgetMakeVisible();
                setIncomingNumber(incomingNumber);

                if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    callAcceptedPhoneWidget(false);
                    setInitialized(true);
                }
            }
        }
    }

    private boolean setIncomingNumber(String incomingNumber) {
        Log_d(LOG_TAG, "incomingNumber: " + incomingNumber);

        boolean result = false;

        if (incomingNumber == null || incomingNumber.equals(""))
            return result;

        mCallerName.setText(incomingNumber);
        result = setDisplayNameByIncomingNumber(incomingNumber);

        return result;
    }

    private boolean setDisplayNameByIncomingNumber(String incomingNumber) {
        Log_d(LOG_TAG, "setDisplayNameByIncomingNumber: ");
        String name = null, type = null, label = null;
        Cursor contactLookup = null;

        try {
            contactLookup = ((Activity)mContext).getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber))
                    ,	new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.LABEL }
                    ,	null
                    ,	null
                    , 	null);

            if (contactLookup != null && contactLookup.getCount() > 0) {

                contactLookup.moveToFirst();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                type = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.TYPE));
                label = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.LABEL));
            }

            if (name != null) {
                String typeString = (String)ContactsContract.CommonDataKinds.Phone.getTypeLabel(this.getResources(), Integer.parseInt(type), "");

                mCallerName.setText(name);
                mCallerNumber.setText((typeString == null ? incomingNumber : typeString));

                Log_d(LOG_TAG, "displayName: " + name + " aka " + label + " (" + type + " -> " + typeString + ")");

                if (!isTtsNotified()) {
                    sendTextToSpeech(name + (typeString != null ? " " + typeString : ""));
                    setTtsNotified(true);
                }
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return (name != null);
    }

    public boolean onTouchEvent_PhoneWidgetHandler(MotionEvent motionEvent) {
        float maxSwipe = 150;
        float swipeTolerance = 0.95f;
        int defaultOffset = 10;

        // point handling
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        final int actionIndex = motionEvent.getActionIndex();
        final int actionMasked = motionEvent.getActionMasked();
        final int pointerId = actionIndex;
        int pointerIndex = -1;
        motionEvent.getPointerCoords(actionIndex, pointerCoords);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!TouchEventProcessor.isTracking()) {
                    // check accept button
                    if (mAcceptButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mAcceptButton);

                    // check reject button
                    if (mRejectButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mRejectButton);

                    if (TouchEventProcessor.isTracking()) {
                        mActivePointerId = pointerId;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int idx=0; idx < motionEvent.getPointerCount(); idx++)
                    if (motionEvent.getPointerId(idx) == mActivePointerId) {
                        pointerIndex = idx;
                        break;
                    }

                // process tracking
                if (TouchEventProcessor.isTracking() && pointerIndex != -1) {
                    motionEvent.getPointerCoords(pointerIndex, pointerCoords);

                    float dist = TouchEventProcessor.getHorizontalDistance(pointerCoords.x);

                    // check accept
                    if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist >= maxSwipe * swipeTolerance) {
                        callAcceptedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                        // animate accept
                        if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist > 0 && dist < maxSwipe)
                            moveCallButton(mAcceptButton, defaultOffset + Math.round(dist));

                    // modify negative dist
                    dist = Math.abs(dist);
                    // check rejected
                    if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist >= maxSwipe * swipeTolerance) {
                        callRejectedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                        // animate rejected
                        if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist > 0 && dist < maxSwipe)
                            moveCallButton(mRejectButton, defaultOffset + Math.round(dist));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mActivePointerId == -1 || (mActivePointerId != -1 && motionEvent.findPointerIndex(mActivePointerId) != actionIndex))
                    break;
            case MotionEvent.ACTION_UP:
                if (TouchEventProcessor.isTracking()) {
                    resetPhoneWidget();
                    TouchEventProcessor.stopTracking();
                    mActivePointerId = -1;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log_d(LOG_TAG + "-oTE", "CANCEL: never seen");
                callRejectedPhoneWidget();
                TouchEventProcessor.stopTracking();
                mActivePointerId = -1;
                break;
            default:
                break;
        }

        return true;
    }

    private void moveCallButton(View button, int offset) {
        if (!mAcceptButton.equals(button) && !mRejectButton.equals(button))
            return;

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(button.getLayoutParams());

        if (button.equals(mAcceptButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.leftMargin + mRedrawOffset)
                return;

            lp.setMargins(offset, 0, 0, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        }

        if (button.equals(mRejectButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.rightMargin + mRedrawOffset)
                return;

            lp.setMargins(0, 0, offset, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        }

        lp.addRule(RelativeLayout.CENTER_VERTICAL);
        button.setLayoutParams(lp);

        mViewNeedsReset = true;
    }

    private void setShowPhoneWidget(boolean show) {
        Log_d(LOG_TAG, "setShowPhoneWidget: " + show);
        mShowPhoneWidget = show;

        if (mShowPhoneWidget) {
            getIntent().putExtra(INTENT_phoneWidgetShow, show);
        } else { // clean up extra info's from intent
            getIntent().removeExtra(INTENT_phoneWidgetShow);
            getIntent().removeExtra(INTENT_phoneWidgetIncomingNumber);
            setInitialized(false);
            setTtsNotified(false);
        }
    }

    private void callAcceptedPhoneWidget() {
        callAcceptedPhoneWidget(true);
    }

    public void callAcceptedPhoneWidget(boolean needsSendPickup) {
        Log_d(LOG_TAG, "callAcceptedPhoneWidget");
        stopTextToSpeech();
        mAcceptButton.setVisibility(View.INVISIBLE);
        mAcceptSlide.setVisibility(View.INVISIBLE);
        resetPhoneWidget();
        if (needsSendPickup)
            sendPickUp();
    }

    public void callRejectedPhoneWidget() {
        callRejectedPhoneWidget(true);
    }

    private void callRejectedPhoneWidget(boolean needsSendHangup) {
        Log_d(LOG_TAG, "callRejectedPhoneWidget");

        stopTextToSpeech();
        resetPhoneWidgetMakeVisible();
        // rearm screen off timer
        Functions.Actions.rearmScreenOffTimer(mContext);
        if (needsSendHangup)
            sendHangUp();

        // cleanup intent & set invisible
        close();
    }

    private void resetPhoneWidget() {
        Log_d(LOG_TAG, "resetPhoneWidget: ");
        if (!mViewNeedsReset)
            return;

        Log_d(LOG_TAG, "resetPhoneWidget");
        moveCallButton(mAcceptButton, mButtonMargin);
        moveCallButton(mRejectButton, mButtonMargin);

        mViewNeedsReset = false;
    }

    private void resetPhoneWidgetMakeVisible() {
        Log_d(LOG_TAG, "resetPhoneWidgetMakeVisible");
        resetPhoneWidget();
        mAcceptButton.setVisibility(View.VISIBLE);
        mAcceptSlide.setVisibility(View.VISIBLE);
        mRejectButton.setVisibility(View.VISIBLE);
        mRejectSlide.setVisibility(View.VISIBLE);

        mCallerName.setText("");
        mCallerNumber.setText("");
    }

    private class  MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    Log_d(LOG_TAG, "onCallStateChanged: idle");
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log_d(LOG_TAG, "onCallStateChanged: off hook");
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    Log_d(LOG_TAG, "onCallStateChanged: ringing");
                    final boolean showPhoneWidget = getIntent().getBooleanExtra(INTENT_phoneWidgetShow, false);
                    final String intentIncomingNumber = getIntent().getStringExtra(INTENT_phoneWidgetIncomingNumber);

                    if (showPhoneWidget) {
                        Log_d(LOG_TAG, "onCallStateChanged: phoneWidget is open, number = '" + incomingNumber + "'");
                        if (intentIncomingNumber == null || intentIncomingNumber.equals("")) {
                            getIntent().putExtra(INTENT_phoneWidgetIncomingNumber, incomingNumber);
                            setIncomingNumber(incomingNumber);
                        }
                    } /*else {
                        Log_d(LOG_TAG, "onCallStateChanged: phoneWidget is closed, number = '" + incomingNumber + "'");
                        getIntent().putExtra(INTENT_phoneWidgetShow, true);
                        getIntent().putExtra(INTENT_phoneWidgetIncomingNumber, incomingNumber);
                        initPhoneWidget();
                    }*/
                    break;
            }
        }
    }

    private static class TouchEventProcessor {
        private static View mTrackObj = null;
        private static Point mTrackStartPoint;

        private final static int mHitRectBoost = 20;

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view) {
            return pointerInRect(pointer, view, mHitRectBoost);
        }

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view, int hitRectBoost) {
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);
            // circle is tangent to edges
            double radius = rect.centerX() - rect.left;

            int extraSnap = (isTrackedObj(view) || !isTracking() ? hitRectBoost : 0);
            //return (pointer.x >= rect.left - extraSnap && pointer.x <= rect.right + extraSnap && pointer.y >= rect.top - extraSnap && pointer.y <= rect.bottom + extraSnap);
            return (Math.sqrt(Math.pow(rect.centerX() - pointer.x, 2) + Math.pow(rect.centerY() - pointer.y, 2)) <= radius + extraSnap);
        }

        public static boolean isTracking() {
            //Log_d(LOG_TAG + ".TouchEventProcessor", "isTracking: " + (mTrackObj != null));
            return (mTrackObj != null);
        }

        public static boolean isTrackedObj(View view) {
            //Log_d(LOG_TAG + ".TouchEventProcessor", "isTrackedObj: " + (isTracking() && mTrackObj.equals(view)));
            return (isTracking() && mTrackObj.equals(view));
        }

        public static void startTracking(View view) {
            //Log_d(LOG_TAG + ".TouchEventProcessor", "startTracking: " + view.getId());
            mTrackObj = view;

            Rect mRect = new Rect();
            view.getGlobalVisibleRect(mRect);

            mTrackStartPoint = new Point(mRect.centerX(), mRect.centerY());
        }

        public static void stopTracking() {
            //Log_d(LOG_TAG + ".TouchEventProcessor", "stopTracking");
            mTrackObj = null;
            mTrackStartPoint = null;
        }

        public static float getHorizontalDistance(float currentX) {
            return currentX - mTrackStartPoint.x;
        }
    }

    /**
     * phone widget stuff (end)
     */

    private void sendHangUp() {
        Log_d(LOG_TAG, "sendHangUp: ");
        if (!mPreviewMode)
            Functions.Actions.hangup_call();
    }

    private void sendPickUp() {
        Log_d(LOG_TAG, "sendPickUp: ");
        if (!mPreviewMode)
            Functions.Actions.pickup_call();
    }

    private void Log_d(String tag, String msg) {
        if (mDebug)
            Log.d(tag, msg);
    }
}
