package org.durka.hallmonitor;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class ComponentPhone extends ComponentFramework.Layout {

    private final String LOG_TAG = "ComponentPhone";

    public ComponentPhone(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        Log_d(LOG_TAG, "onCreate");

        // setup layout resource id (if not loaded via styled attrs)
        if (mLayoutResId == UNDEFINED_LAYOUT) {
            Log_d(LOG_TAG, "setup layout resource id");
            setLayoutResourceId(R.layout.component_phone_habe_ich_vergessen_layout);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        Log_d(LOG_TAG, "action index: " + motionEvent.getActionIndex());

        return true;
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        int bgColor = getPrefInt("pref_default_bgcolor", 0xff000000);

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");
    }
}
