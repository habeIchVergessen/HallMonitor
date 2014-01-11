package org.durka.hallmonitor;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.RelativeLayout;

/**
 * This is the activity that is displayed by default - it is displayed for the configurable delay number of milliseconds when the case is closed,
 * it is also displayed when the power button is pressed when the case is already closed
 */
public class DefaultActivity extends Activity implements OnScreenActionListener {
	
	private final static String LOG_TAG = "DA";

	private HMAppWidgetManager hmAppWidgetManager = Functions.hmAppWidgetManager;

    private static boolean mDebug = false;

	public static boolean on_screen;

	// states for alarm and phone
	public static boolean alarm_firing = false;
	public static boolean phone_ringing = false;

	//audio manager to detect media state
	private AudioManager audioManager;

	//Action fired when alarm goes off
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    //Action to trigger snooze of the alarm
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    //Action to trigger dismiss of the alarm
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    //This action should let us know if the alarm has been killed by another app
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";
    
    //this action will let us toggle the flashlight
    public static final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";
    boolean torchIsOn = false;
    
    //all the views we need
    private GridView grid = null;
    private View snoozeButton = null;
    private View dismissButton = null;
    private View defaultAlarmWidget = null;
    private View defaultNormalWidget = null;
    private View defaultPhoneWidget = null;
    private RelativeLayout defaultContent = null;
    private TextClock defaultTextClock = null;
    public ImageButton torchButton = null;

    private PhoneWidget mPhoneWidget = null;
    
    protected boolean mWiredHeadSetPlugged = false;


	// we need to kill this activity when the screen opens
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            
			if (action.equals(Intent.ACTION_SCREEN_ON)) {

				Log_d(LOG_TAG + ".onReceive", "Screen on event received.");
                boolean coverClosed = Functions.Is.cover_closed(context);

				if (coverClosed) {
                    if (!mPhoneWidget.isShowPhoneWidget()) {
                        Log_d(LOG_TAG + ".onReceive", "Cover is closed, display Default Activity.");
                        //easiest way to do this is actually just to invoke the close_cover action as it does what we want
                        Functions.Actions.close_cover(getApplicationContext(), getIntent().getExtras());
                    } else {
                        Log_d(LOG_TAG + ".onReceive", "Cover is closed, phoneWidget doesn't like this restarts");
                    }
				} else {
					Log_d(LOG_TAG + ".onReceive", "Cover is open, stopping Default Activity.");

					// when the cover opens, the fullscreen activity goes poof				
					moveTaskToBack(true);
					
					// stop screen off timer
					Functions.Actions.stopScreenOffTimer();
				}

			} else if (action.equals(ALARM_ALERT_ACTION)) {

				Log_d(LOG_TAG + ".onReceive", "Alarm on event received.");

				//only take action if alarm controls are enabled
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_alarm_controls", false)) {

					Log_d(LOG_TAG + ".onReceive", "Alarm controls are enabled, taking action.");

					//set the alarm firing state
					alarm_firing=true;

					//if the cover is closed then
					//we want to pop this activity up over the top of the alarm activity
					//to guarantee that we need to hold off until the alarm activity is running
					//a 1 second delay seems to allow this
					if (Functions.Is.cover_closed(context)) {
						Timer timer = new Timer();
						timer.schedule(new TimerTask() {
							@Override
							public void run() {	
								Intent myIntent = new Intent(getApplicationContext(),DefaultActivity.class);
								myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
										| Intent.FLAG_ACTIVITY_CLEAR_TOP
										| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
								myIntent.setAction(Intent.ACTION_MAIN);
								startActivity(myIntent);
							}
						}, 1000);	
					}
				} else {
					Log_d(LOG_TAG + ".onReceive", "Alarm controls are not enabled.");
				}

            } else if (intent.getAction().equals(ALARM_DONE_ACTION) ) {

                Log_d(LOG_TAG + ".onReceive", "Alarm done event received.");

                //if the alarm is turned off using the normal alarm screen this will
                //ensure that we will hide the alarm controls
                alarm_firing=false;
			
			} else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				String phoneExtraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

				Log_d(LOG_TAG + ".onReceive", "ACTION_PHONE_STATE_CHANGED = " + phoneExtraState);

				if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
					Functions.Events.incoming_call(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
                } else if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                    // pick up came from other source than screen
                    mPhoneWidget.callAcceptedPhoneWidget(false);
                    phone_ringing = false;
                } else if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                    phone_ringing = false;
                    mPhoneWidget.close();
                    refreshDisplay();
                }
			} else if (action.equals("org.durka.hallmonitor.debug")) {
				Log_d(LOG_TAG + ".onReceive", "received debug intent");
				// test intent to show/hide a notification
				switch (intent.getIntExtra("notif", 0)) {
				case 1:
					Functions.Actions.debug_notification(context, true);
					break;
				case 2:
					Functions.Actions.debug_notification(context, false);
					break;
				}
			} else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {	// headset (un-)plugged
                mWiredHeadSetPlugged = ((intent.getIntExtra("state", -1) == 1));
                Log_d(LOG_TAG + ".onReceive", "ACTION_HEADSET_PLUG: plugged " + mWiredHeadSetPlugged + " (" + intent.getIntExtra("state", -1) + ")");
            }
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//get the audio manager
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

		//pass a reference back to the Functions class so it can finish us when it wants to
		//FIXME Presumably there is a better way to do this
		Functions.defaultActivity = this;

		Log_d(LOG_TAG + ".onCreate", "");

		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		//Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		//set default view
		setContentView(R.layout.activity_default);

		//get the views we need
		grid = (GridView)findViewById(R.id.default_icon_container);
	    snoozeButton = findViewById(R.id.snoozebutton);
	    dismissButton = findViewById(R.id.dismissbutton);
        defaultAlarmWidget = findViewById(R.id.default_content_alarm);
	    defaultNormalWidget = findViewById(R.id.default_content_normal);
        defaultPhoneWidget = findViewById(R.id.default_content_phone);

	    defaultContent = (RelativeLayout) findViewById(R.id.default_content);
	    defaultTextClock = (TextClock) findViewById(R.id.default_text_clock);
	    //torchButton = (ImageButton) findViewById(R.id.torchbutton);

        // TextClock format
        SpannableString spanString = new SpannableString(getResources().getString(R.string.styled_24_hour_clock));
        spanString.setSpan(new RelativeSizeSpan(2.8f), 1, 5, 0);
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 1, 5, 0);
        spanString.setSpan(new RelativeSizeSpan(0.8f), 6, spanString.length(), 0);
        defaultTextClock.setFormat24Hour(spanString);

        // initPhoneWidget (overtake control if phone state is ringing or offhook)
        mPhoneWidget = (PhoneWidget)findViewById(R.id.phone_widget);
        mPhoneWidget.registerOnLockScreenListener(this);

        // add screen on and alarm fired intent receiver (don't register before variables are initialized)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ALARM_ALERT_ACTION);
        filter.addAction(ALARM_DONE_ACTION);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction("org.durka.hallmonitor.debug");
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(receiver, filter);
    }

	@Override
	protected void onStart() {
		super.onStart();
		Log_d(LOG_TAG + "-oS", "starting");
		on_screen = true;

        updateBatteryStatus();

		if (NotificationService.that != null) {
			// notification listener service is running, show the current notifications
			Functions.Actions.setup_notifications();
		}
	}

    @Override
    protected void onPause() {
        super.onPause();
        Log_d(LOG_TAG, "onPause: " + (mPhoneWidget.isShowPhoneWidget()) + ", extras: " + (getIntent().getExtras() == null ? "null" : getIntent().getExtras().size()));
        mPhoneWidget.unregisterPhoneStateListener();
    }

	@Override
	protected void onResume() {
		super.onResume();

        // load debug setting
        mDebug = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("pref_dev_opts_debug", false);

        Log.d(LOG_TAG, "onResume: " + (mPhoneWidget.isShowPhoneWidget()) + ", debug = " + mDebug);
        // initPhoneWidget (overtake control if phone state is ringing or offhook)
        mPhoneWidget.initPhoneWidget();
        mPhoneWidget.registerPhoneStateListener();

        refreshDisplay();

        // check preview
        if (getIntent().getExtras() != null && !getIntent().getExtras().getString("preview", "").equals("")) {
            String preview = getIntent().getExtras().getString("preview");

            if (preview.equals("phoneWidget")) {
                mPhoneWidget.preview();
            }
        }
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log_d(LOG_TAG + "-oS", "stopping");
		Functions.Actions.stopScreenOffTimer();
		on_screen = false;
	}

	@Override
	protected void onDestroy() {
        //tidy up our receiver when we are destroyed
        unregisterReceiver(receiver);
        mPhoneWidget.unregisterOnLockScreenListener();
        mPhoneWidget.unregisterPhoneStateListener();

        super.onDestroy();
	}

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        boolean result = true;

        // event handling phoneWidget
        if (mPhoneWidget.isShowPhoneWidget())
            result = mPhoneWidget.onTouchEvent_PhoneWidgetHandler(motionEvent);

        return result;
    }

    /**
	 * Refresh the display taking account of device and application state
	 */
    public void refreshDisplay() {
        Log_d(LOG_TAG, "refreshDisplay: ");
        //get the layout for the windowed view
        RelativeLayout contentView = (RelativeLayout)findViewById(R.id.default_widget);

        //hide or show the torch button as required
        if (torchButton != null) {
            boolean prefFlash = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_flash_controls", false);
            torchButton.setVisibility((prefFlash ? View.VISIBLE : View.INVISIBLE));
        }

        //if the alarm is firing then show the alarm controls, otherwise
        //if we have a media app widget and media is playing or headphones are connected then display that, otherwise
        //if we have a default app widget to use then display that, if not then display our default clock screen
        //(which is part of the default layout so will show anyway)
        //will do this simply by setting the widgetType
        String widgetType = "default";
        if (hmAppWidgetManager.doesWidgetExist("media") && (mWiredHeadSetPlugged || audioManager.isMusicActive())) {
            widgetType = "media";
        }
	    
	    if (alarm_firing) {
	    	Log_d(LOG_TAG, "refreshDisplay: alarm_firing");
	    	//show the alarm controls
            defaultAlarmWidget.setVisibility(View.VISIBLE);
            defaultNormalWidget.setVisibility(View.INVISIBLE);
	    	mPhoneWidget.close();
	    	
	    	snoozeButton.setVisibility(View.VISIBLE);
	    	dismissButton.setVisibility(View.VISIBLE);
	    } else if (phone_ringing || mPhoneWidget.isShowPhoneWidget()) {
	    	Log_d(LOG_TAG, "refreshDisplay: phone_ringing or PhoneWidget.isShowPhoneWidget()");

            //show the phone controls
            defaultAlarmWidget.setVisibility(View.INVISIBLE);
    		defaultNormalWidget.setVisibility(View.INVISIBLE);
	    	mPhoneWidget.open();
	    	
        } else {
	    	//default view    	
            defaultAlarmWidget.setVisibility(View.INVISIBLE);
            defaultNormalWidget.setVisibility(View.VISIBLE);
            mPhoneWidget.close();

	    	//add the required widget based on the widgetType
		    if (hmAppWidgetManager.doesWidgetExist(widgetType)) {
		    	Log_d(LOG_TAG, "refreshDisplay: default_widget");
		    	
		    	//remove the TextClock from the contentview
			    contentView.removeAllViews();
		    	
		    	//get the widget
			    AppWidgetHostView hostView = hmAppWidgetManager.getAppWidgetHostViewByType(widgetType);
			    
			    //if the widget host view already has a parent then we need to detach it
			    ViewGroup parent = (ViewGroup)hostView.getParent();
			    if ( parent != null) {
			    	Log_d(LOG_TAG + ".onCreate", "hostView had already been added to a group, detaching it.");
			       	parent.removeView(hostView);
			    }    
			    
			    //add the widget to the view
			    contentView.addView(hostView);
		    } else {
		    	Log_d(LOG_TAG, "refreshDisplay: default_widget");

//		    	Drawable rounded = getResources().getDrawable(R.drawable.rounded);
//		    	rounded.setColorFilter(new PorterDuffColorFilter(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_bgcolor", 0xFF000000), PorterDuff.Mode.MULTIPLY));
//		    	defaultContent.setBackground(rounded);
                defaultContent.setBackgroundColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_bgcolor", 0xFF000000));
		    	defaultTextClock.setTextColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_fgcolor", 0xFFFFFFFF));
		    }
	    }
	}


	/** Called when the user touches the snooze button */
	public void sendSnooze(View view) {
		// Broadcast alarm snooze event
		Intent alarmSnooze = new Intent(ALARM_SNOOZE_ACTION);
		sendBroadcast(alarmSnooze);
		//unset alarm firing flag
		alarm_firing = false;
		//refresh the display
		refreshDisplay();
	}

	/** Called when the user touches the dismiss button */
	public void sendDismiss(View view) {
		// Broadcast alarm dismiss event
		Intent alarmDismiss = new Intent(ALARM_DISMISS_ACTION);
		sendBroadcast(alarmDismiss);
		//unset alarm firing flag
		alarm_firing = false;
		//refresh the display
		refreshDisplay();
	}
	
	public void sendHangUp(View view) {
		Functions.Actions.hangup_call();
	}
	
	public void sendPickUp(View view) {
		Functions.Actions.pickup_call();
	}

    //toggle the torch
    public void sendToggleTorch(View view) {
        Functions.Actions.toggle_torch(this);
    }

    private void updateBatteryStatus() {
        if (findViewById(R.id.default_battery_picture) != null) {
            Intent battery_status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (   battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                    && !(battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL)) {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery_charge);
            } else {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery);
            }
            ((ImageView)findViewById(R.id.default_battery_picture)).getDrawable().setLevel((int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100));
        }
    }

    /*
     * lock screen listener
     */

    public void onLockScreen(ViewGroup caller) {
        Log_d(LOG_TAG, "onLockScreen: " + caller.getId());

        Functions.Actions.rearmScreenOffTimer(getBaseContext());
    }

    public void onWakeUpScreen() {
        Log_d(LOG_TAG, "onWakeUpScreen: ");
        Functions.Actions.wakeUpScreen(getBaseContext());
    }
    public void onStopScreenOffTimer() {
        Log_d(LOG_TAG, "onStopScreenOffTimer: ");
        Functions.Actions.stopScreenOffTimer();
    }

    public void onStartScreenOffTimer() {
        Log_d(LOG_TAG, "onStartScreenOffTimer: ");
        Functions.Actions.rearmScreenOffTimer(this);
    }

    /*
     * helpers
     */

    public static boolean isDebug() {
        return mDebug;
    }

    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }
}

