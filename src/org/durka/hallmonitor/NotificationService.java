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

import android.preference.PreferenceManager;
import android.util.Log;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;


public class NotificationService extends NotificationListenerService {
	
	public static NotificationService that = null;
	
	@Override
	public void onCreate() {
		Log_d("NS-oC", "ohai");
		that = this;
	}
	
	@Override
	public void onDestroy() {
		Log_d("NS-oD", "kthxbai");
		that = null;
	}
	
	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		Log_d("NS-oNP", "notification posted: " + sbn.toString());
		if (DefaultActivity.on_screen) {
			Functions.Actions.refresh_notifications();
		}
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log_d("NS-oNR", "notification removed: " + sbn.toString());
		if (DefaultActivity.on_screen) {
			Functions.Actions.refresh_notifications();
		}
	}
	
	private void Log_d(String tag, String message) {
        if (PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("pref_dev_opts_debug", false))
            Log.d(tag, message);
    }
}
