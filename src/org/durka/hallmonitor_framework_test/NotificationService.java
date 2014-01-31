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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.preference.PreferenceManager;
import android.util.Log;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;


public class NotificationService extends NotificationListenerService {

    private final String mTorchNotificationName = "net.cactii.flash2";

	public static NotificationService that = null;
    private static NotificationService runningInstance = null;

    private static boolean mDebug = false;

	private final List<String> blacklist = new ArrayList<String>() {{
			add(mTorchNotificationName); // we have our own flashlight UI
			add("android");           // this covers the keyboard selection notification, but does it clobber others too? TODO
	}};

    private HashSet<OnNotificationChangedListener> mOnNotificationChangedListeners = null;

    public interface OnNotificationChangedListener {
        public void onNotificationChanged();
    }

	@Override
	public void onCreate() {
		Log_d("NS-oC", "ohai");
		that = runningInstance = this;

        mOnNotificationChangedListeners = new HashSet<OnNotificationChangedListener>();
	}
	
	@Override
	public void onDestroy() {
		Log_d("NS-oD", "kthxbai");
		that = runningInstance = null;
	}
	
	@Override
	public synchronized void onNotificationPosted(StatusBarNotification sbn) {
        for (OnNotificationChangedListener onNotificationChangedListener : mOnNotificationChangedListeners) {
            try {
                onNotificationChangedListener.onNotificationChanged();
            } catch (Exception e) {
                Log.e("NS-oNP", "exception occurred! " + e.getMessage());
            }
        }
	}

	@Override
	public synchronized void onNotificationRemoved(StatusBarNotification sbn) {
        for (OnNotificationChangedListener onNotificationChangedListener : mOnNotificationChangedListeners) {
            try {
                onNotificationChangedListener.onNotificationChanged();
            } catch (Exception e) {
                Log.e("NS-oNR", "exception occurred! " + e.getMessage());
            }
        }
	}

    private synchronized void registerOnNotificationChangedListenerPrivate(OnNotificationChangedListener onNotificationChangedListener) {
        mOnNotificationChangedListeners.add(onNotificationChangedListener);
    }

    private synchronized void unregisterOnNotificationChangedListenerPrivate(OnNotificationChangedListener onNotificationChangedListener) {
        mOnNotificationChangedListeners.add(onNotificationChangedListener);
    }

    private void Log_d(String tag, String message) {
        if (PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("pref_dev_opts_debug", false))
            Log.d(tag, message);
    }

    private boolean isTorchOnPrivate() {
        boolean result = false;

        for (StatusBarNotification statusBarNotification : super.getActiveNotifications())
            if ((result = statusBarNotification.getPackageName().equals(mTorchNotificationName)))
                break;

        return result;
    }

	@Override
	public StatusBarNotification[] getActiveNotifications() {
		StatusBarNotification[] notifs = super.getActiveNotifications();
		
		List<StatusBarNotification> acc = new ArrayList<StatusBarNotification>(notifs.length);
		for (StatusBarNotification sbn : notifs) {
//			Log_d("NS-gAN", sbn.getPackageName());
			if (!blacklist.contains(sbn.getPackageName())) {
				acc.add(sbn);
			}
		}
		return acc.toArray(new StatusBarNotification[acc.size()]);
	}

    /**
     * public static functions
     */
    public static boolean isTorchOn() {
        boolean result = false;

        if (runningInstance != null)
            result = runningInstance.isTorchOnPrivate();

        return result;
    }

    public static StatusBarNotification[] getActiveNotificationsStatic() {
        StatusBarNotification[] result = null;

        if (runningInstance != null)
            result = runningInstance.getActiveNotifications();

        return result;
    }

    public static boolean registerOnNotificationChangedListener(OnNotificationChangedListener onNotificationChangedListener) {
        boolean result = false;

        if (result = (runningInstance != null))
            runningInstance.registerOnNotificationChangedListenerPrivate(onNotificationChangedListener);

        return result;
    }

    public static void unregisterOnNotificationChangedListener(OnNotificationChangedListener onNotificationChangedListener) {
        if (runningInstance != null)
            runningInstance.unregisterOnNotificationChangedListenerPrivate(onNotificationChangedListener);
    }

    public static void setDebugMode(boolean debug) {
        mDebug = debug;
    }

}
