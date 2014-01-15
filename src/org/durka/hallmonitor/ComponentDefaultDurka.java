package org.durka.hallmonitor;

import android.content.Context;
import android.util.AttributeSet;

public class ComponentDefaultDurka extends ComponentFramework.Layout {

    private final String LOG_TAG = "ComponentDefaultDurka";

    public ComponentDefaultDurka(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        setLayoutResourceId(R.layout.component_default_durka_layout);
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");
        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");
    }
}
