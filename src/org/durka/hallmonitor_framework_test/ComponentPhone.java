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
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ComponentPhone extends ComponentFramework.Layout implements ComponentFramework.OnPreviewComponentListener {

    private final String LOG_TAG = "ComponentPhone";
    private final String mPreviewName = "phoneWidget";

    private boolean mInitialized = false;

    public final static String INTENT_phoneWidgetShow = "phoneWidgetShow";
    public final static String INTENT_phoneWidgetIncomingNumber = "phoneWidgetIncomingNumber";
    public final static String INTENT_phoneWidgetInitialized = "phoneWidgetInitialized";
    public final static String INTENT_phoneWidgetTtsNotified = "phoneWidgetTtsNotified";

    private TextView mCallerName;
    private TextView mCallerNumber;
    private View mAcceptButton;
    private View mAcceptSlide;
    private View mRejectButton;
    private View mRejectSlide;

    private boolean mShowPhoneWidget = false;
    private boolean mViewNeedsReset = false;
    private boolean mPreviewMode = false;

    // drawing stuff
    private final static int mButtonMargin = 15; // designer use just 10dp; ode rendering issue
    private final static int mRedrawOffset = 10; // move min 10dp before redraw

    private final int UNDEFINED_TOUCH_EVENT_ACTION_INDEX = -1;
    private int mActivePointerId = UNDEFINED_TOUCH_EVENT_ACTION_INDEX;

    public ComponentPhone(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        Log_d(LOG_TAG, "ComponentPhone");

        // setup layout resource id (if not loaded via styled attrs)
        if (mLayoutResId == UNDEFINED_LAYOUT) {
            Log_d(LOG_TAG, "setup layout resource id");
            setLayoutResourceId(R.layout.component_phone_habe_ich_vergessen_layout);
        }
    }

    protected void onInitComponent() {
        mInitialized = true;
        Log_d(LOG_TAG, "onInitComponent");

        mCallerName = (TextView)findViewById(R.id.caller_name);
        mCallerNumber = (TextView)findViewById(R.id.caller_number);
        mAcceptButton = findViewById(R.id.call_accept_button);
        mAcceptSlide = findViewById(R.id.call_accept_slide);
        mRejectButton = findViewById(R.id.call_reject_button);
        mRejectSlide = findViewById(R.id.call_reject_slide);
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        int bgColor = getPrefInt("pref_default_bgcolor", 0xff000000);

        setShowPhoneWidget(true);

        if (mPreviewMode) {
            preview();
            return true;
        }

        stopScreenOffTimer();

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");

        setShowPhoneWidget(false);

        if (mPreviewMode)
            return;

        startScreenOffTimer();
    }

    public boolean onPreviewComponent() {
        String previewMode = getContainer().getPreviewMode();

        mPreviewMode = (previewMode != null && (previewMode.equals("*") || previewMode.equals(mPreviewName)));
        Log_d(LOG_TAG, "onPreviewComponent: " + mPreviewMode + ", '" + previewMode + "'");

        return (mPreviewMode && previewMode.equals(mPreviewName));
    }

    private void preview() {
        Log_d(LOG_TAG, "preview: ");

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

                getContainer().getApplicationState().putString(INTENT_phoneWidgetIncomingNumber, value);
                resetPhoneWidgetMakeVisible();
                setIncomingNumber(value);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (getContainer().getPreviewMode().equals(mPreviewName))
                    getActivity().finish();
                else
                    setVisibility(INVISIBLE);
            }
        });

        alert.show();
    }

    private String getPreviewTitle() {
        return getResources().getString(R.string.pref_preview) + " " + getResources().getString(R.string.pref_phone_screen);
    }

    private String getPreviewMessage() {
        return getResources().getString(R.string.pref_phone_preview_input_message);
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

    private void initPhoneWidget() {
        Log_d(LOG_TAG, "initPhoneWidget: ");
        final TelephonyManager telephonyManager = (TelephonyManager)mContext.getSystemService(getActivity().TELEPHONY_SERVICE);

        // check phone state (if not invoked by intent)
        if (!getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetShow, false)) {
            int callState = telephonyManager.getCallState();

            if (callState == TelephonyManager.CALL_STATE_RINGING || callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log_d(LOG_TAG, "initPhoneWidget: ringing/off hook detected");
                final String incomingNumber = "";
                //telephonyManager.notify();

                Log_d(LOG_TAG, "initPhoneWidget: number = '" + incomingNumber + "'");
                getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetShow, true);
                getContainer().getApplicationState().putString(INTENT_phoneWidgetIncomingNumber, incomingNumber);
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
            contactLookup = getActivity().getContentResolver().query(
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
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetShow, show);
        } else { // clean up extra info's from intent
            getContainer().getApplicationState().remove(INTENT_phoneWidgetShow);
            getContainer().getApplicationState().remove(INTENT_phoneWidgetIncomingNumber);
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

        // preview
        if (mPreviewMode) {
            preview();
            return;
        }

        if (needsSendHangup)
            sendHangUp();

        // cleanup intent & set invisible
        setVisibility(INVISIBLE);
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

    private boolean isInitialized() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetInitialized, false);
    }

    private void setInitialized(boolean initialized) {
        if (initialized)
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetInitialized, true);
        else
            getContainer().getApplicationState().remove(INTENT_phoneWidgetInitialized);
    }

    private boolean isTtsNotified() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetTtsNotified, false);
    }

    private void setTtsNotified(boolean ttsNotified) {
        if (ttsNotified)
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetTtsNotified, true);
        else
            getContainer().getApplicationState().remove(INTENT_phoneWidgetTtsNotified);
    }

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

    /**
     * phone widget stuff (end)
     */

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        float maxSwipe = 150;
        float swipeTolerance = 0.95f;
        int defaultOffset = 10;

        boolean result = false;

        // point handling
        final int actionIndex = motionEvent.getActionIndex();
        final int actionMasked = motionEvent.getActionMasked();
        final int pointerId = actionIndex;
        int pointerIndex = UNDEFINED_TOUCH_EVENT_ACTION_INDEX;

        // override with absolute coordinates
        Rect containerOffset = new Rect();
        getContainer().getGlobalVisibleRect(containerOffset);
        // coordinates
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        motionEvent.getPointerCoords(actionIndex, pointerCoords);
        // add offset to absolute coordinates
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, pointerCoords.x + containerOffset.left);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, pointerCoords.y + containerOffset.top);

//        Log_d(LOG_TAG, "onTouchEvent: " + actionMasked + ", " + pointerCoords.x + ":" + pointerCoords.y);
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                Log_d(LOG_TAG, "onTouchEvent: DOWN|POINTER_DOWN " + actionMasked + ", " + pointerCoords.x + ":" + pointerCoords.y + ", " + TouchEventProcessor.isTracking() + ", " + TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton) + ", " + TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton));
                if (!TouchEventProcessor.isTracking()) {
                    // check accept button
                    if (mAcceptButton.isShown() && TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mAcceptButton);

                    // check reject button
                    if (mRejectButton.isShown() && TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mRejectButton);

                    if ((result = TouchEventProcessor.isTracking())) {
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
                if (TouchEventProcessor.isTracking() && pointerIndex != UNDEFINED_TOUCH_EVENT_ACTION_INDEX) {
                    result = true;
                    motionEvent.getPointerCoords(pointerIndex, pointerCoords);

                    float dist = TouchEventProcessor.getHorizontalDistance(pointerCoords.x);

                    // check accept
                    if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist >= maxSwipe * swipeTolerance) {
                        callAcceptedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = UNDEFINED_TOUCH_EVENT_ACTION_INDEX;
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
                        mActivePointerId = UNDEFINED_TOUCH_EVENT_ACTION_INDEX;
                    } else
                        // animate rejected
                        if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist > 0 && dist < maxSwipe)
                            moveCallButton(mRejectButton, defaultOffset + Math.round(dist));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mActivePointerId == UNDEFINED_TOUCH_EVENT_ACTION_INDEX || (mActivePointerId != UNDEFINED_TOUCH_EVENT_ACTION_INDEX && motionEvent.findPointerIndex(mActivePointerId) != actionIndex))
                    break;
            case MotionEvent.ACTION_UP:
                if (TouchEventProcessor.isTracking()) {
                    result = true;
                    resetPhoneWidget();
                    TouchEventProcessor.stopTracking();
                    mActivePointerId = UNDEFINED_TOUCH_EVENT_ACTION_INDEX;
                }
                break;
        }

        return true; //result;
    }

    private static class TouchEventProcessor {

        private static final String LOG_TAG = "TouchEventProcessor";
        private static boolean mDebug = false;

        private static View mTrackObj = null;
        private static Point mTrackStartPoint;

        private final static int mHitRectBoost = 20;

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view) {
            return pointerInRect(pointer, view, mHitRectBoost);
        }

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view, int hitRectBoost) {
            boolean result = false;
            Rect rect = new Rect();
            Point offset = new Point();
            view.getGlobalVisibleRect(rect, offset);
            // circle is tangent to edges
            double radius = rect.centerX() - rect.left;

            Log_d(LOG_TAG, "pointerInRect: " + pointer.x + ":" + pointer.y + ", " + offset + ", " + rect + ", " + radius);

            int extraSnap = (isTrackedObj(view) || !isTracking() ? hitRectBoost : 0);
            //return (pointer.x >= rect.left - extraSnap && pointer.x <= rect.right + extraSnap && pointer.y >= rect.top - extraSnap && pointer.y <= rect.bottom + extraSnap);
            result = (Math.sqrt(Math.pow(rect.centerX() - pointer.x, 2) + Math.pow(rect.centerY() - pointer.y, 2)) <= radius + extraSnap);

            Log_d(LOG_TAG, "pointerInRect: " + result);
            return result;
        }

        public static boolean isTracking() {
            Log_d(LOG_TAG + ".TouchEventProcessor", "isTracking: " + (mTrackObj != null));
            return (mTrackObj != null);
        }

        public static boolean isTrackedObj(View view) {
            Log_d(LOG_TAG + ".TouchEventProcessor", "isTrackedObj: " + (isTracking() && mTrackObj.equals(view)));
            return (isTracking() && mTrackObj.equals(view));
        }

        public static void startTracking(View view) {
            Log_d(LOG_TAG + ".TouchEventProcessor", "startTracking: " + view.getId());
            mTrackObj = view;

            Rect mRect = new Rect();
            view.getGlobalVisibleRect(mRect);

            mTrackStartPoint = new Point(mRect.centerX(), mRect.centerY());
        }

        public static void stopTracking() {
            Log_d(LOG_TAG + ".TouchEventProcessor", "stopTracking");
            mTrackObj = null;
            mTrackStartPoint = null;
        }

        public static float getHorizontalDistance(float currentX) {
            return currentX - mTrackStartPoint.x;
        }

        private static void Log_d(String tag, String message) {
            if (mDebug)
                Log.d(tag, message);
        }
    }


}
