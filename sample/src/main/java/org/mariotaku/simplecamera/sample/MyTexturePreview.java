package org.mariotaku.simplecamera.sample;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.TextureView;
import android.view.View;

import org.mariotaku.simplecamera.TexturePreview;

/**
* Created by mariotaku on 14-9-16.
*/
class MyTexturePreview extends TexturePreview {
    public MyTexturePreview(MyCameraView myCameraView) {
        super(myCameraView);
    }
}
