package org.mariotaku.simplecamera.sample;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by mariotaku on 14-7-30.
 */
public class FocusAreaView extends View implements Animator.AnimatorListener {

    public static final Property<View, Float> SCALE_XY = new Property<View, Float>(Float.TYPE, "") {
        @Override
        public void set(View object, Float value) {
            object.setScaleX(value);
            object.setScaleY(value);
        }

        @Override
        public Float get(View object) {
            return (object.getScaleX() + object.getScaleY()) / 2;
        }
    };
    private boolean mFocusEnd;

    public FocusAreaView(Context context) {
        this(context, null);
    }

    public FocusAreaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FocusAreaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundResource(R.drawable.btn_focus_area);
    }

    public void startFocus(float x, float y) {
        if (getVisibility() == VISIBLE) return;
        mFocusEnd = false;
        final ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();
        final int width = getWidth() > 0 ? getWidth() : layoutParams.width;
        final int height = getHeight() > 0 ? getHeight() : layoutParams.height;
        if (width == 0 || height == 0) return;
        layoutParams.leftMargin = Math.round(x) - width / 2;
        layoutParams.topMargin = Math.round(y) - height / 2;
        setLayoutParams(layoutParams);
        setVisibility(View.VISIBLE);
        final float scaleTo = 1.2f;
        final ObjectAnimator pulse = ObjectAnimator.ofFloat(this, SCALE_XY, 1, scaleTo, 1);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setDuration(500);
        pulse.start();
        pulse.addListener(this);
    }

    public void endFocus() {
        mFocusEnd = true;
        setVisibility(View.GONE);
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {

    }

    @Override
    public void onAnimationCancel(Animator animation) {
        setVisibility(View.GONE);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        if (mFocusEnd) {
            animation.cancel();
        }
    }
}
