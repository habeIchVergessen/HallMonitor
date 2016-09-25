package org.durka.hallmonitor_framework_test;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.TextView;

public class ComponentResize extends ComponentFramework.Layout
        implements ComponentFramework.OnPreviewComponentListener, ComponentFramework.OnCoverStateChangedListener {

    private final String LOG_TAG = "ComponentResize";

    private final String mPreviewName = "resizeWidget";

    private final static String INTENT_resizeWidgetShow = "resizeWidgetShow";

    private boolean mPreviewMode = false;

    private int mBackgroundColor = 0xCC000000;

    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;
    private int mOffset = 0;
    private int downY;

    public ComponentResize(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        Log_d(LOG_TAG, "ComponentResize");

        // setup layout resource id (if not loaded via styled attrs)
        if (mLayoutResId == UNDEFINED_LAYOUT) {
            Log_d(LOG_TAG, "setup layout resource id");
            setLayoutResourceId(R.layout.component_resize);
        }

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    protected void onInitComponent() {
        Log_d(LOG_TAG, "onInitComponent");
    }

    protected boolean onOpenComponent() {
        Log_d(LOG_TAG, "onOpenComponent");

        if (mPreviewMode)
            preview();

        // read current values
        mScaleFactor = getPrefFloat("pref_resize_controls_scale", 1.0f);
        mOffset = getPrefInt("pref_resize_controls_offset", 0);

        ViewCoverService.registerOnCoverStateChangedListener(this);

        return true;
    }

    protected void onCloseComponent() {
        Log_d(LOG_TAG, "onCloseComponent");

        ViewCoverService.unregisterOnCoverStateChangedListener(this);

        // save settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit().putFloat("pref_resize_controls_scale", mScaleFactor).commit();
        prefs.edit().putInt("pref_resize_controls_offset", mOffset).commit();

        if (mPreviewMode) {
            getActivity().finish();
        }
    }

    /**
     * implement OnCoverStateChangedListener
     */
    public void onCoverStateChanged(boolean coverClosed)
    {
        Log_d(LOG_TAG, "onCoverStateChanged: " + coverClosed);

        if (coverClosed) {
            final TextView textView = (TextView)findViewById(R.id.instruction_name);

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        textView.setText(getResources().getText(R.string.instruction_arrange));
                        createBackground();
                    } catch (Exception e) {
                        Log_d(LOG_TAG, "onCoverStateChanged: exception occurred! " + e.getMessage());
                    }
                }
            });

            stopScreenOffTimer();
        } else {
            onCloseComponent();
        }
    }

    /**
     * implement OnPreviewComponentListener
     */
    public boolean onPreviewComponent() {
        String previewMode = getContainer().getPreviewMode();

        mPreviewMode = (previewMode != null && (previewMode.equals("*") || previewMode.equals(mPreviewName)));
        Log_d(LOG_TAG, "onPreviewComponent: " + mPreviewMode + ", '" + previewMode + "'");

        return (mPreviewMode && previewMode.equals(mPreviewName));
    }

    private void preview() {
        Log_d(LOG_TAG, "preview: ");

        mPreviewMode = true;
    }

    private void createBackground() {
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
//            Log_d(LOG_TAG, "view: " + view.getLeft() + ":" + view.getTop() + " - " + view.getWidth() + ":" + view.getHeight());

        // translate coordinates
        final float stroke = 5f;
        Rect viewRect = new Rect(0, 0, getWidth(), getHeight());
        Log_d(LOG_TAG, "createBackground: " + viewRect);


//            Log_d(LOG_TAG, "draw: " + drawRect.left + ":" + drawRect.bottom + ", " + mOptions.getLeft() + ":" + mOptions.getBottom());

        int size = 40, thickness = 10;
        Path path = new Path();

        // top left
        path.moveTo(viewRect.left, viewRect.top);
        path.lineTo(viewRect.left, viewRect.top + size);
        path.lineTo(viewRect.left + thickness, viewRect.top + size);
        path.lineTo(viewRect.left + thickness, viewRect.top + thickness);
        path.lineTo(viewRect.left + size, viewRect.top + thickness);
        path.lineTo(viewRect.left + size, viewRect.top);
        path.lineTo(viewRect.left, viewRect.top);
        // top right
        path.moveTo(viewRect.right, viewRect.top);
        path.lineTo(viewRect.right, viewRect.top + size);
        path.lineTo(viewRect.right - thickness, viewRect.top + size);
        path.lineTo(viewRect.right - thickness, viewRect.top + thickness);
        path.lineTo(viewRect.right - size, viewRect.top + thickness);
        path.lineTo(viewRect.right - size, viewRect.top);
        path.lineTo(viewRect.right, viewRect.top);
        // bottom left
        path.moveTo(viewRect.left, viewRect.bottom);
        path.lineTo(viewRect.left, viewRect.bottom - size);
        path.lineTo(viewRect.left + thickness, viewRect.bottom - size);
        path.lineTo(viewRect.left + thickness, viewRect.bottom - thickness);
        path.lineTo(viewRect.left + size, viewRect.bottom - thickness);
        path.lineTo(viewRect.left + size, viewRect.bottom);
        path.lineTo(viewRect.left, viewRect.bottom);
        // bottom right
        path.moveTo(viewRect.right, viewRect.bottom);
        path.lineTo(viewRect.right, viewRect.bottom - size);
        path.lineTo(viewRect.right - thickness, viewRect.bottom - size);
        path.lineTo(viewRect.right - thickness, viewRect.bottom - thickness);
        path.lineTo(viewRect.right - size, viewRect.bottom - thickness);
        path.lineTo(viewRect.right - size, viewRect.bottom);
        path.lineTo(viewRect.right, viewRect.bottom);

        // draw area
        Paint paint = new Paint();
        paint.setARGB(150, 255, 255, 255);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        Canvas c = new Canvas(bmp);
        c.drawColor(mBackgroundColor);
        c.drawPath(path, paint);

        // draw border
        paint.setARGB(200, 255, 255, 255);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        c.drawPath(path, paint);

        setBackground(new BitmapDrawable(getResources(), bmp));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 1) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downY = Math.round(event.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    int deltaY = Math.max(-50, Math.min(Math.round(event.getY()) - downY + mOffset, 50));

                    if (deltaY != mOffset) {
                        Log_d(LOG_TAG, "onTouchEvent: offset=" + deltaY);
                        mOffset = deltaY;
                        getActivity().getContainer().onResize(mScaleFactor, mOffset);
                    }
                    break;
            }
        } else {
            // Let the ScaleGestureDetector inspect all events.
            mScaleDetector.onTouchEvent(event);
        }

        return true; //super.onTouchEvent(event);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Don't let the object get too small or too large.
            float scaleFactor = Math.round(Math.max(1f, Math.min(mScaleFactor * detector.getScaleFactor(), 1.5f)) * 100) / 100f;

            if (scaleFactor != mScaleFactor) {
                Log_d(LOG_TAG, "ScaleListener.onScale: scale=" + scaleFactor + " (" + detector.getScaleFactor() + ")");

                mScaleFactor = scaleFactor;

                // update layout
                getActivity().getContainer().onResize(mScaleFactor, mOffset);
            }
            return true;
        }
    }
}
