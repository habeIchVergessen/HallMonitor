package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by ladmin on 16.01.14.
 */
public class ComponentCamera extends ComponentFramework.Layout {

    final private String LOG_TAG = "ComponentCamera";

    private ImageView mCaptureButton = null;
    private ImageView mCameraBackButton = null;

    public ComponentCamera(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        Log_d(LOG_TAG, "ComponentCamera");

        setLayoutResourceId(R.layout.component_camera_layout);
    }

    public void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");

        mCaptureButton = (ImageView)findViewById(R.id.default_camera_capture);
        mCameraBackButton = (ImageView)findViewById(R.id.default_camera_back);

        mCaptureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "onClick: R.id.default_camera visible doesn't work properly");
                ((CameraPreview)findViewById(R.id.default_camera)).capture();
            }
        });
        mCameraBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setVisibility(INVISIBLE);
            }
        });
    }

    public boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        ComponentFramework.Layout cameraLayout = getContainer().getLayoutByResId(R.id.componentCamera);

        try {
            getChildAt(0).setVisibility(VISIBLE);
            if (cameraLayout != null) {
                Log.d(LOG_TAG, "onOpenComponent: R.id.default_camera visible doesn't work properly");
                cameraLayout.findViewById(R.id.default_camera).setVisibility(VISIBLE);
            }
        } catch (Exception e) {
        }

        return true;
    }

    public void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");

        ComponentFramework.Layout cameraLayout = getContainer().getLayoutByResId(R.id.componentCamera);

        try {
            if (cameraLayout != null) {
                cameraLayout.findViewById(R.id.default_camera).setVisibility(INVISIBLE);
            }
        } catch (Exception e) {
        }
    }
}
