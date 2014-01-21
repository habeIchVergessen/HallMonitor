package org.durka.hallmonitor_framework_test;

import android.app.Activity;
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by habeIchVergessen on 03.01.14.
 *
 * wraps all the option overlay functionality
 */
public class ComponentMenu extends ComponentFramework.Menu {
    private final static String LOG_TAG = "ComponentMenu";

    private Context mContext = null;
    private HashMap<Integer,PointF> mOptionMenuTrack = new HashMap<Integer, PointF>();
    private HashMap<Integer,ImageView> mOptionViews = new HashMap<Integer, ImageView>();

    private int mBackgroundColor = 0xCC000000;
    private int mButtonSize = 52;

    private final float rX = 50, rY = 30;

    private int mOptionsResourceId = -1;
    private RelativeLayout mOptions = null;
    private boolean mOptionSnap = true;
    private ImageView mOptionMenuButton = null;
    private AnimationDrawable mOptionMenuButtonAnim = null;
    private ViewGroup mOverlayLayout = null;

    private View mCurrentHighlightedOption = null;

    private HashMap<Integer,Menu> mMenuList = new HashMap<Integer, Menu>();
    private HashMap<Integer,Option> mOptionList = new HashMap<Integer, Option>();
    private OnMenuOpenListener mOnMenuOpenListener = null;

    public interface OnMenuOpenListener {
        public boolean onMenuOpen();
    }

    public interface OnMenuActionListener {
        public boolean onMenuAction(MenuOption menuOption);
    }

    public ComponentMenu(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        // override layout
        setClickable(false);
        setFocusable(false);

        // read user-defined attributes
        TypedArray styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.ComponentMenu);

        // read background color
        mBackgroundColor = styledAttrs.getInt(R.styleable.ComponentMenu_overlayBackgroundColor, getBackgroundColor());
        // alpha
        mBackgroundColor = (mBackgroundColor & 0xFFFFFF) + 0xCC000000;
        // read button size
        mButtonSize = styledAttrs.getInt(R.styleable.ComponentMenu_buttonSize, mButtonSize);
        // read option snap
        mOptionSnap = styledAttrs.getBoolean(R.styleable.ComponentMenu_enableOptionSnap, mOptionSnap);
        // resizing and positioning buttons
        if ((mOptionsResourceId = styledAttrs.getResourceId(R.styleable.ComponentMenu_overlayLayout, -1)) != -1)
            setupOverlayLayout();

        // set my id
        setId(R.id.componentMenu);

        // menu button
        mOptionMenuButton = new ImageView(context, attrs);
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
            Log.d(LOG_TAG, "setupOverlayLayout: " + getResources().getResourceName(mOptionsResourceId));

            View view = getRootView().findViewById(mOptionsResourceId);

            if (view != null && (view instanceof ViewGroup)) {
                mOverlayLayout = (ViewGroup)view;

                // options
                mOptions = (RelativeLayout)((Activity)mContext).getLayoutInflater().inflate(R.layout.component_menu, mOverlayLayout, false);
                mOverlayLayout.addView(mOptions);

                // init defaults
                close();
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
    public boolean onTouchEvent(MotionEvent motionEvent) {
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

        Log_d(LOG_TAG, "onTouchEvent: " + pointerCoords.x + ":" + pointerCoords.y);

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
                    open();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if ((downPoint = mOptionMenuTrack.get(motionEvent.getPointerId(actionIndex))) != null) {
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
//                            Log.d(LOG_TAG, "MOVE: view " + getResources().getResourceName(view.getId()) + " " + view.getWidth() + ":" + view.getHeight());
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
//                    Log.d(LOG_TAG, "UP|POINTER_UP: " + motionEvent.getPointerId(actionIndex) + " #" + mOptionMenuTrack.size());

                    // snap is enabled and option is selected
                    if (mOptionSnap && mCurrentHighlightedOption != null) {
                        Log.d(LOG_TAG, "option: " + getResources().getResourceName(mCurrentHighlightedOption.getId()));
                        fireOnMenuActionListener(mCurrentHighlightedOption);
                        close();
                        break;
                    }

                    // TODO calc arc's doesn't work properly (until then break here)
                    if (dY > mY) {
                        close();
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
                            close();
                            break;
                        }
                    }
                }
                close();
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

    private void open() {
        Log.d(LOG_TAG, "open");
        if (mOnMenuOpenListener != null && !mOnMenuOpenListener.onMenuOpen())
            return;

        mOptions.setVisibility(View.VISIBLE);
        mOverlayLayout.bringChildToFront(mOptions);
        mOverlayLayout.requestLayout();
        mOverlayLayout.invalidate();
    }

    private void close() {
        Log.d(LOG_TAG, "close");

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
            Log.d(LOG_TAG, "highlightSelection: " + getResources().getResourceName(view.getId()));

            mCurrentHighlightedOption = view;

            Bitmap bmp = Bitmap.createBitmap(mOptions.getWidth(), mOptions.getHeight(), Bitmap.Config.ARGB_8888);
//            Log.d(LOG_TAG, "view: " + view.getLeft() + ":" + view.getTop() + " - " + view.getWidth() + ":" + view.getHeight());

            // translate coordinates
            final float radius = view.getHeight() / 2, snap = 20f, stroke = 5f;
            Rect viewRect = new Rect(), drawRect = new Rect();
            view.getGlobalVisibleRect(viewRect);
            mOptions.getGlobalVisibleRect(drawRect);

//            Log.d(LOG_TAG, "draw: " + drawRect.left + ":" + drawRect.bottom + ", " + mOptions.getLeft() + ":" + mOptions.getBottom());

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
            // close path
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
            Log.d(LOG_TAG, "clearSelection: " + force);
            mOptions.setBackgroundColor(mBackgroundColor);
            mCurrentHighlightedOption = null;
        }
    }

    private RelativeLayout addRelativeLayout(ViewGroup parent, int id) {
        RelativeLayout layout = new RelativeLayout(parent.getContext());

        parent.addView(layout);

        layout.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
        layout.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        layout.setId(id);

        return layout;
    }

    private static class OptionMenuHelper {
        public static PointF movePointOnCircularSegment(PointF center, PointF reference, Float arc, Float offset) {
            PointF result = new PointF(0, 0);

            final double radius = Math.sqrt(Math.pow(reference.x - center.x, 2) + Math.pow(reference.y - center.y, 2));
            final double arcC = Math.toDegrees(Math.acos((reference.x - center.x) / radius));
            final double factor = (radius + offset) / radius;

            result.x = (float)(center.x + factor * radius * Math.cos(Math.toRadians(arcC + arc)));
            result.y = (float)(center.y - factor * radius * Math.sin(Math.toRadians(arcC + arc)));

//            Log.d(LOG_TAG, "center: " + center + ", reference: " + reference + ", radius: " + radius + ", arcC: " + arcC + ", arc: " + arc + ", result: " + result);

            return result;
        }
    }

    private void fireOnMenuActionListener(View view) {
//        if (mOnMenuOpenListener != null)
//            mOnMenuOpenListener.onMenuAction((MenuOption)view.getTag());
    }

    public boolean setupMenuForView(int viewResId) {
        return setupMenuForView(mOverlayLayout.findViewById(viewResId));
    }

    private boolean setupMenuForView(View view) {
        if (view == null || !mMenuList.containsKey(view.getId()))
            return false;

        Menu menu = mMenuList.get(view.getId());

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

    public void registerMenuOption(int viewId, int optionId, int imageId) {
        Menu menu;
        Option option;

        Log.d("Test", "registerMenuOption: menu");
        if ((menu= mMenuList.get(viewId)) == null) {
            menu = new Menu(viewId);
            mMenuList.put(viewId, menu);
        }

        Log.d("Test", "registerMenuOption: option");
        if ((option = mOptionList.get(optionId)) == null) {
            option = new Option(optionId, imageId);
            mOptionList.put(optionId, option);
        }

        Log.d("Test", "registerMenuOption: addOption");
        menu.addOption(option);
    }

    public void registerOnMenuOpenListener(OnMenuOpenListener onMenuOpenListener) {
        mOnMenuOpenListener = onMenuOpenListener;
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

}
