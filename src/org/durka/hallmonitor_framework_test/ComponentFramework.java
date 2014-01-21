package org.durka.hallmonitor_framework_test;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ComponentFramework {

    public interface OnPauseResumeListener {
        public void onPause();
        public void onResume();
    }

    public interface OnScreenOffTimerListener {
        public boolean onStartScreenOffTimer();
        public boolean onStopScreenOffTimer();
    }

    public interface OnPreviewComponentListener {
        public boolean onPreviewComponent();
    }

    public static abstract class Activity extends android.app.Activity {

        private final String LOG_TAG = "ComponentFramework.Activity";

        private HashMap<Integer,Child> mTrackedTouchEvent = new HashMap<Integer,Child>();
        private boolean mDebug = false;

        public abstract Container getContainer();
        public abstract Menu getMenu();

        @Override
        protected void onPause() {
            super.onPause();

            // propagate to layout's
            if (getContainer() != null)
                getContainer().onPause();
        }

        @Override
        protected void onResume() {
            super.onResume();

            // propagate to layout's
            if (getContainer() != null) {
                getContainer().setDebugMode((mDebug = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_dev_opts_debug", false)));
                getContainer().onResume();
            }
        }

        @Override
        final public boolean dispatchTouchEvent(MotionEvent motionEvent) {
            final int UNDEFINED_POINTER = -1;
            if (getContainer() == null)
                return super.dispatchTouchEvent(motionEvent);

            final int actionIndex = motionEvent.getActionIndex(), actionMasked = motionEvent.getActionMasked();
            int pointerId = (actionMasked != MotionEvent.ACTION_MOVE ? motionEvent.getPointerId(actionIndex) : motionEvent.getPointerId(actionIndex));

            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            motionEvent.getPointerCoords(actionIndex, pointerCoords);

            // DOWN or ACTION_POINTER_DOWN
            if ((actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN)) {
                // menu
                if (getMenu() != null && getMenu().isAbsCoordsMatchingMenuHitBox(motionEvent) && dispatchTouchEventToView(motionEvent, getMenu())) {
                    Log_d(LOG_TAG, "dispatchTouchEvent: start tracking menu #" + pointerId);
                    mTrackedTouchEvent.put(pointerId, getMenu());
                }

                // container
                if (!mTrackedTouchEvent.containsKey(pointerId) && getContainer() != null) {
                    Rect visibleRect = new Rect();
                    getContainer().getGlobalVisibleRect(visibleRect);

                    if (visibleRect.contains((int)pointerCoords.x, (int)pointerCoords.y) && dispatchTouchEventToView(motionEvent, getContainer())) {
                        Log_d(LOG_TAG, "dispatchTouchEvent: start tracking container #" + pointerId);
                        mTrackedTouchEvent.put(pointerId, getContainer());
                    }
                }

                // others
                if (!mTrackedTouchEvent.containsKey(pointerId))
                    super.dispatchTouchEvent(motionEvent);
            }

            // MOVE
            if ((actionMasked == MotionEvent.ACTION_MOVE)) {
                Child child = null;

                // search tracked pointers
                for (Iterator<Integer> trackedIds = mTrackedTouchEvent.keySet().iterator(); trackedIds.hasNext(); ) {
                    pointerId = trackedIds.next();

                    if (motionEvent.findPointerIndex(pointerId) != UNDEFINED_POINTER && dispatchTouchEventToView(motionEvent, (child = mTrackedTouchEvent.get(pointerId)))) {
                        //Log_d(LOG_TAG, "dispatchTouchEvent: tracked pointer " + child.getClass().getName());
                    }
                }

                // not in the tracked list (others)
                if (child == null)
                    super.dispatchTouchEvent(motionEvent);
            }

            // UP, POINTER_UP or CANCEL
            if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_POINTER_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
                Child child = null;

                // clean up tracking list
                if (mTrackedTouchEvent.containsKey(pointerId)) {
                    dispatchTouchEventToView(motionEvent, (child = mTrackedTouchEvent.get(pointerId)));

                    Log_d(LOG_TAG, "dispatchTouchEvent: stop tracking #" + pointerId + ", " + child.getClass().getName());
                    mTrackedTouchEvent.remove(pointerId);
                // send to others
                } else {
                    Log_d(LOG_TAG, "dispatchTouchEvent: send to super");
                    super.dispatchTouchEvent(motionEvent);
                }
            }

            // enable processing of all touch events
            return true;
        }

        // helper
        protected void Log_d(String tag, String message) {
            if (mDebug)
                Log.d(tag, message);
        }
    }

    private static boolean dispatchTouchEventToView(MotionEvent motionEvent, View view) {
        boolean result = false;

        Rect visibleRect = new Rect();
        view.getGlobalVisibleRect(visibleRect);

        // send absolute coordinates to menu
        if (view instanceof Menu) {
            result = view.dispatchTouchEvent(motionEvent);
        // calc relative coordinates (action bar & notification bar!!!) and dispatch
        } else {
            MotionEvent dispatch = MotionEvent.obtain(motionEvent);
            dispatch.offsetLocation(-visibleRect.left, -visibleRect.top);
            result = view.dispatchTouchEvent(dispatch);
            dispatch.recycle();
        }

        return result;
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

    public static class Container extends Child implements OnPauseResumeListener, ComponentMenu.OnMenuOpenListener {

        private final String LOG_TAG = "ComponentFramework.Container";

        private boolean mLayoutInflated = false;
        private HashMap<Integer, Layout> mBackStack = new HashMap<Integer, Layout>();
        private String mPreviewMode = null;
        private Bundle mApplicationState = new Bundle();

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
                (loading = new DefaultLayout(mContext, mAttributeSet)) != null) {               // resource id from styled attr
                mLayoutView = loading;
                mBackStack.put(mBackStack.size(), loading);
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
            addView(view, index, null);
        }

        @Override
        public void addView(View view, ViewGroup.LayoutParams layoutParams) {
            addView(view, super.getChildCount(), layoutParams);
        }

        @Override
        public void addView(View view, int width, int height) {
            addView(view, super.getChildCount(), new ViewGroup.LayoutParams(width, height));
        }

        @Override
        public void addView(View view, int index, ViewGroup.LayoutParams layoutParams) {
            if (!(view instanceof Layout) && !(view instanceof MenuLayout) && !(view instanceof WarningLayout)) {
                Log_d(LOG_TAG, "addView: WarningLayout -> '" + view.getClass().getName() + "'");
                super.addView(new WarningLayout(this.getContext(), mAttributeSet, view.getLayoutParams(), view.getClass()));
                return;
            }

            super.addView(view, index, (layoutParams != null ? layoutParams : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)));

//            if (mDebug) {
//                Log_d(LOG_TAG, "addView: dumping current hierarchy");
//                debug(1);
//            }
        }

        @Override
        final protected void onFinishInflate() {
            super.onFinishInflate();

            mLayoutInflated = true;

            Log_d(LOG_TAG, "onFinishInflate: ");
            if (mLayoutView != null)
                mLayoutView.setVisibility(VISIBLE);
        }

        @Override
        public void bringChildToFront(View child) {
            if (mLayoutInflated && (child instanceof Layout)) {
                try {
                    final Layout backStack = mBackStack.get(mBackStack.size() - 1);
                    final Layout lastBackStack = mBackStack.get(mBackStack.size() - 2);
                    final Layout layout = (Layout)child;

                    // layout invisible
                    if (!child.isShown())
                        return;

                    // add to list
                    if (!mBackStack.containsValue(layout)) {
                        mBackStack.put(mBackStack.size(), layout);
                    // resort list
                    } else {
                        int idx = 0;
                        for (Map.Entry<Integer,Layout> entry : mBackStack.entrySet()) {
                            if (entry.getValue() != layout) {
                                mBackStack.put(idx, entry.getValue());
                                idx++;
                            } else
                                mBackStack.remove(entry.getKey());
                        }
                        mBackStack.put(idx, layout);
                    }

                } catch (Exception e) {
                    Log_d(LOG_TAG, "bringChildToFront: exception occurred! " + e.getMessage());
                }
            }

            super.bringChildToFront(child);
        }

        public void sendChildToBack(View child) {
            if (!mBackStack.containsValue(child) || !(child instanceof Layout))
                return;

            Layout layout = (Layout)child;

            for (Map.Entry<Integer,Layout> entry : mBackStack.entrySet()) {
                if (entry.getValue() == layout) {
                    mBackStack.remove(entry.getKey());
                }
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent motionEvent) {
            boolean result = false;
            Layout dispatchLayout = mBackStack.get(mBackStack.size() - 1);

            // calc relative coordinates
            MotionEvent dispatch = MotionEvent.obtain(motionEvent);
            dispatch.offsetLocation(-dispatchLayout.getLeft(), -dispatchLayout.getTop());
            result = dispatchLayout.dispatchTouchEvent(dispatch);
            dispatch.recycle();

            return result;
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

            // propagate resume to child views
            for (int idx=0; idx<getChildCount(); idx++) {
                if ((getChildAt(idx) instanceof Layout)  && OnPauseResumeListener.class.isAssignableFrom(getChildAt(idx).getClass()) && getChildAt(idx).isShown())
                    ((OnPauseResumeListener)getChildAt(idx)).onResume();
            }

            // propagate preview to child views
            if (mPreviewMode == null && getActivity().getIntent().getExtras() != null && !getActivity().getIntent().getExtras().getString("preview", "").equals("")) {
                mPreviewMode = getActivity().getIntent().getExtras().getString("preview");

                for (int idx=0; idx<getChildCount(); idx++) {
                    if ((getChildAt(idx) instanceof Layout) && OnPreviewComponentListener.class.isAssignableFrom(getChildAt(idx).getClass()) &&
                       ((OnPreviewComponentListener)getChildAt(idx)).onPreviewComponent()) {
                        Log_d(LOG_TAG, "onResume: preview -> " + getChildAt(idx).getClass().getName());
                        getChildAt(idx).setVisibility(VISIBLE);
                        break;
                    }
                }
            }
        }

        public void onMenuAction(ComponentMenu.MenuOption menuOption) {
            Log_d(LOG_TAG, "onMenuAction: ");
        }

        public boolean onMenuOpen() {
            Log_d(LOG_TAG, "onMenuOpen: ");

//            if (mBackStack.get(mBackStack.size() - 1))
            return true;
        }

        public String getPreviewMode() {
            return mPreviewMode;
        }

        public Bundle getApplicationState() {
            return mApplicationState;
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
                        // handle default layout visibility
                        if (getContainer() != null)
                            getContainer().bringChildToFront(this);
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
                    // handle default layout visibility
                    if (getContainer() != null)
                        getContainer().sendChildToBack(this);
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

        private final String LOG_TAG = "ComponentFramework.DefaultLayout";

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

    public static abstract class Menu extends Child {

        public Menu(Context context) {
            super(context);
        }

        public Menu(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }

        public Menu(Context context, AttributeSet attributeSet, int defStyle) {
            super(context, attributeSet, defStyle);
        }

        public abstract boolean isAbsCoordsMatchingMenuHitBox(MotionEvent motionEvent);
        public abstract void registerMenuOption(int viewId, int optionId, int imageId);
        public abstract void registerOnMenuOpenListener(ComponentMenu.OnMenuOpenListener onMenuOpenListener);
    }

    public static class MenuLayout extends Child {

        private final String LOG_TAG = "ComponentFramework.MenuLayout";

        public MenuLayout(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }
    }
}
