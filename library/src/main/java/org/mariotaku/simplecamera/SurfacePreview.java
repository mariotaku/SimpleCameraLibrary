package org.mariotaku.simplecamera;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
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
    private boolean mAttachedToCamera;

    public SurfacePreview(CameraView cameraView) {
        mCameraView = cameraView;
        mSurfaceView = new SurfaceView(cameraView.getContext());
        final SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    public View getView() {
        return mSurfaceView;
    }

    @Override
    public void layoutPreview(boolean changed, int l, int t, int r, int b) {
//        mSurfaceView.layout(0, 0, mCameraView.getMeasuredWidth(), mCameraView.getMeasuredHeight());
        notifyPreviewSizeChanged(r - l, b - t);
    }

    @Override
    public boolean isAddedToCameraView() {
        return mSurfaceView.getParent() == mCameraView;
    }

    @Override
    public boolean isAttachedToCamera() {
        return mAttachedToCamera;
    }

    @Override
    public void onPreReleaseCamera(Camera camera) {
        mCameraView.setCameraPreviewStarted(false);
        camera.stopPreview();
        try {
            mAttachedToCamera = false;
            camera.setPreviewDisplay(null);
        } catch (IOException e) {
            Log.w(CameraView.LOGTAG, e);
        }
        mSurfaceView.getHolder().removeCallback(this);
    }

    @Override
    public void attachMediaRecorder(MediaRecorder recorder) {
        recorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
    }

    @Override
    public void detachMediaRecorder(MediaRecorder recorder) {
        final Camera camera = mCameraView.getOpeningCamera();
        if (camera == null) return;
        try {
            camera.setPreviewDisplay(mSurfaceView.getHolder());
        } catch (IOException e) {
            Log.w(CameraView.LOGTAG, e);
        }
    }

    @Override
    public boolean shouldSetSizeForRecorder() {
        return true;
    }

    @Override
    public void notifyPreviewSizeChanged(int width, int height) {
        final Camera camera = mCameraView.getOpeningCamera();
        if (camera == null) return;
        final SurfaceHolder holder = mSurfaceView.getHolder();
        if (width != 0 && height != 0) {
            updateSurface(camera, holder, width, height);
            return;
        }
        final int viewWidth = mSurfaceView.getWidth(), viewHeight = mSurfaceView.getHeight();
        if (viewWidth != 0 && viewHeight != 0) {
            updateSurface(camera, holder, viewWidth, viewHeight);
        } else {
            final int measuredWidth = mSurfaceView.getMeasuredWidth();
            final int measuredHeight = mSurfaceView.getMeasuredHeight();
            updateSurface(camera, holder, measuredWidth, measuredHeight);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        final Camera camera = mCameraView.openCameraIfNeeded();
        if (camera == null) return;
        try {
            final Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.setPreviewDisplay(holder);
            mAttachedToCamera = true;
            camera.startPreview();
            mCameraView.setCameraPreviewStarted(true);
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
        final boolean isPortrait = mCameraView.getCameraRotation() % 180 != 0;
        final int cameraWidth = isPortrait ? size.height : size.width;
        final int cameraHeight = isPortrait ? size.width : size.height;
        final float viewRatio = (float) width / height, cameraRatio = (float) cameraWidth / cameraHeight;
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
