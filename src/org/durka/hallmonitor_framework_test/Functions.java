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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Container Class for various inner Classes which service the capabilities of the HallMonitor app.
 */
public class Functions {
	
	public static final int DEVICE_ADMIN_WAITING = 42;
	
	//needed for the call backs from the widget picker
	//pick is the call back after picking a widget, configure is the call back after
	//widget configuration
	public static final int REQUEST_PICK_APPWIDGET = 9;
	public static final int REQUEST_CONFIGURE_APPWIDGET = 5;
    public static final int NOTIFICATION_LISTENER_ON = 0xDEAD;
    public static final int NOTIFICATION_LISTENER_OFF = 0xBEEF;

    private static boolean mDebug = false;
    private static boolean notification_settings_ongoing = false;
    public static boolean widget_settings_ongoing = false;

    private static final String DEV_SERRANO_LTE_CM10 = "serranolte";    // GT-I9195 CM10.x
    private static final String DEV_SERRANO_LTE_CM11 = "serranoltexx";  // GT-I9195 CM11.x

	//Class that handles interaction with 3rd party App Widgets
	public static final HMAppWidgetManager hmAppWidgetManager = new HMAppWidgetManager();

    public static void setDebugMode(boolean debug) {
        mDebug = debug;
    }

	/**
	 * Provides methods for performing actions. (e.g. what to do when the cover is opened and closed etc.)
	 */
	public static class Actions {
        private static final String LOG_TAG = "F.Act";
		
		// used for the timer to turn off the screen on a delay
        private static Timer timer = new Timer();
        private static TimerTask timerTaskScreenOff;

        public static void setTouchScreenCoverMode(Context ctx, boolean coverMode) {
            //if we are running in root enabled mode then lets up the sensitivity on the view screen
            //so we can use the screen through the window
            if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_runasroot", false)) {
                Log_d(LOG_TAG, "setTouchScreenCoverMode: " + coverMode);

                if (coverMode) {
                    Log_d(LOG_TAG + "setTouchScreenCoverMode", "We're root enabled so lets boost the sensitivity... (Build.Device: '" + Build.DEVICE + "')");

                    if (Build.DEVICE.equals(DEV_SERRANO_LTE_CM10) || Build.DEVICE.equals(DEV_SERRANO_LTE_CM11)) {
                        run_commands_as_root(new String[]{
                            "echo module_on_master > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result"
                        ,   "echo clear_cover_mode,3 > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result"}
                        );
                    } else // others devices
                        run_commands_as_root(new String[]{"echo clear_cover_mode,1 > /sys/class/sec/tsp/cmd"});

                    Log_d(LOG_TAG + "setTouchScreenCoverMode", "...Sensitivity boosted, hold onto your hats!");
                } else {
                    Log_d(LOG_TAG + "setTouchScreenCoverMode", "We're root enabled so lets revert the sensitivity...");

                    if (Build.DEVICE.equals(DEV_SERRANO_LTE_CM10) || Build.DEVICE.equals(DEV_SERRANO_LTE_CM11)) {
                        run_commands_as_root(new String[]{
                            "echo module_on_master > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result"
                        ,   "echo clear_cover_mode,0 > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result"}
                        );
                    } else // others devices
                        run_commands_as_root(new String[]{"echo clear_cover_mode,0 > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result"});

                    Log_d(LOG_TAG + "setTouchScreenCoverMode", "...Sensitivity reverted, sanity is restored!");
                }
            }
        }

		/**
		 * ScreenOnTimer
		 */
		public static void rearmScreenOffTimer(Context ctx) {
			boolean coverClosed = ViewCoverService.isCoverClosed();
			
			// don't let run more than 1 timer
			stopScreenOffTimer("called from rearmScreenOffTimer");
			
			Log_d(LOG_TAG + ".rearmScreenOffTimer", "cover_closed = " + coverClosed);
			
			if (!coverClosed)
				return;

            //need this to let us lock the phone
			final DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
			//final PowerManager pm = (PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
			
			ComponentName me = new ComponentName(ctx, AdminReceiver.class);
			if (!dpm.isAdminActive(me)) {
				// if we're not an admin, we can't do anything
				Log_d(LOG_TAG + ".rearmScreenOffTimer", "We are not an admin so cannot do anything.");
				return;
			}

            //step 2: wait for the delay period and turn the screen off
            int delay = PreferenceManager.getDefaultSharedPreferences(ctx).getInt("pref_delay", 10000);
            
            Log_d(LOG_TAG + ".rearmScreenOffTimer", "Delay set to: " + delay);

            //using the handler is causing a problem, seems to lock up the app, hence replaced with a Timer
            timer.schedule(timerTaskScreenOff = new TimerTask() {
			//handler.postDelayed(new Runnable() {
				@Override
				public void run() {	
					Log_d(LOG_TAG + ".rearmScreenOffTimer", "Locking screen now.");
					dpm.lockNow();
					//FIXME Would it be better to turn the screen off rather than actually locking
					//presumably then it will auto lock as per phone configuration
					//I can't work out how to do it though!
				}
			}, delay);
		}
		
		public static void stopScreenOffTimer() {
			stopScreenOffTimer(null);
		}
		
		public static void stopScreenOffTimer(String info) {
			Log_d(LOG_TAG + ".stopScreenOffTimer", "active: " + (timerTaskScreenOff != null) + (info != null ? " (" + info + ")" : ""));
			if (timerTaskScreenOff != null) {
				timerTaskScreenOff.cancel();
				timerTaskScreenOff = null;
			}
		}

        @SuppressWarnings("deprecation")
        public static void wakeUpScreen(Context ctx) {
            //needed to let us wake the screen
            PowerManager pm  = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);

            if (!pm.isScreenOn()) {
                Log_d(LOG_TAG, "wakeUpScreen");
                //FIXME Would be nice to remove the deprecated FULL_WAKE_LOCK if possible
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, ctx.getString(R.string.app_name));
                wl.acquire();
                wl.release();
            }
        }
		
		/**
		 * Starts the HallMonitor service. Service state is dependent on admin permissions.
		 * This requests admin permissions. The onActionReceiver will pick that up 
		 * and do the necessary to start the service. 
		 * @param act Activity context.
		 */
		public static void start_service(Activity act) {
			Log_d(LOG_TAG + ".start_service", "Start service called.");
			// Become device admin
			DevicePolicyManager dpm = (DevicePolicyManager) act.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(act, AdminReceiver.class);
            Log_d(LOG_TAG, "start_service: component name = '" + me.toString() + "'");
			if (!dpm.isAdminActive(me)) {
				Intent coup = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				coup.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, me);
				coup.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, act.getString(R.string.admin_excuse));
				act.startActivityForResult(coup, DEVICE_ADMIN_WAITING);
			}
		}
		
		/**
		 * Stops the HallMonitor service.
		 * @param ctx Application context.
		 */
		public static void stop_service(Context ctx) {
			
			Log_d(LOG_TAG + ".stop_service", "Stop service called.");
			
			ctx.stopService(new Intent(ctx, ViewCoverService.class));
			ctx.stopService(new Intent(ctx, NotificationService.class));
			
			// Relinquish device admin
			DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(ctx, AdminReceiver.class);
			if (dpm.isAdminActive(me)) dpm.removeActiveAdmin(me);
		}

        public static void do_notifications(Activity act, boolean enable) {

            if (enable && !notification_settings_ongoing && !Is.service_running(act.getBaseContext(), NotificationService.class)) {
                notification_settings_ongoing = true;
                Toast.makeText(act, act.getString(R.string.notif_please_check), Toast.LENGTH_SHORT).show();
                act.startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), NOTIFICATION_LISTENER_ON);
            } else if (!enable && !notification_settings_ongoing && Is.service_running(act.getBaseContext(), NotificationService.class)) {
                notification_settings_ongoing = true;
                Toast.makeText(act, act.getString(R.string.notif_please_uncheck), Toast.LENGTH_SHORT).show();
                act.startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), NOTIFICATION_LISTENER_OFF);
            }

        }

        /**
		 * Hand off to the HMAppWidgetManager to deal with registering new app widget.
		 * @param act The Activity to use as the context for these actions
		 * @param widgetType The type of widget (e.g. 'default', 'media', 'notification' etc.)
		 */
		public static void register_widget(Activity act, String widgetType) {
			
			Log_d(LOG_TAG + ".register_widget", "Register widget called for type: " + widgetType);
			//hand off to the HM App Widget Manager for processing
			hmAppWidgetManager.register_widget(act, widgetType);
		}
		
		/**
		 * Hand off to the HMAppWidgetManager to deal with unregistering existing app widget.
		 * @param act The Activity to use as the context for these actions
		 * @param widgetType The type of widget (e.g. 'default', 'media', 'notification' etc.)
		 */
		public static void unregister_widget(Activity act, String widgetType) {
			
			Log_d(LOG_TAG + ".unregister_widget", "unregister widget called for type: " + widgetType);
			//hand off to the HM App Widget Manager for processing
			hmAppWidgetManager.unregister_widget(act, widgetType);
		}
		
		
		/**
		 * Execute shell commands
		 * @param cmds Commands to execute
		 */
		public static String run_commands_as_root(String[] cmds) {
            return run_commands_as_root(cmds, true);
        }
		
		public static String run_commands_as_root(String[] cmds, boolean want_output) {
            String result = "";

	        try {
	        	Process p = Runtime.getRuntime().exec("su");
	        	
	        	//create output stream for running commands
	            DataOutputStream os = new DataOutputStream(p.getOutputStream());  
	            
	            //tap into the output
	            InputStream is = p.getInputStream();
	            BufferedReader isBr = new BufferedReader(new InputStreamReader(is));
	            
	            //tap into the error output
	            InputStream es = p.getErrorStream();
	            BufferedReader esBr = new BufferedReader(new InputStreamReader(es));
	            
	            //use this for collating the output
	            String currentLine;
	            
	            //run commands
	            for (String tmpCmd : cmds) {
	            	Log_d(LOG_TAG + ".run_comm_as_root", "Running command: " + tmpCmd);
                    os.writeBytes(tmpCmd+"\n");
	            }      
	            os.writeBytes("exit\n");  
	            os.flush();
	            
	            if (want_output) {
		            //log out the output
		            String output = "";
		            while ((currentLine = isBr.readLine()) != null) {
		              output += currentLine + "\n";
		            } 
		            Log_d(LOG_TAG + ".run_comm_as_root", "Have output: " + output);
	           
		            //log out the error output
		            String error = "";
		            currentLine = "";
		            while ((currentLine = esBr.readLine()) != null) {
		              error += currentLine + "\n";
		            }	           
		            Log_d(LOG_TAG + ".run_comm_as_root", "Have error: " + error);

                    result = output.trim();
                }
	        } catch (IOException ioe) {
	        	Log.e(LOG_TAG + ".run_comm_as_root","Failed to run command!", ioe);
	        }

            return result;
		}


		public static void hangup_call() {
			Log_d("phone", "hanging up! goodbye");
			run_commands_as_root(new String[]{"input keyevent 6"}, false);
		}
		
		public static void pickup_call() {
			Log_d("phone", "picking up! hello");
			run_commands_as_root(new String[]{"input keyevent 5"}, false);
		}
		
		public static void debug_notification(Context ctx, boolean showhide) {
			if (showhide) {
				Notification.Builder mBuilder =
				        new Notification.Builder(ctx)
				        .setSmallIcon(R.drawable.ic_launcher)
				        .setContentTitle("Hall Monitor")
				        .setContentText("Debugging is fun!");

				NotificationManager mNotificationManager =
				    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManager.notify(42, mBuilder.build());
			} else {
				NotificationManager mNotificationManager =
					    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
					mNotificationManager.cancel(42);
			}
		}

        private static void Log_d(String tag, String msg) {
            if (mDebug)
                Log.d(tag, msg);
        }
	}

	/**
	 * Provides event handling.
	 */
	public static class Events {
        private static final String LOG_TAG = "F.Evt";

		/**
		 * Invoked from the BootReceiver, allows for start on boot, as is registered in the manifest as listening for:
		 * android.intent.action.BOOT_COMPLETED and
		 * android:name="android.intent.action.QUICKBOOT_POWERON" 
		 * Starts the ViewCoverService which handles detection of the cover state.
		 * @param ctx Application context
		 */
		public static void boot(Context ctx) {
			
			Log_d(LOG_TAG + ".boot", "Boot called.");
			
			if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", false)) {
	    		Intent startServiceIntent = new Intent(ctx, ViewCoverService.class);
	    		ctx.startService(startServiceIntent);
	    	}
        }
		
		/**
		 * The Configuration activity acts as the main activity for the app. Any events received into
		 * its onActivityReceived method are passed on to be handled here.
		 * @param ctx Application context.
		 * @param request Request Activity ID.
		 * @param result Result Activity ID.
		 * @param data Intent that holds the data received.
		 */
		public static void activity_result(Context ctx, int request, int result, Intent data) {
			Log_d(LOG_TAG + ".activity_result", "Activity result received: request=" + Integer.toString(request) + ", result=" + Integer.toString(result));
			switch (request) {
                //call back for admin access request
                case DEVICE_ADMIN_WAITING:
                if (result == Activity.RESULT_OK) {
                    // we asked to be an admin and the user clicked Activate
                    // (the intent receiver takes care of showing a toast)
                    // go ahead and start the service
                    ctx.startService(new Intent(ctx, ViewCoverService.class));
                } else {
                    // we asked to be an admin and the user clicked Cancel (why?)
                    // complain, and un-check pref_enabled
                    Toast.makeText(ctx, ctx.getString(R.string.admin_refused), Toast.LENGTH_SHORT).show();
                    Log_d(LOG_TAG + ".activity_result", "pref_enabled = " + Boolean.toString(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", true)));
                    PreferenceManager.getDefaultSharedPreferences(ctx)
                            .edit()
                            .putBoolean("pref_enabled", false)
                            .commit();
                    Log_d(LOG_TAG + ".activity_result", "pref_enabled = " + Boolean.toString(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", true)));
                }
                break;
                //call back for appwidget pick
                case REQUEST_PICK_APPWIDGET:
                //widget picked
                if (result == Activity.RESULT_OK) {
                    //widget chosen so launch configurator
                    hmAppWidgetManager.configureWidget(data, ctx);
                } else {
                    //choose dialog cancelled so clean up
                    int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    if (appWidgetId != -1) {
                        hmAppWidgetManager.deleteAppWidgetId(appWidgetId);
                    }
                }
                break;
                //call back for appwidget configure
                case REQUEST_CONFIGURE_APPWIDGET:
                widget_settings_ongoing = false;
                //widget configured
                if (result == Activity.RESULT_OK) {
                    //widget configured successfully so create it
                    hmAppWidgetManager.createWidget(data, ctx);
                } else {
                    //configure dialog cancelled so clean up
                    if (data != null) {
                        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                        if (appWidgetId != -1) {
                            hmAppWidgetManager.deleteAppWidgetId(appWidgetId);
                            PreferenceManager.getDefaultSharedPreferences(ctx)
                                    .edit()
                                    .putBoolean("pref_" + hmAppWidgetManager.currentWidgetType + "_widget", false) // FIXME this is a huge hack
                                    .commit();
                        }
                    }

                }
                break;

                case NOTIFICATION_LISTENER_ON:
                    Log_d("F-oAR", "return from checking the box");
                    notification_settings_ongoing = false;
                    if (!Functions.Is.service_running(ctx, NotificationService.class)) {
                        Toast.makeText(ctx, ctx.getString(R.string.notif_left_unchecked), Toast.LENGTH_SHORT).show();
                        PreferenceManager.getDefaultSharedPreferences(ctx)
                                .edit()
                                .putBoolean("pref_do_notifications", false)
                                .commit();
                    }
                    break;
                case NOTIFICATION_LISTENER_OFF:
                    Log_d("F-oAR", "return from unchecking the box");
                    notification_settings_ongoing = false;
                    if (Functions.Is.service_running(ctx, NotificationService.class)) {
                        Toast.makeText(ctx, ctx.getString(R.string.notif_left_checked), Toast.LENGTH_SHORT).show();
                        PreferenceManager.getDefaultSharedPreferences(ctx)
                                .edit()
                                .putBoolean("pref_do_notifications", true)
                                .commit();
                    }
                    break;
			}
		}
		
		/**
		 * Invoked via the AdminReceiver when the admin status changes.
		 * @param ctx Application context.
		 * @param admin Is the admin permission granted.
		 */
		public static void device_admin_status(Context ctx, boolean admin) {

			Log_d(LOG_TAG + ".dev_adm_status", "Device admin status called with admin status: " + admin);
			
			Toast.makeText(ctx, ctx.getString(admin ? R.string.admin_granted : R.string.admin_revoked), Toast.LENGTH_SHORT).show();
			
			//FIXME none of the below seems to actually be necessary?
			/*if (admin) {
				if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", false) && Is.cover_closed(ctx)) {
					Actions.close_cover(ctx);
				}
			} else {
				Actions.stop_service(ctx);
				PreferenceManager.getDefaultSharedPreferences(ctx)
						.edit()
						.putBoolean("pref_enabled", false)
						.commit();
			}*/
			
			
		}
		
        private static void Log_d(String tag, String msg) {
            if (mDebug)
                Log.d(tag, msg);
        }
	}
	
	/**
	 * Contains methods to check the state
	 */
	public static class Is {
        private static final String LOG_TAG = "F.Is";

		/**
		 * Is the service running.
		 * @param ctx Application context.
		 * @return Is the cover closed.
		 */
        public static boolean service_running(Context ctx, Class svc) {

            Log_d("F.Is.service_running", "Is service running called.");

            ActivityManager manager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (svc.getName().equals(service.service.getClassName())) {
                    // the service is running
                    Log_d("F.Is.service_running", "The " + svc.getName() + " is running.");
                    return true;
                }
            }
            // the service must not be running
            Log_d("F.Is.service_running", "The " + svc.getName() + " service is NOT running.");
            return false;
        }
		
		/**
		 * Is the specified widget enabled
		 * @param ctx Application context
		 * @param widgetType Widget type to check for
		 * @return True if it is, False if not
		 */
		public static boolean widget_enabled(Context ctx, String widgetType) {
			
			Log_d(LOG_TAG + ".wid_enabled", "Is default widget enabled called with widgetType: " + widgetType);
			
			boolean widgetEnabled = Functions.hmAppWidgetManager.doesWidgetExist(widgetType);
			
			Log_d(LOG_TAG + ".wid_enabled", widgetType + " widget enabled state is: " + widgetEnabled);
			
			return widgetEnabled;
		}

        private static void Log_d(String tag, String msg) {
            if (mDebug)
                Log.d(tag, msg);
        }
	}
}
