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

package org.durka.hallmonitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import eu.chainfire.libsuperuser.Shell;

public class CoreStateManager {
	private final String LOG_TAG = "Hall.CSM";

	private final Context mAppContext;

	// All we need for alternative torch
	private Camera camera;
	// private final boolean flashIsOn = false;
	private boolean deviceHasFlash;

	// Class that handles interaction with 3rd party App Widgets
	public HMAppWidgetManager hmAppWidgetManager;

	private DefaultActivity defaultActivity;
	private Configuration configurationActivity;

	private final SharedPreferences preference_all;

	private final boolean systemApp;
	private final boolean adminApp;
	private final boolean rootApp;

	// audio manager to detect media state
	private AudioManager audioManager;

	private final boolean lockMode;

	private boolean notification_settings_ongoing = false;
	private boolean widget_settings_ongoing = false;

	// states for alarm and phone
	private boolean alarm_firing = false;
	private boolean phone_ringing = false;
	private boolean torch_on = false;
	private boolean camera_up = false;
	private String call_from = "";
	private boolean cover_closed = false;
	private boolean forceCheckCoverState = false;

	private boolean mainLaunched = false;

	private CoreReceiver mCoreReceiver;

	private static long blackscreen_time = 0;

	CoreStateManager(Context context) {
		mAppContext = context;

		preference_all = PreferenceManager
				.getDefaultSharedPreferences(mAppContext);

		// Enable access to sleep mode
		systemApp = (mAppContext.getApplicationInfo().flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
		if (systemApp) {
			Log.d(LOG_TAG, "We are a system app.");
		} else {
			Log.d(LOG_TAG, "We are not a system app.");
		}

		// Lock mode
		if (!systemApp || preference_all.getBoolean("pref_lock_mode", false)) {
			lockMode = true;
		} else {
			lockMode = false;
		}

		if (lockMode) {
			final DevicePolicyManager dpm = (DevicePolicyManager) mAppContext
					.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(mAppContext,
					AdminReceiver.class);
			adminApp = dpm.isAdminActive(me);
			if (!adminApp) {
				// FIXME (remove it?)
				Log.d(LOG_TAG, "launching dpm overlay");
				// kick off the widget picker, the call back will be picked up
				// in
				setWidgetSettingsOngoing(true);
				getContext()
						.startActivity(
								new Intent(getContext(), Configuration.class)
										.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
												| Intent.FLAG_ACTIVITY_NO_ANIMATION
												| Intent.FLAG_ACTIVITY_CLEAR_TOP
												| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
				Log.d(LOG_TAG, "Started configuration activity.");
				Intent coup = new Intent(
						DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				coup.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, me);
				coup.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
						mAppContext.getString(R.string.admin_excuse));
				getConfigurationActivity().startActivityForResult(coup,
						CoreApp.DEVICE_ADMIN_WAITING);
			}
		} else {
			adminApp = false;
		}

		if (adminApp) {
			Log.d(LOG_TAG + ".lBS", "We are an admin.");
		} else {
			Log.d(LOG_TAG + ".lBS",
					"We are not an admin so cannot do anything.");
		}

		if (preference_all.getBoolean("pref_runasroot", false)) {
			rootApp = Shell.SU.available();
		} else {
			rootApp = false;
		}
		if (rootApp) {
			Log.d(LOG_TAG + ".lBS", "We are root.");
		} else {
			Log.d(LOG_TAG + ".lBS", "We are not root.");
		}

		if (preference_all.getBoolean("pref_proximity", false)) {
			forceCheckCoverState = true;
		}

		if (preference_all.getBoolean("pref_media_widget", false)
				|| preference_all.getBoolean("pref_default_widget", false)) {
			hmAppWidgetManager = new HMAppWidgetManager(this);
		}

		if (preference_all.getBoolean("pref_default_widget", false)) {
			int widgetId = preference_all.getInt("default_widget_id", -1);
			if (widgetId == -1) {
				register_widget("default");
			} else {
				if (!hmAppWidgetManager.doesWidgetExist("default")) {
					Log.d(LOG_TAG + ".sWC", "creating default widget with id="
							+ widgetId);
					Intent data = new Intent();
					data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

					hmAppWidgetManager.createWidget("default", data);
				}
			}
		}

		if (preference_all.getBoolean("pref_media_widget", false)) {
			audioManager = (AudioManager) mAppContext
					.getSystemService(Context.AUDIO_SERVICE);

			int widgetId = preference_all.getInt("media_widget_id", -1);
			if (widgetId == -1) {
				register_widget("media");
			} else {
				if (!hmAppWidgetManager.doesWidgetExist("media")) {
					Log.d(LOG_TAG + ".sWC", "creating media widget with id="
							+ widgetId);
					Intent data = new Intent();
					data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

					hmAppWidgetManager.createWidget("media", data);
				}
			}
		}

		// we might have missed a phone-state revelation
		phone_ringing = ((TelephonyManager) mAppContext
				.getSystemService(Context.TELEPHONY_SERVICE)).getCallState() == TelephonyManager.CALL_STATE_RINGING;
		// we might have missed an alarm alert
		// TODO: find a way
		// alarm_firing =
		// ((TelephonyManager)
		// mAppContext.getSystemService(Context.TELEPHONY_SERVICE)).getCallState()
		// == TelephonyManager.CALL_STATE_RINGING;
		Intent stateIntent = mAppContext.registerReceiver(null,
				new IntentFilter(CoreReceiver.TORCH_STATE_CHANGED));
		torch_on = stateIntent != null
				&& stateIntent.getIntExtra("state", 0) != 0;

	}

	public Context getContext() {
		return mAppContext;
	}

	public boolean getPhoneRinging() {
		return phone_ringing;
	}

	public void setPhoneRinging(boolean enable) {
		phone_ringing = enable;
	}

	public boolean getAlarmFiring() {
		return alarm_firing;
	}

	public void setAlarmFiring(boolean enable) {
		alarm_firing = enable;
	}

	public boolean getTorchOn() {
		return torch_on;
	}

	public void setTorchOn(boolean enable) {
		torch_on = enable;
	}

	public boolean getCameraUp() {
		return camera_up;
	}

	public void setCameraUp(boolean enable) {
		camera_up = enable;
	}

	public boolean getCoverClosed() {
		return getCoverClosed(forceCheckCoverState);
	}

	public boolean getCoverClosed(boolean forceCheck) {
		if (forceCheck) {
			String status = "";
			try {
				Scanner sc = new Scanner(new File(
						mAppContext.getString(R.string.hall_file)));
				status = sc.nextLine();
				sc.close();
			} catch (FileNotFoundException e) {
				Log.e(mAppContext.getString(R.string.app_name),
						"Hall effect sensor device file not found!");
			}
			boolean isClosed = (status.compareTo("CLOSE") == 0);
			Log.d(LOG_TAG + ".cover_closed", "Cover closed state is: "
					+ isClosed);
			return isClosed;
		} else {
			Log.d(LOG_TAG + ".cover_closed", "Cover closed state is: "
					+ cover_closed);
			return cover_closed;
		}
	}

	public void setCoverClosed(boolean enable) {
		cover_closed = enable;
	}

	public boolean getMainLaunched() {
		return mainLaunched;
	}

	public void setMainLaunched(boolean enable) {
		mainLaunched = enable;
	}

	public boolean getWidgetSettingsOngoing() {
		return widget_settings_ongoing;
	}

	public void setWidgetSettingsOngoing(boolean enable) {
		widget_settings_ongoing = enable;
	}

	public boolean getNotificationSettingsOngoing() {
		return notification_settings_ongoing;
	}

	public void setNotificationSettingsOngoing(boolean enable) {
		notification_settings_ongoing = enable;
	}

	public boolean getSystemApp() {
		return systemApp;
	}

	public boolean getAdminApp() {
		return adminApp;
	}

	public boolean getRootApp() {
		return rootApp;
	}

	public boolean getLockMode() {
		return lockMode;
	}

	public long getBlackScreenTime() {
		return blackscreen_time;
	}

	public void setBlackScreenTime(long time) {
		blackscreen_time = time;
	}

	public String getCallFrom() {
		return call_from;
	}

	public void setCallFrom(String num) {
		call_from = num;
	}

	public SharedPreferences getPreference() {
		return preference_all;
	}

	public void registerCoreReceiver() {
		if (mCoreReceiver == null) {
			/*
			 * HEADSET_PLUG, SCREEN_ON and SCREEN_OFF only available through
			 * registerReceiver function
			 */
			mCoreReceiver = new CoreReceiver();
			IntentFilter intfil = new IntentFilter();
			intfil.addAction(Intent.ACTION_HEADSET_PLUG);
			intfil.addAction(Intent.ACTION_SCREEN_ON);
			intfil.addAction(Intent.ACTION_SCREEN_OFF);
			mAppContext.registerReceiver(mCoreReceiver, intfil);
		}
	}

	public void unregisterCoreReceiver() {
		if (mCoreReceiver != null) {
			mAppContext.unregisterReceiver(mCoreReceiver);
		}
	}

	public boolean setDefaultActivity(DefaultActivity activityInstance) {
		if (defaultActivity == null) {
			defaultActivity = activityInstance;
			return true;
		} else if (activityInstance == null) {
			defaultActivity = null;
			return true;
		} else {
			Log.w(LOG_TAG, "Warning already default activity set!!!!");
			return false;
		}
	}

	public boolean setConfigurationActivity(Configuration activityInstance) {
		// if (configurationActivity == null) {
		configurationActivity = activityInstance;
		return true;
		// } else {
		// Log.w(LOG_TAG, "Warning already configuration activity set!!!!");
		// return false;
		// }
	}

	public DefaultActivity getDefaultActivity() {
		return defaultActivity;
	}

	public Configuration getConfigurationActivity() {
		return configurationActivity;
	}

	public boolean getDefaultActivityRunning() {
		try {
			ActivityInfo[] list = mAppContext.getPackageManager()
					.getPackageInfo(mAppContext.getPackageName(),
							PackageManager.GET_ACTIVITIES).activities;
			for (int i = 0; i < list.length; i++) {
				if (list[i].name == "org.durka.hallmonitor.DefaultActivity") {
					return true;
				}
			}
		} catch (NameNotFoundException e1) {
		}
		return false;
	}

	public boolean getConfigurationActivityRunning() {
		try {
			ActivityInfo[] list = mAppContext.getPackageManager()
					.getPackageInfo(mAppContext.getPackageName(),
							PackageManager.GET_ACTIVITIES).activities;
			for (int i = 0; i < list.length; i++) {
				if (list[i].name == "org.durka.hallmonitor.DefaultActivity") {
					return true;
				}
			}
		} catch (NameNotFoundException e1) {
		}
		return false;
	}

	public void closeDefaultActivity() {
		if (defaultActivity != null) {
			defaultActivity.finish();
		}
	}

	public void closeConfigurationActivity() {
		if (configurationActivity != null) {
			configurationActivity.finish();
		}
	}

	public void closeAllActivity() {
		closeDefaultActivity();
		closeConfigurationActivity();
	}

	public void freeDevice(Context context) {
		closeAllActivity();
		Intent mIntent = new Intent(context, CoreService.class);
		mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_CANCEL_BLACKSCREEN);
		context.startService(mIntent);
	}

	public AudioManager getAudioManager() {
		return audioManager;
	}

	/**
	 * Hand off to the HMAppWidgetManager to deal with registering new app
	 * widget.
	 * 
	 * @param act
	 *            The Activity to use as the context for these actions
	 * @param widgetType
	 *            The type of widget (e.g. 'default', 'media', 'notification'
	 *            etc.)
	 */
	public void register_widget(String widgetType) {

		Log.d(LOG_TAG + ".register_widget", "Register widget called for type: "
				+ widgetType);
		// hand off to the HM App Widget Manager for processing
		if (widget_settings_ongoing) {
			Log.d(LOG_TAG + ".register_widget", "skipping, already inflight");
		} else {
			hmAppWidgetManager.registerWidget(widgetType);
		}
	}

	/**
	 * Hand off to the HMAppWidgetManager to deal with unregistering existing
	 * app widget.
	 * 
	 * @param act
	 *            The Activity to use as the context for these actions
	 * @param widgetType
	 *            The type of widget (e.g. 'default', 'media', 'notification'
	 *            etc.)
	 */
	public void unregister_widget(String widgetType) {

		Log.d(LOG_TAG + ".unregister_widget",
				"unregister widget called for type: " + widgetType);
		// hand off to the HM App Widget Manager for processing
		hmAppWidgetManager.unregisterWidget(widgetType);
	}

	public HMAppWidgetManager getHMAppWidgetManager() {
		return hmAppWidgetManager;
	}

	/**
	 * Starts the HallMonitor services.
	 * 
	 */
	public void startServices() {
		Log.d(LOG_TAG + ".start_service", "Start service called.");

		mAppContext.startService(new Intent(mAppContext, CoreService.class));
		if (preference_all.getBoolean("pref_internalservice", false)) {
			if (preference_all.getBoolean("pref_realhall", false)) {
				mAppContext.startService(new Intent(mAppContext,
						ViewCoverHallService.class));
			} else if (preference_all.getBoolean("pref_proximity", false)) {
				mAppContext.startService(new Intent(mAppContext,
						ViewCoverProximityService.class));
			}
		}
		if (preference_all.getBoolean("pref_do_notifications", false)) {
			mAppContext.startService(new Intent(mAppContext,
					NotificationService.class));
		}
		if (getCoverClosed(true)) {
			Intent mIntent = new Intent(mAppContext, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_LAUNCH_ACTIVITY);
			mAppContext.startService(mIntent);
		}
	}

	/**
	 * Stops the HallMonitor service.
	 * 
	 */
	public void stopServices() {
		stopServices(false);
	}

	public void stopServices(boolean override_keep_admin) {

		Log.d(LOG_TAG + ".stop_service", "Stop service called.");

		if (getServiceRunning(ViewCoverHallService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					ViewCoverHallService.class));
		}
		if (getServiceRunning(ViewCoverProximityService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					ViewCoverProximityService.class));
		}
		if (getServiceRunning(NotificationService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					NotificationService.class));
		}
		if (getServiceRunning(CoreService.class)) {
			mAppContext.stopService(new Intent(mAppContext, CoreService.class));
		}

		// Relinquish device admin (unless asked not to)
		if (!override_keep_admin
				&& !preference_all.getBoolean("pref_keep_admin", false)) {
			DevicePolicyManager dpm = (DevicePolicyManager) mAppContext
					.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(mAppContext,
					AdminReceiver.class);
			if (dpm.isAdminActive(me)) {
				dpm.removeActiveAdmin(me);
			}
		}
	}

	/**
	 * Is the service running.
	 * 
	 * @param ctx
	 *            Application context.
	 * @return Is the cover closed.
	 */
	public boolean getServiceRunning(@SuppressWarnings("rawtypes") Class svc) {

		Log.d(LOG_TAG + ".service_running", "Is service running called.");

		ActivityManager manager = (ActivityManager) mAppContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (svc.getName().equals(service.service.getClassName())) {
				// the service is running
				Log.d(LOG_TAG + ".Is.service_running", "The " + svc.getName()
						+ " is running.");
				return true;
			}
		}
		// the service must not be running
		Log.d(LOG_TAG + ".service_running", "The " + svc.getName()
				+ " service is NOT running.");
		return false;
	}

	/**
	 * With this non-CM users can use torch button in HallMonitor. Should
	 * (Hopefully) work on every device with SystemFeature FEATURE_CAMERA_FLASH
	 * This code has been tested on I9505 jflte with ParanoidAndroid 4.4 rc2
	 */

	// Turn On Flash
	public void turnOnFlash() {
		setTorchOn(true);
		camera = Camera.open();
		Parameters p = camera.getParameters();
		p.setFlashMode(Parameters.FLASH_MODE_TORCH);
		camera.setParameters(p);
		camera.startPreview();
		Log.d(LOG_TAG + ".TA.tOnF", "turned on!");
	}

	// Turn Off Flash
	public void turnOffFlash() {
		Parameters p = camera.getParameters();
		p.setFlashMode(Parameters.FLASH_MODE_OFF);
		camera.setParameters(p);
		camera.stopPreview();
		// Be sure to release the camera when the flash is turned off
		if (camera != null) {
			camera.release();
			camera = null;
			Log.d(LOG_TAG + ".TA.tOffF", "turned off and camera released!");
		}
		setTorchOn(false);
	}

	public boolean getDeviceHasFlash() {
		deviceHasFlash = mAppContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA_FLASH);
		return deviceHasFlash;
	}
}