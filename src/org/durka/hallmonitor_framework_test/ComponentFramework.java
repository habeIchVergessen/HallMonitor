package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
        public abstract MenuController getMenuController();

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
            // propagate to menu
            if (getMenuController() != null) {
                getMenuController().setDebugMode(mDebug);
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
                if (getMenuController() != null && getMenuController().isAbsCoordsMatchingMenuHitBox(motionEvent) && dispatchTouchEventToView(motionEvent, getMenuController())) {
                    // don't track if container already tracks
                    if (getContainer() != null && mTrackedTouchEvent.containsValue(getContainer())) {
                        Log_d(LOG_TAG, "dispatchTouchEvent: excluded from tracking " + pointerId);
                        mTrackedTouchEvent.put(pointerId, null);
                    // don't track 2 menu actions
                    } else if (mTrackedTouchEvent.containsValue(getMenuController())) {
                        Log_d(LOG_TAG, "dispatchTouchEvent: excluded from tracking " + pointerId);
                        mTrackedTouchEvent.put(pointerId, null);
                    } else {
                        Log_d(LOG_TAG, "dispatchTouchEvent: start tracking menu #" + pointerId);
                        mTrackedTouchEvent.put(pointerId, getMenuController());
                    }
                }

                // container
                if (!mTrackedTouchEvent.containsKey(pointerId) && getContainer() != null) {
                    Rect visibleRect = new Rect();
                    getContainer().getGlobalVisibleRect(visibleRect);

                    if (visibleRect.contains((int)pointerCoords.x, (int)pointerCoords.y) && dispatchTouchEventToView(motionEvent, getContainer())) {
                        // don't track if menu already tracks
                        if (getMenuController() != null && mTrackedTouchEvent.containsValue(getMenuController())) {
                            Log_d(LOG_TAG, "dispatchTouchEvent: excluded from tracking " + pointerId);
                            mTrackedTouchEvent.put(pointerId, null);
                        } else {
                            Log_d(LOG_TAG, "dispatchTouchEvent: start tracking container #" + pointerId);
                            mTrackedTouchEvent.put(pointerId, getContainer());
                        }
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

        private boolean dispatchTouchEventToView(MotionEvent motionEvent, View view) {
            // send absolute coordinates to menu
            if (view instanceof MenuController)
                return view.dispatchTouchEvent(motionEvent);

            // calc relative coordinates (action bar & notification bar!!!) and dispatch
            Rect visibleRect = new Rect();
            view.getGlobalVisibleRect(visibleRect);

            MotionEvent dispatch = MotionEvent.obtain(motionEvent);
            dispatch.offsetLocation(-visibleRect.left, -visibleRect.top);
            boolean result = view.dispatchTouchEvent(dispatch);
            dispatch.recycle();

            return result;
        }

        // helper
        protected void Log_d(String tag, String message) {
            if (mDebug)
                Log.d(tag, message);
        }

        protected void Log_e(String tag, String message) {
            if (mDebug)
                Log.e(tag, "uups: " + message);
        }
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

        protected void Log_e(String tag, String message) {
                Log.e(tag, "uups: " + message);
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

    public static class Container extends Child implements OnPauseResumeListener, MenuController.OnMenuOpenListener {

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
            super.setDebugMode(debugMode);
            Log_d(LOG_TAG, "setDebugMode: " + debugMode + ", #" + getChildCount());

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

        public MenuController.OnMenuActionListener onMenuOpen() {
            Log_d(LOG_TAG, "onMenuOpen: #" + mBackStack.size());

            MenuController.OnMenuActionListener result = null;

            if (MenuController.OnMenuActionListener.class.isAssignableFrom(mBackStack.get(mBackStack.size() - 1).getClass()))
                result = (MenuController.OnMenuActionListener)mBackStack.get(mBackStack.size() - 1);

            return result;
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

    public static class MenuController extends Child {

        private final static String LOG_TAG = "MenuController";

        private HashMap<Integer,PointF> mOptionMenuTrack = new HashMap<Integer, PointF>();
        private HashMap<Integer,ImageView> mOptionViews = new HashMap<Integer, ImageView>();

        private int mBackgroundColor = 0xCC000000;
        private int mButtonSize = 52;

        private final float rX = 50, rY = 30;

        private int mOptionsResourceId = -1;
        private MenuLayout mOptions = null;
        private boolean mOptionSnap = true;
        private ImageView mOptionMenuButton = null;
        private AnimationDrawable mOptionMenuButtonAnim = null;
        private ViewGroup mOverlayLayout = null;

        private View mCurrentHighlightedOption = null;

        private HashMap<Integer,Menu> mMenuList = new HashMap<Integer, Menu>();
        private HashMap<Integer,Option> mOptionList = new HashMap<Integer, Option>();
        private OnMenuOpenListener mOnMenuOpenListener = null;
        private OnMenuActionListener mOnMenuActionListener = null;

        public interface OnMenuActionListener {
            public int getMenuId();
            public void onMenuInit(MenuController menuController);
            public boolean onMenuOpen(Menu menu);
            public void onMenuAction(MenuOption menuOption);
        }

        public interface OnMenuOpenListener {
            public OnMenuActionListener onMenuOpen();
        }

        public class Menu {
            private HashMap<Integer,Integer> mMenuOptions = new HashMap<Integer, Integer>();
            private int mViewId;

            protected Menu(int viewId) {
                mViewId = viewId;
            }

            protected void addOption(Option option) {
                if (!mMenuOptions.containsValue(option.getId()))
                    mMenuOptions.put(mMenuOptions.size(), option.getId());
            }

            protected Set<Integer> getOptions() {
                return mMenuOptions.keySet();
            }

            protected Option getOption(int optionId) {
                return mOptionList.get(mMenuOptions.get(optionId));
            }

            protected int getId() {
                return mViewId;
            }
        }

        public class Option {
            private int mOptionId;
            private int mImageId;
            private Object mStateObject;

            protected Option(int optionId, int imageId) {
                mOptionId = optionId;
                mImageId = imageId;
            }

            protected int getId() {
                return mOptionId;
            }

            protected Object getStateObject() {
                return mStateObject;
            }

            protected void setStateObject(Object stateObject) {
                mStateObject = stateObject;
            }

            protected int getImageId() {
                return mImageId;
            }

            protected void setImageId(int imageId) {
                mImageId = imageId;
            }
        }

        public class MenuOption {
            private Menu mMenu;
            private Option mOption;

            protected MenuOption(Menu menu, Option option) {
                mMenu = menu;
                mOption = option;
            }

            public int getViewId() {
                return mMenu.getId();
            }

            public int getOptionId() {
                return mOption.getId();
            }

            public Object getStateObject() {
                return mOption.getStateObject();
            }

            public void setStateObject(Object stateObject) {
                mOption.setStateObject(stateObject);
            }

            public int getImageId() {
                return mOption.getImageId();
            }

            public void setImageId(int imageId) {
                mOption.setImageId(imageId);
            }
        }

        public MenuController(Context context) {
            super(context);
        }

        public MenuController(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);

            initLayout();
        }

        public MenuController(Context context, AttributeSet attributeSet, int defStyle) {
            super(context, attributeSet, defStyle);

            initLayout();
        }

        private void initLayout() {
            // override layout
            setClickable(false);
            setFocusable(false);

            if (mAttributeSet != null) {
                // read user-defined attributes
                TypedArray styledAttrs = getContext().obtainStyledAttributes(mAttributeSet, R.styleable.ComponentMenu);

                // read background color (apply alpha)
                mBackgroundColor = (styledAttrs.getInt(R.styleable.ComponentMenu_overlayBackgroundColor, getBackgroundColor()) & 0xFFFFFF) + 0xCC000000;
                // read button size
                mButtonSize = styledAttrs.getInt(R.styleable.ComponentMenu_buttonSize, mButtonSize);
                // read option snap
                mOptionSnap = styledAttrs.getBoolean(R.styleable.ComponentMenu_enableOptionSnap, mOptionSnap);
                // resizing and positioning buttons
                if ((mOptionsResourceId = styledAttrs.getResourceId(R.styleable.ComponentMenu_overlayLayout, -1)) != -1)
                    setupOverlayLayout();
            }

            // set my id
            setId(R.id.componentMenu);

            // menu button
            mOptionMenuButton = new ImageView(getContext(), mAttributeSet);
            mOptionMenuButton.setId(R.id.option_overlay_menu_button);
            mOptionMenuButton.setAlpha(0.75f);
            mOptionMenuButton.setClickable(false);
            mOptionMenuButton.setFocusable(false);
            mOptionMenuButton.setBackground(getResources().getDrawable(R.drawable.option_overlay_menu_button));
            addView(mOptionMenuButton);

            // layout
            RelativeLayout.LayoutParams rlLp = (RelativeLayout.LayoutParams)mOptionMenuButton.getLayoutParams();
            rlLp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            rlLp.addRule(RelativeLayout.CENTER_VERTICAL);
            mOptionMenuButton.setLayoutParams(rlLp);

            // animate menu button
            mOptionMenuButtonAnim = (AnimationDrawable)mOptionMenuButton.getBackground();

            if (!isInEditMode()) {
                animateMenuButton(true);
            }
        }

        private boolean setupOverlayLayout() {
            if (isInEditMode()) {
                setBackgroundColor(0xff000000);
            }

            if (mOptionsResourceId != -1 && mOptions == null && !isInEditMode()) {
                Log_d(LOG_TAG, "setupOverlayLayout: " + getResources().getResourceName(mOptionsResourceId));

                View view = getRootView().findViewById(mOptionsResourceId);

                if (view != null && (view instanceof ViewGroup)) {
                    mOverlayLayout = (ViewGroup)view;

                    // options
                    mOptions = (MenuLayout)((android.app.Activity)mContext).getLayoutInflater().inflate(R.layout.component_menu, mOverlayLayout, false);
                    mOverlayLayout.addView(mOptions);

                    // init defaults
                    closeMenu();
                    clearSelection(true);

                    // search image view's and process layout
                    processImageViews();
                } else
                    Log_d(LOG_TAG, "setupOverlayLayout: view " + view);
            }

            return (mOptions != null);
        }

        @Override
        public int getId() {
            return R.id.componentMenu;
        }

        @Override
        public void setId(int resourceId) {
            super.setId(getId());
        }

        @Override
        final public boolean dispatchTouchEvent(MotionEvent motionEvent) {
            Log_d(LOG_TAG, "dispatchTouchEvent: ");
            return onTouchEvent(motionEvent);
        }

        @Override
        final public boolean onTouchEvent(MotionEvent motionEvent) {
            // not initialized yet
            if (!setupOverlayLayout())
                return true;

            // point handling
            final int actionIndex = motionEvent.getActionIndex();
            final int actionMasked = motionEvent.getActionMasked();
            final int pointerId = actionIndex;
            PointF downPoint = null;
            double radius = 0;

            // override with absolute coordinates
            Rect mOptionMenuRect = new Rect();
            getGlobalVisibleRect(mOptionMenuRect);
            // coordinates
            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            motionEvent.getPointerCoords(actionIndex, pointerCoords);
            // add offset to absolute coordinates
            pointerCoords.setAxisValue(MotionEvent.AXIS_X, pointerCoords.x + mOptionMenuRect.left);
            pointerCoords.setAxisValue(MotionEvent.AXIS_Y, pointerCoords.y + mOptionMenuRect.top);

//            Log_d(LOG_TAG, "onTouchEvent: " + pointerCoords.x + ":" + pointerCoords.y + ", tracked #" + mOptionMenuTrack.size() + ", " + motionEvent.getPointerId(actionIndex));

            final float dX = motionEvent.getX(actionIndex), mX = mOptionMenuRect.centerX();
            final float dY = motionEvent.getY(actionIndex), mY = mOptionMenuRect.centerY();

            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    // just handle 1 event
                    if (mOptionMenuTrack.size() > 0)
                        break;

                    if (isAbsCoordsMatchingMenuHitBox(mX, mY, dX, dY, rX, rY)) {
                        mOptionMenuTrack.put(motionEvent.getPointerId(actionIndex), new PointF(mX, mY));
                        boolean opened = openMenu();
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isOpen() && (downPoint = mOptionMenuTrack.get(motionEvent.getPointerId(actionIndex))) != null) {
                        // TODO calc arc's doesn't work properly (until then break here)
                        if (dY > mY)
                            break;

                        final double disPoint = Math.sqrt(Math.pow(dX - mX, 2) + Math.pow(dY - mY, 2));
                        if (disPoint == 0)
                            break;

                        final double arcPoint = Math.toDegrees(Math.acos((dX - mX) / disPoint));

                        boolean found = false;
                        // find ImageView at coordinates
                        Rect hitRect = new Rect();
                        for (int idx : mOptionViews.keySet()) {
                            View view = mOptionViews.get(idx);

                            // ignore invisible views
                            if (view.getVisibility() == View.INVISIBLE)
                                continue;

                            view.getGlobalVisibleRect(hitRect);
                            radius = hitRect.centerX() - hitRect.left;

                            final double disCenter =  Math.sqrt(Math.pow(hitRect.centerX() - mX, 2) + Math.pow(hitRect.centerY() - mY, 2));
                            final double arcCenter = Math.toDegrees(Math.acos((hitRect.centerX() - mX) / disCenter));
                            final double arcMatch = 360 * radius/(disCenter * 2 * Math.PI);

                            if (disPoint >= disCenter - radius && disPoint <= disCenter + radius && arcPoint >= arcCenter - arcMatch && arcPoint <= arcCenter + arcMatch) {
//                            Log_d(LOG_TAG, "MOVE: view " + getResources().getResourceName(view.getId()) + " " + view.getWidth() + ":" + view.getHeight());
                                highlightSelection(view, disCenter, arcCenter, arcMatch);
                                found = true;
                                break;
                            }

                        }

                        // no option selected
//                    if (!found && (!mOptionSnap || ((Math.pow(mX - dX, 2) / Math.pow(rX, 2) + Math.pow(mY - dY, 2) / Math.pow(rY, 2) <= 1))))
                        if (!found && (!mOptionSnap || (isAbsCoordsMatchingMenuHitBox(mX, mY, dX, dY, rX, rY))))
                            clearSelection();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if ((downPoint = mOptionMenuTrack.get(motionEvent.getPointerId(actionIndex))) != null) {
                        mOptionMenuTrack.remove(motionEvent.getPointerId(actionIndex));
//                      Log_d(LOG_TAG, "UP|POINTER_UP: " + motionEvent.getPointerId(actionIndex) + " #" + mOptionMenuTrack.size());

                        // snap is enabled and option is selected
                        if (mOptionSnap && mCurrentHighlightedOption != null) {
                            Log_d(LOG_TAG, "option: " + getResources().getResourceName(mCurrentHighlightedOption.getId()));
                            fireOnMenuActionListener(mCurrentHighlightedOption);
                            closeMenu();
                            break;
                        }

                        // TODO calc arc's doesn't work properly (until then break here)
                        if (dY > mY) {
                            closeMenu();
                            break;
                        }

                        // check coordinates
                        final double disPoint = Math.sqrt(Math.pow(dX - mX, 2) + Math.pow(dY - mY, 2));
                        if (disPoint == 0)
                            break;

                        final double arcPoint = Math.toDegrees(Math.acos((dX - mX) / disPoint));

                        // find ImageView at coordinates
                        Rect hitRect = new Rect();
                        for (int idx : mOptionViews.keySet()) {
                            View view = mOptionViews.get(idx);

                            // ignore invisible views
                            if (view.getVisibility() == View.INVISIBLE)
                                continue;

                            view.getGlobalVisibleRect(hitRect);
                            radius = hitRect.centerX() - hitRect.left;

                            final double disCenter =  Math.sqrt(Math.pow(hitRect.centerX() - mX, 2) + Math.pow(hitRect.centerY() - mY, 2));
                            final double arcCenter = Math.toDegrees(Math.acos((hitRect.centerX() - mX) / disCenter));
                            final double arcMatch = 360 * radius / (disCenter * 2 * Math.PI);

                            if (disPoint >= disCenter - radius && disPoint <= disCenter + radius && arcPoint >= arcCenter - arcMatch && arcPoint <= arcCenter + arcMatch) {
                                fireOnMenuActionListener(view);
                                closeMenu();
                                break;
                            }
                        }
                    }
                    closeMenu();
                    break;
            }

            return true;
        }

        public boolean isAbsCoordsMatchingMenuHitBox(MotionEvent motionEvent) {
            boolean result = false;

            final int actionIndex = motionEvent.getActionIndex(), actionMasked = motionEvent.getActionMasked();

            // absolute coordinates
            Rect mOptionMenuRect = new Rect();
            getGlobalVisibleRect(mOptionMenuRect);
            // coordinates
            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            motionEvent.getPointerCoords(actionIndex, pointerCoords);

            final float dX = motionEvent.getX(actionIndex), mX = mOptionMenuRect.centerX();
            final float dY = motionEvent.getY(actionIndex), mY = mOptionMenuRect.centerY();

            return isAbsCoordsMatchingMenuHitBox(mX, mY, dX, dY, rX, rY);
        }

        private boolean isAbsCoordsMatchingMenuHitBox(final float mX, final float mY, final float dX, final float dY, final float rX, final float rY) {
            // ellipse
            return (Math.pow(mX - dX, 2) / Math.pow(rX, 2) + Math.pow(mY - dY, 2) / Math.pow(rY, 2) <= 1);
        }

        public boolean isOpen() {
            return (mOptions.getVisibility() == View.VISIBLE);
        }

        public void animateMenuButton(boolean animate) {
            if (animate)
                mOptionMenuButtonAnim.start();
            else
                mOptionMenuButtonAnim.stop();
        }

        private boolean openMenu() {
            Log_d(LOG_TAG, "openMenu");

            if (mOnMenuOpenListener == null || (mOnMenuActionListener = mOnMenuOpenListener.onMenuOpen()) == null) {
                Log_d(LOG_TAG, "openMenu: " + mOnMenuOpenListener + ", " + mOnMenuActionListener);
                return false;
            }

            try {
                if (!mMenuList.containsKey(mOnMenuActionListener.getMenuId()))
                    mOnMenuActionListener.onMenuInit(this);

                Menu menu = mMenuList.get(mOnMenuActionListener.getMenuId());

                if (menu == null || menu.getOptions().size() == 0 || !mOnMenuActionListener.onMenuOpen(menu)) {
                    Log_d(LOG_TAG, "openMenu: canceled. " + menu);
                    return false;
                }

                setupMenu(menu);
            } catch (Exception e) {
                Log_e(LOG_TAG, "openMenu: exception occurred! " + e.getMessage());
                return false;
            }

            mOptions.setVisibility(View.VISIBLE);
            mOverlayLayout.bringChildToFront(mOptions);
            mOverlayLayout.requestLayout();
            mOverlayLayout.invalidate();

            return true;
        }

        private void closeMenu() {
            Log_d(LOG_TAG, "closeMenu");

            clearSelection();
            mOptions.setVisibility(View.INVISIBLE);
        }

        private void processImageViews() {
            processImageViews(mOptions, false);
        }

        private void processImageViews(RelativeLayout layout, boolean recursive) {
            Log_d(LOG_TAG, "processImageViews: " + recursive + ", " + layout);

            for (int idx=0; idx < layout.getChildCount(); idx++) {
                // image view's
                if (layout.getChildAt(idx) instanceof ImageView) {
                    ImageView img = (ImageView) layout.getChildAt(idx);
                    addImageViewToHashMap(img);

                    if (!recursive)
                        ((RelativeLayout.LayoutParams)img.getLayoutParams()).setMargins(0, (int)(mOverlayLayout.getHeight() - mButtonSize * 2.30f), 0, 0);
                }
                // text view's
                if ((layout.getChildAt(idx) instanceof TextView)) {
                    TextView textView = (TextView)layout.getChildAt(idx);
                    int padding = 0, spacing = 0;
                    switch (textView.getId()) {
                        case R.id.spacer_2_3:
                            padding = (int)(mButtonSize * 0.90f);
                            spacing = (int)(mButtonSize * 2.00f);
                            break;
                        case R.id.spacer_4_5:
                            padding = (int)(mButtonSize * 2.00f);
                            spacing = (int)(mButtonSize * 1.00f);
                            break;
                        case R.id.spacer_6_7:
                            padding = (int)(mButtonSize * 0.45f);
                            spacing = (int)(mButtonSize * 3.20f);
                            break;
                        case R.id.spacer_8_9:
                            padding = (int)(mButtonSize * 2.00f);
                            spacing = (int)(mButtonSize * 2.20f);
                            break;
                    }
                    if (padding > 0)
                        textView.setPadding(padding, 0, padding, 0);
                    if (spacing > 0)
                        ((RelativeLayout.LayoutParams)layout.getLayoutParams()).setMargins(0, mOverlayLayout.getHeight() - spacing, 0, 0);
                }
                // relative layout's
                if ((layout.getChildAt(idx) instanceof RelativeLayout) && !recursive)
                    processImageViews((RelativeLayout)layout.getChildAt(idx), true);
            }
        }

        private void addImageViewToHashMap(ImageView imageView) {
            mOptionViews.put(mOptionViews.size(), imageView);

            imageView.getLayoutParams().width = mButtonSize;
            imageView.getLayoutParams().height = mButtonSize;
            imageView.setAlpha(0.75f);
            imageView.setClickable(false);
            imageView.setFocusable(false);
        }

        private void highlightSelection(View view, double disCenter, double arcCenter, double arcMatch) {
            if (mCurrentHighlightedOption != view) {
                Log_d(LOG_TAG, "highlightSelection: " + getResources().getResourceName(view.getId()));

                mCurrentHighlightedOption = view;

                Bitmap bmp = Bitmap.createBitmap(mOptions.getWidth(), mOptions.getHeight(), Bitmap.Config.ARGB_8888);
//            Log_d(LOG_TAG, "view: " + view.getLeft() + ":" + view.getTop() + " - " + view.getWidth() + ":" + view.getHeight());

                // translate coordinates
                final float radius = view.getHeight() / 2, snap = 20f, stroke = 5f;
                Rect viewRect = new Rect(), drawRect = new Rect();
                view.getGlobalVisibleRect(viewRect);
                mOptions.getGlobalVisibleRect(drawRect);

//            Log_d(LOG_TAG, "draw: " + drawRect.left + ":" + drawRect.bottom + ", " + mOptions.getLeft() + ":" + mOptions.getBottom());

                PointF drawCenter = new PointF(mOptions.getWidth() / 2, mOptions.getBottom() + getHeight() / 2);
                PointF viewCenter = new PointF(viewRect.left - drawRect.left + radius, viewRect.top - drawRect.top + radius);

                PointF viewCenterTop = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, 0f, radius);
                PointF viewLeftTop = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)arcMatch, radius + snap);
                PointF viewRightTop = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)-arcMatch, radius + snap);
                // circular segment
//            PointF viewLeftBottom = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)arcMatch, -radius);
//            PointF viewRightBottom = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)-arcMatch, -radius);
                // arrow to center
                PointF viewLeftBottom = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)arcMatch, radius + snap / 2);
                PointF viewRightBottom = OptionMenuHelper.movePointOnCircularSegment(drawCenter, viewCenter, (float)-arcMatch, radius + snap / 2);

                Path path = new Path();
                path.moveTo(viewLeftTop.x, viewLeftTop.y);
                final float drawRadiusTop = (float)(disCenter + radius + snap);
                path.arcTo(new RectF(drawCenter.x - drawRadiusTop, drawCenter.y - drawRadiusTop, drawCenter.x + drawRadiusTop, drawCenter.y + drawRadiusTop), (float) -(arcCenter + arcMatch), (float) arcMatch * 2, true);
                path.lineTo(viewRightBottom.x, viewRightBottom.y);
                // circular segment
//            final float drawRadiusBottom = (float)(disCenter - radius);
//            path.arcTo(new RectF(drawCenter.x - drawRadiusBottom, drawCenter.y - drawRadiusBottom, drawCenter.x + drawRadiusBottom, drawCenter.y + drawRadiusBottom), (float) -(arcCenter - arcMatch), (float) -arcMatch * 2, true);
                // arrow to center
                path.lineTo(viewCenterTop.x, viewCenterTop.y);
                path.lineTo(viewLeftBottom.x, viewLeftBottom.y);
                // closeMenu path
                path.lineTo(viewLeftTop.x, viewLeftTop.y);

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

                mOptions.setBackground(new BitmapDrawable(getResources(), bmp));
            }
        }

        private void clearSelection() {
            clearSelection(false);
        }

        private void clearSelection(boolean force) {
            if (mCurrentHighlightedOption != null || force) {
                Log_d(LOG_TAG, "clearSelection: " + force);
                mOptions.setBackgroundColor(mBackgroundColor);
                mCurrentHighlightedOption = null;
            }
        }

        private void fireOnMenuActionListener(View view) {
            if (mOnMenuActionListener != null && view.getTag() != null)
                try {
                    mOnMenuActionListener.onMenuAction((MenuOption)view.getTag());
                } catch (Exception e) {
                    Log_e(LOG_TAG, "fireOnMenuActionListener: exception occurred! " + e.getMessage());
                }
        }

        private boolean setupMenu(Menu menu) {
            for (int idx=0; idx<mOptionViews.size(); idx++) {
                ImageView imageView = (ImageView)mOptionViews.values().toArray()[idx];
                Option option = menu.getOption(idx);

                imageView.setVisibility((option == null ? INVISIBLE : VISIBLE));
                imageView.setTag((option == null ? null : new MenuOption(menu, option)));
                if (option != null)
                    imageView.setImageDrawable(getResources().getDrawable(option.getImageId()));
            }

            return true;
        }

        public void registerMenuOption(int menuResId, int optionResId, int drawableResId) {
            Menu menu = mMenuList.get(menuResId);

            if (menu == null)
                mMenuList.put(menuResId, (menu = new Menu(menuResId)));

            Option option = new Option(optionResId, drawableResId);
            menu.addOption(option);
            mOptionList.put(optionResId, option);
        }

        public void registerOnOpenListener(OnMenuOpenListener menuOpenListener) {
            mOnMenuOpenListener = menuOpenListener;
        }

        private static class OptionMenuHelper {
            public static PointF movePointOnCircularSegment(PointF center, PointF reference, Float arc, Float offset) {
                PointF result = new PointF(0, 0);

                final double radius = Math.sqrt(Math.pow(reference.x - center.x, 2) + Math.pow(reference.y - center.y, 2));
                final double arcC = Math.toDegrees(Math.acos((reference.x - center.x) / radius));
                final double factor = (radius + offset) / radius;

                result.x = (float)(center.x + factor * radius * Math.cos(Math.toRadians(arcC + arc)));
                result.y = (float)(center.y - factor * radius * Math.sin(Math.toRadians(arcC + arc)));

//            Log_d(LOG_TAG, "center: " + center + ", reference: " + reference + ", radius: " + radius + ", arcC: " + arcC + ", arc: " + arc + ", result: " + result);

                return result;
            }
        }
    }

    public static class MenuLayout extends Child {

        private final String LOG_TAG = "ComponentFramework.MenuLayout";

        public MenuLayout(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
        }
    }

}
