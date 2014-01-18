package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.util.AttributeSet;

public class ComponentPhone extends ComponentFramework.Layout {

    private final String LOG_TAG = "ComponentPhone";

    private boolean mInitialized = false;

    public ComponentPhone(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        Log_d(LOG_TAG, "ComponentPhone");

        // setup layout resource id (if not loaded via styled attrs)
        if (mLayoutResId == UNDEFINED_LAYOUT) {
            Log_d(LOG_TAG, "setup layout resource id");
            setLayoutResourceId(R.layout.component_phone_habe_ich_vergessen_layout);
        }
    }

    protected void onInitComponent() {
        mInitialized = true;
        Log_d(LOG_TAG, "onInitComponent");

    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        int bgColor = getPrefInt("pref_default_bgcolor", 0xff000000);

        stopScreenOffTimer();

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");

        startScreenOffTimer();
    }
}
