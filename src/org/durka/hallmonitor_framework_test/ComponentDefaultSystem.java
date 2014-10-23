package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ComponentDefaultSystem extends ComponentFramework.Layout
        implements ComponentFramework.OnPauseResumeListener, ComponentFramework.MenuController.OnMenuActionListener,
        ComponentFramework.OnGyroscopeChangedListener {

    private final String LOG_TAG = "ComponentDefaultSystem";

    private final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";

    public ComponentDefaultSystem(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");

        setBackgroundColor(0x00000000);
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
    }

    public void onPause() {
        Log_d(LOG_TAG, "onPause");
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
                    option.setImageId(!NotificationService.isTorchOn() ? R.drawable.ic_appwidget_torch_off : R.drawable.ic_appwidget_torch_on);

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
                getContext().sendBroadcast(intent);

                boolean torchOn = (menuOption.getImageId() == R.drawable.ic_appwidget_torch_off);

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
                break;
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

}
