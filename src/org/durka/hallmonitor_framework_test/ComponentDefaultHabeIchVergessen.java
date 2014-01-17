package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.TextClock;

public class ComponentDefaultHabeIchVergessen extends ComponentFramework.Layout {

    private final String LOG_TAG = "ComponentDefaultDurka";

    public ComponentDefaultHabeIchVergessen(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        setLayoutResourceId(R.layout.component_default_habe_ich_vergessen_layout);
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");

        TextClock defaultTextClock = (TextClock) findViewById(R.id.default_text_clock);

        // TextClock format
        SpannableString spanString = new SpannableString(getResources().getString(R.string.styled_24_hour_clock));
        spanString.setSpan(new RelativeSizeSpan(2.8f), 1, 5, 0);
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 1, 5, 0);
        spanString.setSpan(new RelativeSizeSpan(0.8f), 6, spanString.length(), 0);
        defaultTextClock.setFormat24Hour(spanString);
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");
        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");
    }
}
