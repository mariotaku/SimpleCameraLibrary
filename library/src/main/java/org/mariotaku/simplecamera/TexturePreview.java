package org.mariotaku.simplecamera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;

/**
 * Created by mariotaku on 14-9-9.
 */
final class TexturePreview implements Preview, TextureView.SurfaceTextureListener {

    private final CameraView mCameraView;
    private final TextureView mTextureView;
    private int mLeft, mTop;

    public TexturePreview(CameraView cameraView) {
        mCameraView = cameraView;
        mTextureView = new InternalTextureView(cameraView.getContext());
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    public View getView() {
        return mTextureView;
    }

    @Override
    public void layoutPreview(boolean changed, int l, int t, int r, int b) {
        mTextureView.layout(mLeft, mTop, mLeft + mTextureView.getMeasuredWidth(), mTop + mTextureView.getMeasuredHeight());
    }

    @Override
    public boolean isAttachedToCameraView() {
        return mTextureView.getParent() == mCameraView;
    }

    @Override
    public boolean getDisplayBounds(Rect bounds) {
        final Camera camera = mCameraView.getOpeningCamera();
        final int width = mCameraView.getWidth(), height = mCameraView.getHeight();
        if (camera == null || width == 0 || height == 0) return false;
        final Camera.Size size = camera.getParameters().getPreviewSize();
        if (size == null) return false;
        final int rotation = CameraUtils.getDisplayRotation(mCameraView.getContext());
        final boolean isPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;
        final int cameraWidth = isPortrait ? size.height : size.width;
        final int cameraHeight = isPortrait ? size.width : size.height;
        final float viewRatio = (float) width / height, cameraRatio = (float) cameraWidth / cameraHeight;
        if (viewRatio > cameraRatio) {
            // fit width
            final int displayHeight = Math.round(cameraWidth / viewRatio);
            final int top = (cameraHeight - displayHeight) / 2;
            bounds.set(0, top, cameraWidth, top + displayHeight);
        } else {
            // fit height
            final int displayWidth = Math.round(cameraHeight * viewRatio);
            final int left = (cameraWidth - displayWidth) / 2;
            bounds.set(left, 0, left + displayWidth, cameraHeight);
        }
        return true;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        final Camera camera = mCameraView.openCameraIfNeeded();
        if (camera == null) return;
        try {
            final Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.setPreviewTexture(surface);
            updateSurface(camera, width, height);
            camera.startPreview();
            mCameraView.requestLayout();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateSurface(final Camera camera, final int width, final int height) {
        final Camera.Size size = camera.getParameters().getPreviewSize();
        final int rotation = CameraUtils.getDisplayRotation(mCameraView.getContext());
        final boolean isPortrait;
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            isPortrait = true;
        } else {
            isPortrait = false;
        }
        final int cameraWidth = isPortrait ? size.height : size.width;
        final int cameraHeight = isPortrait ? size.width : size.height;
        final float viewRatio = (float) width / height, cameraRatio = (float) cameraWidth / cameraHeight;
        final Matrix transform = new Matrix();
        if (viewRatio > cameraRatio) {
            // fit width
            transform.setScale(1, width / cameraRatio / height);
        } else {
            // fit height
            transform.setScale(height * cameraRatio / width, 1);
        }
        mTextureView.setTransform(transform);
        if (viewRatio > cameraRatio) {
            mLeft = 0;
            mTop = -Math.round((width / cameraRatio - height)) / 2;
        } else {
            mLeft = -Math.round((height * cameraRatio - width)) / 2;
            mTop = 0;
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mCameraView.requestLayout();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCameraView.releaseCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private class InternalTextureView extends TextureView {
        public InternalTextureView(Context context) {
            super(context);
        }


        @Override
        public void invalidate(Rect dirty) {
            super.invalidate(dirty);
            Log.d("SimpleCameraView", String.format("invalidate, %s", dirty));
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            super.invalidate(l, t, r, b);
            Log.d("SimpleCameraView", String.format("invalidate, l:%d, t:%d, r:%d, b:%d", l, t, r, b));
        }

        @Override
        public void invalidate() {
            super.invalidate();
        }
    }
}
