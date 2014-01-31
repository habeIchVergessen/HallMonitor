/* Copyright 2013 Alex Burka

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.durka.hallmonitor_framework_test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.WindowManager;


public class ViewCoverService extends Service implements SensorEventListener, TextToSpeech.OnInitListener {

    private final static String LOG_TAG = "VCS";

    private final static String hallFileName = "/sys/devices/virtual/sec/sec_key/hall_detect";
    private float lastProximityValue = 0.0f;
    private static boolean globalCoverState = false;
    private CoverThread coverThread = null;
    private RestartThread restartThread = null;
    private ComponentFramework.OnCoverStateChangedListener mOnCoverStateChangedListener = null;

    private static ViewCoverService runningInstance = null;
	private SensorManager       mSensorManager;

    private static boolean mDebug = false;

    /**
     *  Text-To-Speech
     */
    private TextToSpeech mTts;
    private boolean mTtsInitComplete = false;

    private boolean mWiredHeadSetPlugged = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {	// headset (un-)plugged
                mWiredHeadSetPlugged = ((intent.getIntExtra("state", -1) == 1));
            } else if (action.equals(getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE))) {
                String text = intent.getStringExtra("sendTextToSpeech");
                sendTextToSpeech(text);
            } else if (action.equals(getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE))) {
                stopTextToSpeech();
            } else if (action.equals(getString(R.string.ACTION_RESTART_DEFAULT_ACTIVITY))) {
                Log_d(LOG_TAG, "onReceive: ACTION_RESTART_DEFAULT_ACTIVITY");
            } else if (action.equals(getString(R.string.ACTION_RESTART_FRAMEWORK_TEST))) {
                Log_d(LOG_TAG, "onReceive: ACTION_RESTART_FRAMEWORK_TEST");

                int delay = intent.getIntExtra("restartDelay", 0);
                intent.getExtras().remove("restartDelay");

                restartFrameworkTest(intent.getExtras(), delay);
            }
        }
    };


    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log_d(LOG_TAG + ".onStartCommand", "View cover service started");

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
		
        // Text-To-Speech
        initTextToSpeech();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE));
        filter.addAction(getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE));
        filter.addAction(getString(R.string.ACTION_RESTART_DEFAULT_ACTIVITY));
        filter.addAction(getString(R.string.ACTION_RESTART_FRAMEWORK_TEST));
        registerReceiver(receiver, filter);

        runningInstance = this;
        globalCoverState = isCoverClosedPrivate();

        return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		Log_d(LOG_TAG + ".onStartCommand", "View cover service stopped");
		
		mSensorManager.unregisterListener(this);

        // Text-To-Speech
        unregisterReceiver(receiver);
        destroyTextToSpeech();

        if (coverThread != null)
            coverThread.interrupt();

        runningInstance = null;
    }

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
		Log_d(LOG_TAG + "onAccuracyChanged", "OnAccuracyChanged: Sensor=" + sensor.getName() + ", accuracy=" + accuracy);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            proximity(event.values[0]);
		}
	}

    /**
     * Text-To-Speech
     *
     * Executed when a new TTS is instantiated. We don't do anything here since
     * our speech is now determine by the button click
     * @param initStatus
     */

    public void onInit(int initStatus) {
        switch (initStatus) {
            case TextToSpeech.SUCCESS:
                Log_d(LOG_TAG, "init Text To Speech successed");
                //mTts.setLanguage(Locale.GERMANY);
                mTtsInitComplete = true;
                break;
            case TextToSpeech.ERROR:
                Log_d(LOG_TAG, "init Text To Speech failed");
                mTts = null;
                break;
            default:
                Log_d(LOG_TAG, "onInit: " + initStatus);
                break;
        }
    }

    private void initTextToSpeech() {
        // init Text To Speech
        if (!mTtsInitComplete && mTts ==  null) {
            mTts = new TextToSpeech(this, this);
        }
    }

    private boolean sendTextToSpeech(String text) {
        boolean ttsEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_phone_controls_tts", false);
        boolean speakerEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_phone_controls_speaker", false);

        int delay = 500;
        try {
            delay = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("pref_phone_controls_tts_delay", Integer.toString(delay)));
        } catch (NumberFormatException nfe) {
            ;
        }

        Log_d(LOG_TAG, "sendTextToSpeech: " + text + ", " + ttsEnabled);
        boolean result = false;

        if (mTtsInitComplete && mTts != null && ttsEnabled) {
            AudioManager audioManager = (AudioManager)this.getSystemService(AUDIO_SERVICE);

            boolean isHeadsetConnected = audioManager.isBluetoothA2dpOn() || mWiredHeadSetPlugged;
            if (isHeadsetConnected || speakerEnabled) {
                HashMap <String, String> params = new HashMap<String, String>();

                if (isHeadsetConnected)
                    params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(audioManager.STREAM_VOICE_CALL));
                else
                    params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(audioManager.STREAM_RING));

                // clear queue
                mTts.stop();
                // delay
                    mTts.playSilence(delay, TextToSpeech.QUEUE_ADD, params);
                // play
                result = (mTts.speak(text, TextToSpeech.QUEUE_ADD, params) == TextToSpeech.SUCCESS);
                //Log_d(LOG_TAG, "sendTextToSpeech: text = '" + text.replaceAll(".", "*") + "' (" + delay + " ms) -> " + (result ? "ok" : "failed"));
            }
        }

        return result;
    }

    private void stopTextToSpeech() {
        Log_d(LOG_TAG, "stopTextToSpeech: ");

        if (mTtsInitComplete && mTts != null)
            mTts.stop();
    }

    private void destroyTextToSpeech() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
    }

    /**
     * Text-To-Speech (end)
     */

    private void proximity(final float value) {
        if ((value > 0 && lastProximityValue == 0) || (value == 0 && lastProximityValue > 0)) {
            Log_d(LOG_TAG, "proximity: " + value);

            // stop running thread
            if (coverThread != null)
                coverThread.interrupt();

            // start new thread
            coverThread = new CoverThread(value);
            coverThread.start();

            lastProximityValue = value;
        }
    }

    private synchronized void onCoverStateChanged(boolean coverState) {
        Log_d(LOG_TAG, "onCoverStateChanged: " + coverState);

        globalCoverState = coverState;

        // notify listener
        if (mOnCoverStateChangedListener != null)
            mOnCoverStateChangedListener.onCoverStateChanged(globalCoverState);

        if (!globalCoverState) {
            // step 1: if we were going to turn the screen off, cancel that
            Functions.Actions.stopScreenOffTimer();

            // step 2: wake the screen
            Functions.Actions.wakeUpScreen(getApplicationContext());

            // step 3: reset touch screen sensitivity
            startTouchScreenCoverThread(false);
        }

        if (globalCoverState) {
            // step 1: if we were going to turn the screen off, cancel that
            Functions.Actions.rearmScreenOffTimer(getBaseContext());

            // step 2: set touch screen sensitivity
            startTouchScreenCoverThread(true);

            // start activity
            if (mOnCoverStateChangedListener == null)
                restartFrameworkTest(null);
        }
    }

    private synchronized boolean isCoverClosedPrivate() {
        boolean result = false;

        String status = "";
        try {
            Scanner sc = new Scanner(new File(hallFileName));
            status = sc.nextLine();
            sc.close();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Hall effect sensor device file not found!");
        }

        result = (status.compareTo("CLOSE") == 0);

        return result;
    }

    private void registerOnCoverStateChangedListenerPrivate(ComponentFramework.OnCoverStateChangedListener onCoverStateChangedListener) {
        Log_d(LOG_TAG, "registerOnCoverStateChangedListenerPrivate");
        mOnCoverStateChangedListener = onCoverStateChangedListener;
    }

    private void unregisterOnCoverStateChangedListenerPrivate(ComponentFramework.OnCoverStateChangedListener onCoverStateChangedListener) {
        Log_d(LOG_TAG, "unregisterOnCoverStateChangedListenerPrivate");

        if (mOnCoverStateChangedListener == onCoverStateChangedListener)
            mOnCoverStateChangedListener = null;
    }

    private void restartFrameworkTest(final Bundle extras, int delay) {
        Log_d(LOG_TAG, "restartFrameworkTest: " + extras + ", " + delay);

        if (restartThread != null)
            restartThread.interrupt();

        restartThread = new RestartThread(extras, delay);
        restartThread.start();
    }

    private void restartFrameworkTest(final Bundle extras) {
        Log_d(LOG_TAG, "restartFrameworkTest: " + extras);

        Intent start = new Intent(getBaseContext(), ComponentTestActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        start.setAction(Intent.ACTION_MAIN);
        // restore extras from previously running task
        if (extras != null)
            start.putExtras(extras);
        getApplicationContext().startActivity(start);
    }

    private void startTouchScreenCoverThread(boolean coverMode) {
        Log_d(LOG_TAG, "startTouchScreenCoverThread: " + coverMode);
        TouchScreenCoverThread touchScreenCoverThread = new TouchScreenCoverThread(coverMode);
        touchScreenCoverThread.start();
    }

    /**
     * helper
     */
    private void Log_d(String tag, String message) {
        if (mDebug)
            Log.d(tag, message);
    }

    /**
     * public static functions
     */
    public static boolean isServiceRunning() {
        return (runningInstance != null);
    }

    public static boolean isCoverClosed() {
        return globalCoverState;
    }

    public static void registerOnCoverStateChangedListener(ComponentFramework.OnCoverStateChangedListener onCoverStateChangedListener) {
        if (runningInstance != null)
            runningInstance.registerOnCoverStateChangedListenerPrivate(onCoverStateChangedListener);
    }

    public static void unregisterOnCoverStateChangedListener(ComponentFramework.OnCoverStateChangedListener onCoverStateChangedListener) {
        if (runningInstance != null)
            runningInstance.unregisterOnCoverStateChangedListenerPrivate(onCoverStateChangedListener);
    }

    public static void setDebugMode(boolean debug) {
        mDebug = debug;
    }

    /**
     * helper CoverThread
     */
    private class CoverThread extends Thread {
        private final String LOG_TAG = "CoverThread";

        private float mValue;
        private final int maxCnt = 3;
        private int cnt = 0;

        public CoverThread(float value) {
            super();

            mValue = value;
        }

        @Override
        public void run() {
            boolean coverState = false;

            cnt = 0;

            do {
                try {
                    synchronized (this) {
                        wait(15 * (int)Math.pow(2, cnt));
                    }
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "run: exception occurred! " + e.getMessage());
                    break;
                }

                if ((coverState = isCoverClosedPrivate()) == (mValue == 0)) {
                    if (coverState != globalCoverState)
                        onCoverStateChanged(coverState);
                    break;
                }

                cnt++;
            } while (cnt <= maxCnt);
        }
    }

    private class RestartThread extends Thread {
        private final String LOG_TAG = "RestartThread";
        private Bundle mExtras = null;
        private int mWaitTime = 0;

        public RestartThread(Bundle extras, int waitTime) {
            mExtras = extras;
            mWaitTime = (waitTime > 0 ? waitTime : 0);
        }

        @Override
        public void run() {
            try {
                if (mWaitTime > 0)
                    synchronized (this) {
                        wait(mWaitTime);
                    }

                Intent start = new Intent(getBaseContext(), ComponentTestActivity.class);
                start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
//                start.setAction(Intent.ACTION_VIEW);
                // restore extras from previously running task
                if (mExtras != null)
                    start.putExtras(mExtras);
                getApplicationContext().startActivity(start);
            } catch (InterruptedException ie) {
            } catch (Exception e) {
                Log.e(LOG_TAG, "run: exception occurred! " + e.getMessage());
            }
        }
    }

    private class TouchScreenCoverThread extends Thread {
        private final String LOG_TAG = "TouchScreenCoverThread";
        private boolean mCoverMode = false;

        public TouchScreenCoverThread(boolean coverMode) {
            mCoverMode = coverMode;
        }

        @Override
        public void run() {
            try {
                Functions.Actions.setTouchScreenCoverMode(getApplicationContext(), mCoverMode);
            } catch (Exception e) {
                Log.e(LOG_TAG, "run: exception occurred! " + e.getMessage());
            }
        }
    }
}