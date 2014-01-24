package org.durka.hallmonitor;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class NotificationAdapter extends BaseAdapter {
	
	private StatusBarNotification[] notifs;
	private Context that;

    private final int numOfItems = 10;
	
	public NotificationAdapter(Context ctx, StatusBarNotification[] n) {
		that = ctx;
		notifs = n;
	}
	
	public void update(StatusBarNotification[] n) {
		notifs = n;
		Log_d("NA.upd", "update: " + Integer.toString(n.length) + " notifications");
	}

	@Override
	public int getCount() {
		return (notifs.length < numOfItems ? numOfItems  + (notifs.length % 2) : notifs.length + (isAirPlaneMode() ? 1 : 0));
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convert, ViewGroup parent) {
		ImageView view;
		if (convert != null) {
			view = (ImageView)convert;
		} else {
			view = new ImageView(that);
			//view.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, GridView.LayoutParams.MATCH_PARENT));
            //view.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.WRAP_CONTENT, GridView.LayoutParams.WRAP_CONTENT));
            view.setLayoutParams(new GridView.LayoutParams(30, 30));
			view.setScaleType(ImageView.ScaleType.FIT_CENTER);
			view.setPadding(0, 0, 0, 0);
			try {
                if (notifs.length > 0) {
                    int offset = Math.round((getCount() - notifs.length) / 2);
                    if (position >= offset && position < offset + notifs.length) {
                        StatusBarNotification sBN = notifs[position - offset];
				        view.setImageDrawable(that.createPackageContext(sBN.getPackageName(), 0).getResources().getDrawable(sBN.getNotification().icon));
                    }
                }
                if (position == getCount() - 1) {
                    if (isAirPlaneMode())
                        view.setImageDrawable(that.getResources().getDrawable(R.drawable.ic_phone_flight_mode));
                    else if (isNetworkEnabled(ConnectivityManager.TYPE_WIFI))
                        view.setImageDrawable(that.getResources().getDrawable(R.drawable.ic_phone_wifi_100));
                    else if (isNetworkEnabled(ConnectivityManager.TYPE_MOBILE))
                        view.setImageDrawable(that.getResources().getDrawable(R.drawable.ic_phone_mobile_data));
                }
			} catch (NotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return view;
	}

    private boolean isAirPlaneMode() {
        return Settings.Global.getInt(that.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private boolean isNetworkEnabled(int connectionType) {
        ConnectivityManager connectivityManager = (ConnectivityManager)that.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null || (connectionType != ConnectivityManager.TYPE_MOBILE && connectionType != ConnectivityManager.TYPE_WIFI))
            return false;

        return (connectivityManager.getNetworkInfo(connectionType).getState() == NetworkInfo.State.CONNECTED);
    }

    private void Log_d(String tag, String message) {
        if (DefaultActivity.isDebug())
            Log.d(tag, message);
    }
}
