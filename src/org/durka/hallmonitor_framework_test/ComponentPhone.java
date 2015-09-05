package org.durka.hallmonitor_framework_test;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ComponentPhone extends ComponentFramework.Layout
        implements ComponentFramework.OnPreviewComponentListener, ComponentFramework.OnPauseResumeListener,
        ComponentFramework.MenuController.OnMenuActionListener, ComponentFramework.OnGyroscopeChangedListener {

    private final String LOG_TAG = "ComponentPhone";
    private final String mPreviewName = "phoneWidget";

    private boolean mInitialized = false;

    private final static String INTENT_phoneWidgetShow = "phoneWidgetShow";
    private final static String INTENT_phoneWidgetIncomingNumber = "phoneWidgetIncomingNumber";
    private final static String INTENT_phoneWidgetInitialized = "phoneWidgetInitialized";
    private final static String INTENT_phoneWidgetTtsNotified = "phoneWidgetTtsNotified";
    private final static String INTENT_phoneWidgetRestartForced = "phoneWidgetRestartForced";

    private TextView mCallerName;
    private TextView mCallerNumber;
    private View mAcceptButton;
    private View mAcceptSlide;
    private View mRejectButton;
    private View mRejectSlide;

    private boolean mShowPhoneWidget = false;
    private boolean mViewNeedsReset = false;
    private boolean mPreviewMode = false;

    private ImplPhoneStateListener mImplPhoneStateListener = null;

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

        mImplPhoneStateListener = new ImplPhoneStateListener();
    }

    /**
     * implement abstract methods
     */
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

        if (!isPhoneShow() && mPreviewMode)
            preview();

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");

        if (mPreviewMode) {
            setGyroscopeListener(false);
            getActivity().finish();
        }
    }

    /**
     * implement OnPreviewComponentListener
     */
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

                //resetPhoneWidgetMakeVisible();
                setIncomingNumber(value);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log_d(LOG_TAG, "preview: cancel = '" + getContainer().getPreviewMode() + "'");
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
     * implement OnPauseResumeListener
     */
    public void onResume() {
        Log_d(LOG_TAG, "onResume");

        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(getActivity().TELEPHONY_SERVICE);
        telephonyManager.listen(mImplPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);

        initPhoneWidget();
    }

    public void onPause() {
        Log_d(LOG_TAG, "onPause");

        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(getActivity().TELEPHONY_SERVICE);
        telephonyManager.listen(mImplPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        if (isPhoneShow() && ViewCoverService.isCoverClosed()) {
            Log_d(LOG_TAG, "force restart (overdrive telephone manager)");
            forceRestart();
        }
    }

    /**
     * implement OnGyroscopeChangedListener
     */
    public void onGyroscopeChanged() {
        Log_d(LOG_TAG, "onGyroscopeChanged: ");

        if (ComponentFramework.OnWakeUpScreenListener.class.isAssignableFrom(getActivity().getClass()))
            ((ComponentFramework.OnWakeUpScreenListener)getActivity()).onWakeUpScreen();
    }

    protected void setGyroscopeListener(boolean enabled) {
        boolean useGyroscope = getPrefBoolean("pref_gyroscope_enabled", true);
        Log_d(LOG_TAG, "setGyroscopeListener: " + enabled + ", " + useGyroscope);

        if (!useGyroscope)
            return;

        if (enabled)
            ViewCoverService.registerOnGyroscopeChangedListener(this);
        else
            ViewCoverService.unregisterOnGyroscopeChangedListener(this);
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

    private synchronized void initPhoneWidget() {
        Log_d(LOG_TAG, "initPhoneWidget: enter");
        final TelephonyManager telephonyManager = (TelephonyManager)mContext.getSystemService(getActivity().TELEPHONY_SERVICE);

        // check phone state (if not invoked by intent)
        if (!isPhoneShow() || getVisibility() != VISIBLE) {
            int callState = telephonyManager.getCallState();

            if (callState == TelephonyManager.CALL_STATE_RINGING || callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log_d(LOG_TAG, "initPhoneWidget: ringing/off hook detected");
                String incomingNumber = getIncomingNumber();

                if (callState == TelephonyManager.CALL_STATE_OFFHOOK && (incomingNumber == null || incomingNumber.equals(""))) {
/* doesn't work properly yet
                    String lastCall = CallLog.Calls.getLastOutgoingCall(getActivity());
                    if (lastCall != null && !lastCall.equals("")) {
                        Log_d(LOG_TAG, "getIncomingNumber: using getLastOutgoingCall() -> " + lastCall);

                        incomingNumber = lastCall;
                        // no text to speech
                        setPhoneTtsNotified(true);
                    }
*/
                }

                Log_d(LOG_TAG, "initPhoneWidget: number = '" + incomingNumber + "'");
                setPhoneShow(true);
                getContainer().getApplicationState().putString(INTENT_phoneWidgetIncomingNumber, incomingNumber);
                setVisibility(VISIBLE);
                resetPhoneWidgetMakeVisible();
                setIncomingNumber(incomingNumber);

                if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    callAcceptedPhoneWidget(false);
                    setPhoneInitialized(true);
                    setGyroscopeListener(true);
                    stopScreenOffTimer();
                }
            }
        }
        Log_d(LOG_TAG, "initPhoneWidget: leave");
    }

    private boolean setIncomingNumber(String incomingNumber) {
        Log_d(LOG_TAG, "incomingNumber: " + incomingNumber);

        boolean result = false;

        if (incomingNumber == null || incomingNumber.equals(""))
            return result;

        mCallerName.setText(incomingNumber);
        result = setDisplayNameByIncomingNumber(incomingNumber);
        getContainer().getApplicationState().putString(INTENT_phoneWidgetIncomingNumber, incomingNumber);

        return result;
    }

    private String getIncomingNumber() {
        return getContainer().getApplicationState().getString(INTENT_phoneWidgetIncomingNumber, "");
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

                if (!isPhoneTtsNotified()) {
                    sendTextToSpeech(name + (typeString != null ? " " + typeString : ""));
                    setPhoneTtsNotified(true);
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

    private boolean isPhoneShow() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetShow, false);
    }

    private void setPhoneShow(boolean show) {
        Log_d(LOG_TAG, "setShowPhoneWidget: " + show);
        mShowPhoneWidget = show;

        if (mShowPhoneWidget) {
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetShow, show);
        } else { // clean up extra info's from intent
            getContainer().getApplicationState().remove(INTENT_phoneWidgetShow);
            getContainer().getApplicationState().remove(INTENT_phoneWidgetIncomingNumber);
            setPhoneInitialized(false);
            setPhoneTtsNotified(false);
            setPhoneRestartForced(false);
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

        if (needsSendHangup)
            sendHangUp();

        // cleanup intent & set invisible
        setPhoneShow(false);
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

    private boolean isPhoneInitialized() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetInitialized, false);
    }

    private void setPhoneInitialized(boolean initialized) {
        if (initialized)
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetInitialized, true);
        else
            getContainer().getApplicationState().remove(INTENT_phoneWidgetInitialized);
    }

    private boolean isPhoneTtsNotified() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetTtsNotified, false);
    }

    private void setPhoneTtsNotified(boolean ttsNotified) {
        if (ttsNotified)
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetTtsNotified, true);
        else
            getContainer().getApplicationState().remove(INTENT_phoneWidgetTtsNotified);
    }

    private boolean isPhoneRestartForced() {
        return getContainer().getApplicationState().getBoolean(INTENT_phoneWidgetRestartForced, false);
    }

    private void setPhoneRestartForced(boolean restartForced) {
        if (restartForced)
            getContainer().getApplicationState().putBoolean(INTENT_phoneWidgetRestartForced, true);
        else
            getContainer().getApplicationState().remove(INTENT_phoneWidgetRestartForced);
    }

    private void sendHangUp() {
        Log_d(LOG_TAG, "sendHangUp: ");
        sendKeyHeadSetHook(true);
    }

    private void sendPickUp() {
        Log_d(LOG_TAG, "sendPickUp: ");
        sendKeyHeadSetHook(false);
    }

    private void sendKeyHeadSetHook(boolean longPress) {
        if (mPreviewMode)
            return;

        // KEYCODE_HEADSETHOOK doesn't work for lollipop
        if (Build.VERSION.SDK_INT < 21) {
            long millis = System.currentTimeMillis();
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK);

            if (longPress)
                keyEvent = KeyEvent.changeTimeRepeat(keyEvent, millis, 1, KeyEvent.FLAG_LONG_PRESS);

            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
            getContext().sendOrderedBroadcast(intent, null);
        } else
            Functions.Actions.run_commands_as_root(new String[]{"input keyevent " + (!longPress ? "5" : "6")}, false);
    }

    private void sendKeyHeadSetHook(int keyEventAction, boolean longPress) {
    }

    /**
     * phone widget stuff (end)
     */

    /**
     * implement touch event handling
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

        Log_d(LOG_TAG, "onTouchEvent: " + actionMasked + ", " + pointerCoords.x + ":" + pointerCoords.y);
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!TouchEventProcessor.isTracking()) {
                    // check accept button
                    if (mAcceptButton.isShown() && TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton))
                        TouchEventProcessor.startTracking(mAcceptButton);
                    // check reject button
                    else if (mRejectButton.isShown() && TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton))
                        TouchEventProcessor.startTracking(mRejectButton);

                    if ((result = TouchEventProcessor.isTracking())) {
                        mActivePointerId = pointerId;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                pointerIndex = motionEvent.findPointerIndex(mActivePointerId);

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

        return result;
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

    /**
     * implement PhoneStateListener
     */
    private class ImplPhoneStateListener extends PhoneStateListener {
        private final String LOG_TAG = "ImplPhoneStateListener";

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (mPreviewMode)
                return;

            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    Log_d(LOG_TAG, "onCallStateChanged: idle");
                    if (getVisibility() == VISIBLE) {
                        callRejectedPhoneWidget(false);
                        setPhoneShow(false);
                        setVisibility(INVISIBLE);
                    }

                    setGyroscopeListener(false);
                    startScreenOffTimer();
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log_d(LOG_TAG, "onCallStateChanged: off hook");
                    if (getVisibility() == VISIBLE) {
                        callAcceptedPhoneWidget(false);
                        break;
                    }
                case TelephonyManager.CALL_STATE_RINGING:
                    Log_d(LOG_TAG, "onCallStateChanged: ringing");
                    setPhoneShow(true);
                    setVisibility(VISIBLE);
                    resetPhoneWidgetMakeVisible();
                    setIncomingNumber(incomingNumber);

                    setGyroscopeListener(true);
                    break;
            }
        }

        @Override
        public void onCallForwardingIndicatorChanged (boolean callForwardingIndicator) {
            Log_d(LOG_TAG, "onCallForwardingIndicatorChanged: " + callForwardingIndicator);
        }
    }

    /**
     * implement OnMenuActionListener
     */
    public int getMenuId() {
        return getId();
    }

    public void onMenuInit(ComponentFramework.MenuController menuController) {
        Log_d(LOG_TAG, "onMenuInit:");

        menuController.registerMenuOption(getMenuId(), R.id.menu_phone_speaker, R.drawable.ic_phone_speaker_on);
        menuController.registerMenuOption(getMenuId(), R.id.menu_phone_mic, R.drawable.ic_phone_mic_off);
//        menuController.registerMenuOption(getMenuId(), R.id.menu_test, R.drawable.ic_option_overlay_option1);
    }

    public boolean onMenuOpen(ComponentFramework.MenuController.Menu menu) {
        Log_d(LOG_TAG, "onMenuOpen: ");

        TelephonyManager telephonyManager = (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);

        // don't open menu until a call is active
        if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
            return false;

        AudioManager audioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);

        for (int optionId : menu.getOptions()) {
            ComponentFramework.MenuController.Option option = menu.getOption(optionId);

            switch (option.getId()) {
                case R.id.menu_phone_speaker:
                    option.setImageId(audioManager.isSpeakerphoneOn() ? R.drawable.ic_phone_speaker_off : R.drawable.ic_phone_speaker_on);
                    option.setEnabled(telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK);
                    break;
                case R.id.menu_phone_mic:
                    option.setImageId(!audioManager.isMicrophoneMute() ? R.drawable.ic_phone_mic_off : R.drawable.ic_phone_mic_on);
                    option.setEnabled(telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK);
                    break;
                case R.id.menu_test:
                    option.setEnabled(telephonyManager.getCallState() == TelephonyManager.CALL_STATE_RINGING);
                    break;
            }
        }

        return true;
    }

    public void onMenuAction(ComponentFramework.MenuController.MenuOption menuOption) {
        Log_d(LOG_TAG, "onMenuAction: " + menuOption.getOptionId());

        AudioManager audioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);

        switch (menuOption.getOptionId()) {
            case R.id.menu_phone_speaker:
                audioManager.setSpeakerphoneOn(menuOption.getImageId() == R.drawable.ic_phone_speaker_on);
                menuOption.setImageId(audioManager.isSpeakerphoneOn() ? R.drawable.ic_phone_speaker_off : R.drawable.ic_phone_speaker_on);
                break;
            case R.id.menu_phone_mic:
                audioManager.setMicrophoneMute(menuOption.getImageId() == R.drawable.ic_phone_mic_off);
                menuOption.setImageId(!audioManager.isMicrophoneMute() ? R.drawable.ic_phone_mic_off : R.drawable.ic_phone_mic_on);
                break;
            case R.id.menu_test:
                getContainer().dumpBackStack();
                break;
        }
    }

    private void forceRestart() {
        Log_d(LOG_TAG, "forceRestart: " + isPhoneRestartForced());
        if (!isPhoneRestartForced() && ComponentFramework.OnKeepOnScreen.class.isAssignableFrom(getActivity().getClass())) {
            setPhoneRestartForced(true);

            ((ComponentFramework.OnKeepOnScreen)getActivity()).onKeepOnScreen(getContainer().getApplicationState(), 200);
        }
    }
}
