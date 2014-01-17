package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class ComponentDefaultDurka extends ComponentFramework.Layout {

    private final String LOG_TAG = "ComponentDefaultDurka";

    private ImageView mCaptureButton = null;
    private ImageView mTorchButton = null;

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
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");
        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");
    }
}
