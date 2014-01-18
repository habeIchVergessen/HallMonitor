package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.lang.reflect.Constructor;

public class ComponentFramework {

    public interface OnPauseResumeListener {
        public void onPause();
        public void onResume();
    }

    public interface OnScreenOffTimerListener {
        public boolean onStartScreenOffTimer();
        public boolean onStopScreenOffTimer();
    }

    public static class Child extends RelativeLayout {

        private final String LOG_TAG = "ComponentFramework.Child";

        protected Context mContext = null;
        protected AttributeSet mAttributeSet = null;
        protected boolean mDebug = false;

        // layout
        protected final int UNDEFINED_LAYOUT = 0;

        protected String mLayoutClassName = "";
        protected String mLayoutResName = "";
        protected int mLayoutResId = UNDEFINED_LAYOUT;
        protected View mLayoutView = null;

        public Child(Context context) {
            super(context);

            mContext = context;
        }

        public Child(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);

            mContext = context;
            loadStyledAttrs(attributeSet);
        }

        public Child(Context context, AttributeSet attributeSet, int defStyle) {
            super(context, attributeSet, defStyle);

            mContext = context;
            loadStyledAttrs(attributeSet);
        }

        private void loadStyledAttrs(AttributeSet attributeSet) {
            mAttributeSet = attributeSet;

            if (mAttributeSet == null)
                return;

            TypedArray styledAttrs = getContext().obtainStyledAttributes(mAttributeSet, R.styleable.ComponentStyleable);

            // reading parameter debugMode
            setDebugMode(styledAttrs.getBoolean(R.styleable.ComponentStyleable_debugMode, false));

            // reading parameter defaultLayoutResourceId
            try {
                if (styledAttrs != null) {
                    int resId = styledAttrs.getResourceId(R.styleable.ComponentStyleable_defaultLayoutResourceId, UNDEFINED_LAYOUT);
                    mLayoutResName = getResources().getResourceName(resId);
                    mLayoutResId = resId;
                }
            } catch (Exception e) {
            }

            mLayoutClassName = styledAttrs.getString(R.styleable.ComponentStyleable_defaultLayoutClassName);
        }

        public void setDebugMode(boolean debugMode) {
            mDebug = debugMode;
        }

        // helpers
        protected Activity getActivity() {
            return (Activity)mContext;
        }

        protected void Log_d(String tag, String message) {
            if (mDebug)
                Log.d(tag, message);
        }

        protected int getPrefInt(String prefName, int defaultValue) {
            if (isInEditMode())
                return defaultValue;

            return PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(prefName, defaultValue);
        }

        protected boolean getPrefBoolean(String prefName, boolean defaultValue) {
            if (isInEditMode())
                return defaultValue;

            return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(prefName, defaultValue);
        }

        protected String getPrefString(String prefName) {
            return getPrefString(prefName, "");
        }

        protected String getPrefString(String prefName, String defaultValue) {
            if (isInEditMode())
                return defaultValue;

            return PreferenceManager.getDefaultSharedPreferences(getContext()).getString(prefName, defaultValue);
        }

        protected int getBackgroundColor() {
            int defaultBg = 0xff000000;
            if (isInEditMode())
                return defaultBg;

            return getPrefInt("pref_default_bgcolor", defaultBg);
        }
    }

    public static class Container extends Child implements OnPauseResumeListener {

        private final String LOG_TAG = "ComponentFramework.Container";

        public Container(Context context) {
            super(context);

            initLayout();
        }

        public Container(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);

            initLayout();
        }

        public Container(Context context, AttributeSet attributeSet, int defStyle) {
            super(context, attributeSet, defStyle);

            initLayout();
        }

        private void initLayout() {
            // do the static stuff for designer
            if (isInEditMode()) {
                setBackgroundColor(0xff000000);
                return;
            }

            // go for the dynamic stuff (preference based)
            setBackgroundColor(getBackgroundColor());

            // init default layout
            Layout loading = null;
            if ((loading = initLayout(getPrefString("prefDefaultLayoutClassName"))) != null ||  // class name from preference
                (loading = initLayout(mLayoutClassName)) != null ||                             // class name from styled attr
                (loading = new DefaultLayout(mContext, mAttributeSet)) != null                  // resource id from styled attr
               ) {
                    mLayoutView = loading;
            }

            addView(mLayoutView);
        }

        private Layout initLayout(String className) {
            Layout result = null;

            if (className == null || className.equals(""))
                return result;

            try {
                Log_d(LOG_TAG, "loading class: " + className);
                Class<?> loadClass = Class.forName(this.getClass().getPackage().getName() + "." + className);

                if (Layout.class.isAssignableFrom(loadClass)) {
                    Constructor<?> loadConstructor = loadClass.getConstructor(Context.class, AttributeSet.class);
                    Layout loadLayout = (Layout)loadConstructor.newInstance(mContext, mAttributeSet);
                    result = loadLayout;
                }
            } catch (Exception e) {
                Log_d(LOG_TAG, "loading class: exception occurred " + e.getMessage());
            }

            return result;
        }

        public Layout getDefaultLayout() {
            return (mLayoutView instanceof Layout ? (Layout)mLayoutView : null);
        }

        /*
         * override all the addView stuff
         */
        @Override
        public void addView(View view) {
            Log_d(LOG_TAG, "addView: view");
            addView(view, super.getChildCount());
        }

        @Override
        public void addView(View view, int index) {
            Log_d(LOG_TAG, "addView: view, index");
            addView(view, index, null);
        }

        @Override
        public void addView(View view, ViewGroup.LayoutParams layoutParams) {
            Log_d(LOG_TAG, "addView: view, layoutParams");
            addView(view, super.getChildCount(), layoutParams);
        }

        @Override
        public void addView(View view, int width, int height) {
            Log_d(LOG_TAG, "addView: view, width, height");
            addView(view, super.getChildCount(), new ViewGroup.LayoutParams(width, height));
        }

        @Override
        public void addView(View view, int index, ViewGroup.LayoutParams layoutParams) {
            Log_d(LOG_TAG, "addView: view, index, layoutParams");
            if (!(view instanceof Layout) && !(view instanceof MenuLayout) && !(view instanceof WarningLayout)) {
                super.addView(new WarningLayout(this.getContext(), mAttributeSet, view.getLayoutParams(), view.getClass()));
                return;
            }

            super.addView(view, index, (layoutParams != null ? layoutParams : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)));
        }

        @Override
        final protected void onFinishInflate() {
            super.onFinishInflate();

            Log.d(LOG_TAG, "onFinishInflate: ");
            if (mLayoutView != null)
                mLayoutView.setVisibility(VISIBLE);
        }

        @Override
        final public void setDebugMode(boolean debugMode) {
            Log_d(LOG_TAG, "setDebugMode: " + debugMode + ", #" + getChildCount());
            super.setDebugMode(debugMode);

            // propagate to child views
            for (int idx=0; idx<getChildCount(); idx++) {
                if (getChildAt(idx) instanceof WarningLayout)
                    ((WarningLayout)getChildAt(idx)).setDebugMode(debugMode);
                if (getChildAt(idx) instanceof Layout)
                    ((Layout)getChildAt(idx)).setDebugMode(debugMode);
                if (getChildAt(idx) instanceof MenuLayout)
                    ((MenuLayout)getChildAt(idx)).setDebugMode(debugMode);
            }
        }

        final Layout getLayoutByResId(int resourceId) {
            Layout result = null;

            for (int idx=0; idx<getChildCount(); idx++)
                if (getChildAt(idx).getId() == resourceId) {
                    if (getChildAt(idx) instanceof Layout)
                        result = (Layout)getChildAt(idx);
                    break;
                }

            return result;
        }

        public void onPause() {
            // propagate to child views
            for (int idx=0; idx<getChildCount(); idx++) {
                if (getChildAt(idx) instanceof Layout) {
                    Layout layout = (Layout)getChildAt(idx);

                    if (OnPauseResumeListener.class.isAssignableFrom(layout.getClass()) && layout.isShown())
                        ((OnPauseResumeListener)layout).onPause();
                }
            }
        }

        public void onResume() {
            setDebugMode(getPrefBoolean("pref_dev_opts_debug", false));

            // propagate to child views
            for (int idx=0; idx<getChildCount(); idx++) {
                if (getChildAt(idx) instanceof Layout) {
                    Layout layout = (Layout)getChildAt(idx);

                    if (OnPauseResumeListener.class.isAssignableFrom(layout.getClass()) && layout.isShown())
                        ((OnPauseResumeListener)layout).onResume();
                }
            }
        }
    }

    private static class WarningLayout extends Container {

        private final String LOG_TAG = "ComponentFramework.Warning";

        public WarningLayout(Context context, AttributeSet attributeSet, ViewGroup.LayoutParams layoutParams, Class dismissedView) {
            super(context, attributeSet);

            Bitmap bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);

            Paint paint = new Paint();
            paint.setARGB(150, 255, 0, 0);
            paint.setAntiAlias(true);
            paint.setTextSize(15);

            Canvas c = new Canvas(bitmap);
            c.drawColor(0xff808080);
            c.drawText("unsupported class", 5, 30, paint);
            c.drawText(dismissedView.getName(), 5, 60, paint);

            int visible = (isInEditMode() ? VISIBLE : INVISIBLE);
            String visibility = null;
            if (isInEditMode() && attributeSet != null && (visibility = attributeSet.getAttributeValue("http://schemas.android.com/apk/res/android", "visibility")) != null) {
                if (visibility.equals("gone"))
                    visible = GONE;
                if (visibility.equals("invisible"))
                    visible = INVISIBLE;
            }

            setBackground(new BitmapDrawable(getResources(), bitmap));
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            setVisibility(visible);
        }

        @Override
        public void addView(View view, int index, ViewGroup.LayoutParams layoutParams) {
            return;
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onResume() {
        }
    }

    public static abstract class Layout extends Child {

        private final String LOG_TAG = "ComponentFramework.Layout";

        private boolean mDeflateOnClose = true;

        public Layout(Context context) {
            super(context);

            Log_d(LOG_TAG, "Layout");
            setVisibility(INVISIBLE);
        }

        public Layout(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);

            Log_d(LOG_TAG, "Layout");
            setVisibility(INVISIBLE);
        }

        public Layout(Context context, AttributeSet attributeSet, int defStyle) {
            super(context, attributeSet, defStyle);

            Log_d(LOG_TAG, "Layout");
            setVisibility(INVISIBLE);
        }

        @Override
        final public void setVisibility(int visibility) {
            Log_d(LOG_TAG, "setVisibility");
            switch (visibility) {
                case VISIBLE:
                    // layoutInflater doesn't work in constructor
                    if (mLayoutView == null && !initLayout())
                        return;

                    if (mLayoutView != null && onOpenComponent()) {
                        if (OnPauseResumeListener.class.isAssignableFrom(mLayoutView.getClass()))
                            ((OnPauseResumeListener)mLayoutView).onResume();
                        super.setVisibility(VISIBLE);
                    } else
                        clearChildViews();
                    break;
                case INVISIBLE:
                case GONE:
                    if (mLayoutView != null) {
                        if (OnPauseResumeListener.class.isAssignableFrom(mLayoutView.getClass()))
                            ((OnPauseResumeListener)mLayoutView).onPause();
                        onCloseComponent();
                    }
                    super.setVisibility(INVISIBLE);
                    clearChildViews();
                    break;
            }
        }

        private boolean initLayout() {
            Log_d(LOG_TAG, "initLayout");
            // loading layout with resource id
            if (mLayoutView == null && mLayoutResId != UNDEFINED_LAYOUT && !isInEditMode()) {
                Log_d(LOG_TAG, "initLayout: inflating layout '" + mLayoutResName + "'");
                mLayoutView = getActivity().getLayoutInflater().inflate(mLayoutResId, this, false);
                addView(mLayoutView);

                // callback to implementer
                onInitComponent();
            }

            setBackgroundColor(getBackgroundColor());
            return (mLayoutView != null);
        }

        private void clearChildViews() {
            if (mDeflateOnClose) {
                removeAllViews();
                mLayoutView = null;
            }
        }

        final protected void setLayoutResourceId(int layoutResId) {
            // reading parameter defaultLayoutResourceId
            try {
                    int resId = layoutResId;
                    mLayoutResName = getResources().getResourceName(resId);
                    mLayoutResId = resId;
            } catch (Exception e) {
            }
        }

        /**
         * <br/>
         * remove all views after onCloseComponent was called<br/>
         * <br/>
         * default: true<br/>
         * <br/>
         * @param deflateOnClose boolean
         */
        final protected void setDeflateOnClose(boolean deflateOnClose) {
            mDeflateOnClose = deflateOnClose;
        }

        final protected Container getContainer() {
            return (getParent() instanceof Container ? (Container)getParent() : null);
        }

        /**
         * <br/>
         * called after layout inflate finished<br/>
         * <br/>
         */
        protected abstract void onInitComponent();
        /**
         * <br/>
         * called after layout inflate finished<br/>
         * <br/>
         * @return true to show layout. otherwise is invisible
         */
        protected abstract boolean onOpenComponent();
        /**
         * <br/>
         * called before layout is closed<br/>
         * <br/>
         */
        protected abstract void onCloseComponent();

        /**
         * <br/>
         * notify activity about the layout request for starting screen off timer
         * <br/>
         * @return result from activity method call or false if activity doesn't implement the listener
         */
        protected boolean startScreenOffTimer() {
            boolean result = false;

            if (getActivity() != null) {
                if (OnScreenOffTimerListener.class.isAssignableFrom(getActivity().getClass())) {
                    result = ((OnScreenOffTimerListener)getActivity()).onStartScreenOffTimer();
                }
            }

            return result;
        }

        /**
         * <br/>
         * notify activity about the layout request for stopping screen off timer
         * <br/>
         * @return result from activity method call or false if activity doesn't implement the listener
         */
        protected boolean stopScreenOffTimer() {
            boolean result = false;

            if (getActivity() != null) {
                if (OnScreenOffTimerListener.class.isAssignableFrom(getActivity().getClass())) {
                    result = ((OnScreenOffTimerListener)getActivity()).onStopScreenOffTimer();
                }
            }

            return result;
        }
    }

    public static class DefaultLayout extends Layout {

        private final String LOG_TAG = "ComponentFramework.Default";

        public DefaultLayout(Context context) {
            super(context);
        }

        public DefaultLayout(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }

        protected void onInitComponent() {
        }

        protected boolean onOpenComponent() {
            return true;
        }

        protected void onCloseComponent() {
        }
    }

    public static abstract class MenuLayout extends Child {

        private final String LOG_TAG = "ComponentFramework.Menu";

        protected boolean mDebug = false;

        // layout
        private String mLayoutResName = "";
        private int mLayoutResId = UNDEFINED_LAYOUT;
        private View mLayoutView = null;

        public MenuLayout(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }
    }
}
