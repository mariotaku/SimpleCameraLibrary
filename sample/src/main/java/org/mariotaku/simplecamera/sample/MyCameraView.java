package org.mariotaku.simplecamera.sample;

import android.content.Context;
import android.util.AttributeSet;

import org.mariotaku.simplecamera.CameraView;
import org.mariotaku.simplecamera.Preview;

/**
 * Created by mariotaku on 14-9-15.
 */
public class MyCameraView extends CameraView {
    public MyCameraView(Context context) {
        super(context);
    }

    public MyCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyCameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    protected Preview createPreview() {
        return new MyTexturePreview(this);
    }

}
