package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.IOException;

public class ComponentDefaultHabeIchVergessen extends ComponentFramework.Layout
        implements ComponentFramework.OnPauseResumeListener, ComponentFramework.MenuController.OnMenuActionListener,
        NotificationService.OnNotificationChangedListener, ComponentFramework.OnGyroscopeChangedListener {

    private final String LOG_TAG = "ComponentDefaultHabeIchVergessen";

    private TextClock mDefaultTextClock = null;
    private Camera mCamera;
    private Object mCameraSync = new Object();

    private final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";

    public ComponentDefaultHabeIchVergessen(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        setLayoutResourceId(R.layout.component_default_habe_ich_vergessen_layout);
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");

        mDefaultTextClock = (TextClock) findViewById(R.id.default_text_clock);

        // TextClock format
        SpannableString spanString = new SpannableString(getResources().getString(R.string.styled_24_hour_clock));
        spanString.setSpan(new RelativeSizeSpan(2.8f), 1, 5, 0);
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 1, 5, 0);
        spanString.setSpan(new RelativeSizeSpan(0.8f), 6, spanString.length(), 0);
        mDefaultTextClock.setFormat24Hour(spanString);

        mDefaultTextClock.setTextColor(getPrefInt("pref_default_fgcolor", 0xffffffff));
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");
    }

    public void onResume() {
        Log_d(LOG_TAG, "onResume");

        if (NotificationService.registerOnNotificationChangedListener(this))
            onNotificationChanged();

        updateBatteryStatus();
        (new GetCameraThread()).start();
    }

    public void onPause() {
        Log_d(LOG_TAG, "onPause");

        NotificationService.unregisterOnNotificationChangedListener(this);
        releaseCamera();
    }

    public int getMenuId() {
        return getId();
    }

    public void onMenuInit(ComponentFramework.MenuController menuController) {
        Log_d(LOG_TAG, "onMenuInit:");

        menuController.registerMenuOption(getMenuId(), R.id.menu_phone_ringer_normal, R.drawable.ic_phone_speaker_on);
        menuController.registerMenuOption(getMenuId(), R.id.menu_phone_ringer_vibrate, R.drawable.ic_phone_vibrate);
        menuController.registerMenuOption(getMenuId(), R.id.menu_phone_ringer_silent, R.drawable.ic_phone_speaker_off);
        menuController.registerMenuOption(getMenuId(), R.id.camerabutton, R.drawable.ic_notification);
        menuController.registerMenuOption(getMenuId(), R.id.torchbutton, R.drawable.ic_appwidget_torch_off);
//        menuController.registerMenuOption(getMenuId(), R.id.menu_test, R.drawable.ic_option_overlay_option1);
    }

    public boolean onMenuOpen(ComponentFramework.MenuController.Menu menu) {
        Log_d(LOG_TAG, "onMenuOpen: " + menu.getId() + ", " + menu.getOptions().toString());

        AudioManager audioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);

        for (int optionId : menu.getOptions()) {
            ComponentFramework.MenuController.Option option = menu.getOption(optionId);

            switch (option.getId()) {
                case R.id.menu_phone_ringer_normal:
                    Log_d(LOG_TAG, "onMenuOpen: menu_phone_ringer_normal " + (audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL));
                    option.setEnabled(audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
                    break;
                case R.id.menu_phone_ringer_vibrate:
                    Log_d(LOG_TAG, "onMenuOpen: menu_phone_ringer_vibrate " + (audioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE));
                    option.setEnabled(audioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE);
                    break;
                case R.id.menu_phone_ringer_silent:
                    Log_d(LOG_TAG, "onMenuOpen: menu_phone_ringer_silent " + (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT));
                    option.setEnabled(audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT);
                    break;
                case R.id.torchbutton:
                    // read notifications for torch state
                    boolean torchOn = (Build.VERSION.SDK_INT < 21 ? NotificationService.isTorchOn() : isFlashOn());

                    option.setImageId(!torchOn ? R.drawable.ic_appwidget_torch_off : R.drawable.ic_appwidget_torch_on);
                    break;
            }
        }
        return true;
    }

    public void onMenuAction(ComponentFramework.MenuController.MenuOption menuOption) {
        Log_d(LOG_TAG, "onMenuAction: " + menuOption.getOptionId());

        AudioManager audioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);

        switch (menuOption.getOptionId()) {
            case R.id.camerabutton:
                releaseCamera();
                getContainer().getLayoutByResId(R.id.componentCamera).setVisibility(VISIBLE);
                break;
            case R.id.torchbutton:
                boolean torchOn = false;

                if (Build.VERSION.SDK_INT < 21) {
                    Intent intent = new Intent(TOGGLE_FLASHLIGHT);
                    intent.putExtra("strobe", false);
                    intent.putExtra("period", 100);
                    intent.putExtra("bright", false);
                    ((Activity) getContext()).sendBroadcast(intent);

                    torchOn = (menuOption.getImageId() == R.drawable.ic_appwidget_torch_off);
                } else {
                    if (!isFlashOn())
                        turnFlashOn();
                    else
                        turnFlashOff();

                    torchOn = isFlashOn();
                }

                menuOption.setImageId((torchOn ? R.drawable.ic_appwidget_torch_on : R.drawable.ic_appwidget_torch_off));

                if (torchOn)
                    ViewCoverService.registerOnGyroscopeChangedListener(this);
                else
                    ViewCoverService.unregisterOnGyroscopeChangedListener(this);
                break;
            case R.id.menu_phone_ringer_normal:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                break;
            case R.id.menu_phone_ringer_vibrate:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                break;
            case R.id.menu_phone_ringer_silent:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                break;
            case R.id.menu_test:
                Log_d(LOG_TAG, "onMenuAction:\n" + getContainer().dumpBackStack());
//                ((ComponentTestActivity)getActivity()).onShowComponentPhone(this);
                break;
        }
    }

    /**
     * OnNotificationChanged
     */
    public synchronized void onNotificationChanged() {
//        Log_d(LOG_TAG, "onNotificationChanged");

        StatusBarNotification[] notifs = NotificationService.getActiveNotificationsStatic();

        if (notifs != null) {
//            Log_d(LOG_TAG, "onNotificationChanged: service is running");
            final GridView grid = (GridView)findViewById(R.id.default_icon_container);

            if (grid == null) {
                Log_d(LOG_TAG, "onNotificationChanged: no grid found");
                return;
            }

            if (!(grid.getAdapter() instanceof NotificationService)) {
                final NotificationAdapter nA = new NotificationAdapter(getContext(), notifs);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            grid.setNumColumns(nA.getCount());
                            grid.setAdapter(nA);
                        } catch (Exception e) {
                            Log_d(LOG_TAG, "onNotificationChanged: exception occurred! " + e.getMessage());
                        }
                    }
                });
            } else {
                final NotificationAdapter adapter = (NotificationAdapter)grid.getAdapter();
                adapter.update(notifs);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            grid.setNumColumns(adapter.getCount());
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Log_d(LOG_TAG, "onNotificationChanged: exception occurred! " + e.getMessage());
                        }
                    }
                });
            }
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

    private Camera getCamera() {
        Log_d(LOG_TAG, "getCamera");
        synchronized (mCameraSync) {
            try {
                if (mCamera == null)
                    mCamera = Camera.open();
            } catch (RuntimeException e) {
                Log_d(LOG_TAG, "getCamera: RuntimeException: " + e.getMessage());
                mCamera = null;
            }
        }

        return mCamera;
    }

    private void releaseCamera() {
        Log_d(LOG_TAG, "releaseCamera");
        synchronized (mCameraSync) {
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
        }
    }

    private Camera.Parameters getCameraParameter() throws RuntimeException {
        Camera camera = getCamera();

        return (camera != null ? camera.getParameters() : null);
    }

    private boolean hasFlashSupport() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    private boolean isFlashOn() {
        String flashMode = null;

        try {
            Camera.Parameters params = getCameraParameter();
            flashMode = (params != null ? params.getFlashMode() : null);
        } catch (RuntimeException e) {
            Log_d(LOG_TAG, "isFlashOn: " + e.getMessage());
        }

        return (Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
    }

    private void turnFlashOn() {
        Camera camera;

        if ((camera = getCamera()) != null) {
            Camera.Parameters params;

            if ((params = camera.getParameters()) != null) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(params);
                camera.startPreview();
                stopScreenOffTimer();
            }
        }
    }

    private void turnFlashOff() {
        Camera camera;

        if ((camera = getCamera()) != null) {
            Camera.Parameters params;

            if ((params = camera.getParameters()) != null) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(params);
                camera.stopPreview();
                startScreenOffTimer();
            }
        }
    }

    private void updateBatteryStatus() {
        Log_d(LOG_TAG, "updateBatteryStatus");
        if (findViewById(R.id.default_battery_picture) != null) {
            Intent battery_status = getActivity().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (   battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                    && !(battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL)) {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery_charge);
            } else {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery);
            }
            ((ImageView)findViewById(R.id.default_battery_picture)).getDrawable().setLevel((int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100));
        }
    }

    private class GetCameraThread extends Thread {
        private final String LOG_TAG = "GetCameraThread";
        public GetCameraThread() {
        }

        @Override
        public void run() {
            getCamera();
        }
    }
}
