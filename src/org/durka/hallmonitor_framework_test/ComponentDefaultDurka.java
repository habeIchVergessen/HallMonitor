package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;

public class ComponentDefaultDurka extends ComponentFramework.Layout implements ComponentFramework.OnPauseResumeListener {

    private final String LOG_TAG = "ComponentDefaultDurka";

    private ImageView mCaptureButton = null;
    private ImageView mTorchButton = null;
    private TextClock mDefaultTextClock = null;

    private final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";

    private boolean torchIsOn = false;

    public ComponentDefaultDurka(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        setLayoutResourceId(R.layout.component_default_durka_layout);
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");

        mCaptureButton = (ImageView)findViewById(R.id.camerabutton);
        mTorchButton = (ImageView)findViewById(R.id.torchbutton);

        mCaptureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getContainer().getLayoutByResId(R.id.componentCamera).setVisibility(VISIBLE);
            }
        });
        mTorchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(TOGGLE_FLASHLIGHT);
                intent.putExtra("strobe", false);
                intent.putExtra("period", 100);
                intent.putExtra("bright", false);
                ((Activity)getContext()).sendBroadcast(intent);
                torchIsOn = !torchIsOn;
                if (torchIsOn) {
                    mTorchButton.setImageResource(R.drawable.ic_appwidget_torch_on);
                } else {
                    mTorchButton.setImageResource(R.drawable.ic_appwidget_torch_off);
                }
            }
        });

        mDefaultTextClock = (TextClock) findViewById(R.id.default_text_clock);
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

    public void onPause() {
        Log_d(LOG_TAG, "onPause");
    }

    public void onResume() {
        Log_d(LOG_TAG, "onResume");
    }

    private void updateBatteryStatus() {
        if (findViewById(R.id.default_battery_picture) != null) {
            Intent battery_status = getActivity().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            if (   battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                    || (battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL)) {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery_charge);
            } else {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery);
            }
            ((ImageView)findViewById(R.id.default_battery_picture)).getDrawable().setLevel((int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100));
        }
    }
}
