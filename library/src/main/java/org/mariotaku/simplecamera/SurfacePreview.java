package org.mariotaku.simplecamera;

import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

/**
 * Created by mariotaku on 14-9-17.
 */
public class SurfacePreview implements Preview, SurfaceHolder.Callback {

    private final CameraView mCameraView;
    private final SurfaceView mSurfaceView;

    public SurfacePreview(CameraView cameraView) {
        mCameraView = cameraView;
        mSurfaceView = new SurfaceView(cameraView.getContext());
        mSurfaceView.getHolder().addCallback(this);
    }

    @Override
    public View getView() {
        return mSurfaceView;
    }

    @Override
    public void layoutPreview(boolean changed, int l, int t, int r, int b) {
//        mSurfaceView.layout(0, 0, mCameraView.getMeasuredWidth(), mCameraView.getMeasuredHeight());
        final Camera camera = mCameraView.getOpeningCamera();
        updateSurface(camera, mSurfaceView.getHolder(), mSurfaceView.getMeasuredWidth(), mSurfaceView.getMeasuredHeight());
    }

    @Override
    public boolean isAttachedToCameraView() {
        return mSurfaceView.getParent() == mCameraView;
    }

    @Override
    public void onPreReleaseCamera(Camera camera) {
        mSurfaceView.getHolder().removeCallback(this);
    }

    @Override
    public void attachMediaRecorder(MediaRecorder recorder) {
        recorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
    }

    @Override
    public void detachMediaRecorder(MediaRecorder recorder) {
        final Camera camera = mCameraView.getOpeningCamera();
        try {
            camera.setPreviewDisplay(mSurfaceView.getHolder());
        } catch (IOException e) {
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        holder.setFormat(PixelFormat.TRANSLUCENT);
        final Camera camera = mCameraView.openCameraIfNeeded();
        if (camera == null) return;
        try {
            final Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            mCameraView.requestLayout();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//        updateSurface(mCameraView.getOpeningCamera(), holder, width, height);
    }

    private void updateSurface(final Camera camera, final SurfaceHolder holder, final int width,
                               final int height) {
        if (camera == null || width == 0 || height == 0) return;
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
        final int actualW, actualH;
        if (viewRatio > cameraRatio) {
            // fit width
            actualW = width;
            actualH = Math.round(width / cameraRatio);
        } else {
            // fit height
            actualW = Math.round(height * cameraRatio);
            actualH = height;
        }
//        holder.setFixedSize(actualW, actualH);
        final int translateX, translateY;
        if (viewRatio > cameraRatio) {
            translateX = 0;
            translateY = Math.round(-(width / cameraRatio - height) / 2);
        } else {
            translateX = Math.round(-(height * cameraRatio - width) / 2);
            translateY = 0;
        }
        mSurfaceView.layout(translateX, translateY, translateX + actualW, translateY + actualH);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCameraView.releaseCamera();
    }
}
