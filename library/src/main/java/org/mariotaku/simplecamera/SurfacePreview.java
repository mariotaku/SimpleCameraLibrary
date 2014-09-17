package org.mariotaku.simplecamera;

import android.hardware.Camera;
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
        mSurfaceView.layout(0, 0, mCameraView.getMeasuredWidth(), mCameraView.getMeasuredHeight());
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
    public void surfaceCreated(SurfaceHolder holder) {
        final Camera camera = mCameraView.openCameraIfNeeded();
        if (camera == null) return;
        try {
            final Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.setPreviewDisplay(holder);
//            updateSurface(camera, width, height);
            camera.startPreview();
            mCameraView.requestLayout();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCameraView.releaseCamera();
    }
}
