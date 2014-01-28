package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextClock;

public class ComponentDefaultHabeIchVergessen extends ComponentFramework.Layout implements ComponentFramework.OnPauseResumeListener, ComponentFramework.MenuController.OnMenuActionListener, NotificationService.OnNotificationChangedListener {

    private final String LOG_TAG = "ComponentDefaultHabeIchVergessen";

    private TextClock mDefaultTextClock = null;

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

        updateBatteryStatus();
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

        if (NotificationService.that != null) {
            NotificationService.that.registerOnNotificationChangedListener(this);
            onNotificationChanged();
        }
    }

    public void onPause() {
        Log_d(LOG_TAG, "onPause");

        if (NotificationService.that != null)
            NotificationService.that.unregisterOnNotificationChangedListener(this);
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
                    if (NotificationService.that != null) {
                        boolean isTorchOn = NotificationService.that.isTorchOn();
                        option.setImageId(!isTorchOn ? R.drawable.ic_appwidget_torch_off : R.drawable.ic_appwidget_torch_on);
                    }
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
                getContainer().getLayoutByResId(R.id.componentCamera).setVisibility(VISIBLE);
                break;
            case R.id.torchbutton:
                Intent intent = new Intent(TOGGLE_FLASHLIGHT);
                intent.putExtra("strobe", false);
                intent.putExtra("period", 100);
                intent.putExtra("bright", false);
                ((Activity)getContext()).sendBroadcast(intent);

                menuOption.setImageId((menuOption.getImageId() == R.drawable.ic_appwidget_torch_off ? R.drawable.ic_appwidget_torch_on : R.drawable.ic_appwidget_torch_off));
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
        }
    }

    /**
     * OnNotificationChanged
     */
    public void onNotificationChanged() {
        Log_d(LOG_TAG, "onNotificationChanged");

        if (NotificationService.that != null) {
            Log_d(LOG_TAG, "onNotificationChanged: service is running");
            final GridView grid = (GridView)findViewById(R.id.default_icon_container);

            if (grid == null) {
                Log_d(LOG_TAG, "onNotificationChanged: no grid found");
                return;
            }

            if (!(grid.getAdapter() instanceof NotificationService)) {
                StatusBarNotification[] notifs = NotificationService.that.getActiveNotifications();

                final NotificationAdapter nA = new NotificationAdapter(getContext(), notifs);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        grid.setNumColumns(nA.getCount());
                        grid.setAdapter(nA);
                    }
                });
            } else {
                final NotificationAdapter adapter = (NotificationAdapter)grid.getAdapter();
                adapter.update(NotificationService.that.getActiveNotifications());
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        grid.setNumColumns(adapter.getCount());
                        adapter.notifyDataSetChanged();
                    }
                });
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
}
