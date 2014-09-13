package org.mariotaku.simplecamera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mariotaku on 14-9-9.
 */
public class CameraView extends ViewGroup {

    private static final String LOGTAG = "CameraView";

    private Preview mPreview;
    private int mOpeningCameraId;
    private Camera mOpeningCamera;
    private Listener mListener;
    private int mCameraRotation;
    private boolean mSingleShot;
    private int mCameraId;
    private MediaRecorder mRecorder;
    private boolean videoRecordStarted;
    private boolean mAutoFocusing;

    public int getCameraRotation() {
        return mCameraRotation;
    }

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setClipChildren(false);
        initPreview();
    }

    private void initPreview() {
        removeAllViews();
        mPreview = createPreview();
        addViewInternal(mPreview.getView());
    }

    public void openCamera(int cameraId) {
        mCameraId = cameraId;
        initPreview();
    }

    private Camera openCameraSafely(final int cameraId) {
        final Camera oldCamera = mOpeningCamera;
        if (oldCamera != null) {
            oldCamera.release();
        }
        try {
            final Camera camera = Camera.open(cameraId);
            mOpeningCameraId = cameraId;
            mOpeningCamera = camera;
            if (mListener != null) {
                mListener.onCameraInitialized(camera);
            }
            return camera;
        } catch (Exception e) {
            Log.e(LOGTAG, "Error opening camera", e);
        }
        mOpeningCamera = null;
        mOpeningCameraId = -1;
        return null;
    }

    public VideoRecordConfig newVideoRecordConfig() {
        if (mOpeningCameraId == -1) return null;
        return new VideoRecordConfig(mOpeningCameraId);
    }

    public void setAutoFocusing(boolean autoFocusing) {
        this.mAutoFocusing = autoFocusing;
    }


    public static final class VideoRecordTransaction {

        private final CameraView cameraView;
        private final VideoRecordConfig config;
        private final VideoRecordCallback callback;
        private Object extra;

        public Object getExtra() {
            return extra;
        }

        public void setExtra(Object extra) {
            this.extra = extra;
        }

        VideoRecordTransaction(CameraView cameraView, VideoRecordConfig config, VideoRecordCallback callback) {
            this.cameraView = cameraView;
            this.config = config;
            this.callback = callback;
        }


        public VideoRecordConfig getConfig() {
            return config;
        }

        public void stop() {
            final MediaRecorder recorder = cameraView.getCurrentMediaRecorder();
            if (recorder == null) {
                return;
            }
            try {
                recorder.stop();
                recorder.reset();
                recorder.release();
            } catch (RuntimeException e) {
                //Ignore
            }
            final Camera camera = cameraView.getOpeningCamera();
            if (camera != null) {
                camera.lock();
                try {
                    camera.reconnect();
                } catch (IOException e) {
                }
                camera.startPreview();
            }
            cameraView.post(new NotifyRecordStopRunnable(callback));
            cameraView.setCurrentMediaRecorder(null);
        }


        private static class NotifyRecordStopRunnable implements Runnable {
            private final VideoRecordCallback callback;

            public NotifyRecordStopRunnable(VideoRecordCallback callback) {
                this.callback = callback;
            }

            @Override
            public void run() {
                if (callback == null) return;
                callback.onRecordStopped();
            }
        }
    }

    private MediaRecorder getCurrentMediaRecorder() {
        return mRecorder;
    }

    public interface VideoRecordCallback extends MediaRecorder.OnInfoListener {
        void onRecordStarted();

        void onRecordError(Exception e);

        void onRecordStopped();
    }


    private static class RecordVideoRunnable implements Runnable {

        private final CameraView cameraView;
        private final MediaRecorder recorder;
        private final VideoRecordCallback callback;
        private final VideoRecordConfig config;

        private RecordVideoRunnable(CameraView cameraView, MediaRecorder recorder,
                                    VideoRecordConfig config, VideoRecordCallback callback) {
            this.cameraView = cameraView;
            this.recorder = recorder;
            this.callback = callback;
            this.config = config;
        }

        @Override
        public void run() {
            final Camera camera = cameraView.getOpeningCamera();
            if (camera == null) return;
            try {
                camera.unlock();
                recorder.setCamera(camera);
                recorder.setOnInfoListener(callback);
                recorder.setAudioSource(config.audioSource);
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                recorder.setProfile(config.profile);
                config.applyOutputFile(recorder);
                recorder.setOrientationHint(cameraView.getVideoRotation());
//                cameraView.previewStrategy.attach(recorder);
                recorder.prepare();
                recorder.start();
                cameraView.videoRecordStarted = true;
                cameraView.post(new NotifyRecordStartRunnable(callback));
            } catch (Exception e) {
                cameraView.videoRecordStarted = false;
                recorder.reset();
                recorder.release();
                camera.lock();
                cameraView.setCurrentMediaRecorder(null);
                cameraView.post(new NotifyRecordFailedRunnable(callback, e));
//                Log.e(Constants.LOGTAG, "Error recording video", e);
            }
        }

        private static class NotifyRecordFailedRunnable implements Runnable {
            private final VideoRecordCallback callback;
            private final Exception exception;

            public NotifyRecordFailedRunnable(VideoRecordCallback callback, Exception exception) {
                this.callback = callback;
                this.exception = exception;
            }

            @Override
            public void run() {
                if (callback == null) return;
                callback.onRecordError(exception);
            }
        }

        private static class NotifyRecordStartRunnable implements Runnable {
            private final VideoRecordCallback callback;

            public NotifyRecordStartRunnable(VideoRecordCallback callback) {
                this.callback = callback;
            }

            @Override
            public void run() {
                if (callback == null) return;
                callback.onRecordStarted();
            }
        }
    }

    private void setCurrentMediaRecorder(MediaRecorder recorder) {
        mRecorder = recorder;
    }

    private int getVideoRotation() {
        if (mOpeningCameraId == -1) return 0;
        return CameraUtils.getPictureRotation(CameraUtils.getDisplayRotation(getContext()), mOpeningCameraId);
    }

    public static final class VideoRecordConfig {

        private CamcorderProfile profile;
        private int audioSource;
        private FileDescriptor outputFileDescriptor;
        private boolean readOnly;

        public String getOutputPath() {
            return outputPath;
        }

        public FileDescriptor getOutputFileDescriptor() {
            return outputFileDescriptor;
        }

        public int getAudioSource() {
            return audioSource;
        }

        public CamcorderProfile getProfile() {
            return profile;
        }

        private String outputPath;

        VideoRecordConfig(int cameraId) {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            setProfile(CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH));
        }

        void setReadOnly() {
            this.readOnly = true;
        }

        public void setOutputFileDescriptor(FileDescriptor outputFileDescriptor) {
            checkReadable();
            this.outputFileDescriptor = outputFileDescriptor;
        }

        private void checkReadable() {
            if (readOnly) throw new IllegalArgumentException("Config is read only");
        }

        public void setOutputPath(String outputPath) {
            checkReadable();
            this.outputPath = outputPath;
        }

        public void setAudioSource(int audioSource) {
            checkReadable();
            this.audioSource = audioSource;
        }

        public void setProfile(CamcorderProfile profile) {
            checkReadable();
            this.profile = profile;
        }

        void applyOutputFile(MediaRecorder recorder) {
            if (outputFileDescriptor != null) {
                recorder.setOutputFile(outputFileDescriptor);
            } else if (outputPath != null) {
                recorder.setOutputFile(outputPath);
            }
        }
    }

    public VideoRecordTransaction recordVideo(VideoRecordConfig config, VideoRecordCallback callback) {
        if (mRecorder != null) {
            throw new IllegalStateException();
        }
        config.setReadOnly();
        final MediaRecorder recorder = new MediaRecorder();
        setCurrentMediaRecorder(recorder);
        final Thread recordThread = new Thread(new RecordVideoRunnable(this, recorder, config, callback));
        recordThread.start();
        return new VideoRecordTransaction(this, config, callback);
    }

    public Camera.Size getPreviewSize() {
        final Camera camera = getOpeningCamera();
        if (camera == null) return null;
        return camera.getParameters().getPreviewSize();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int measuredWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int measuredHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
        final Camera camera = openCameraIfNeeded();
        final Camera.Parameters parameters = camera.getParameters();
        final int rotation = CameraUtils.getCameraRotation(CameraUtils.getDisplayRotation(getContext()), getOpeningCameraId());
        final List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        final Point previewSize = CameraUtils.getBestSize(previewSizes, measuredWidth, measuredHeight, rotation);
        parameters.setPreviewSize(previewSize.x, previewSize.y);
        camera.setDisplayOrientation(rotation);
        parameters.setRotation(rotation);
        mCameraRotation = rotation;
        measureChild(getChildAt(0), widthMeasureSpec, heightMeasureSpec);
    }

    public boolean getDisplayBounds(Rect bounds) {
        return mPreview.getDisplayBounds(bounds);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mPreview.layoutPreview(changed, l, t, r, b);
    }

    private void addViewInternal(View child) {
        super.addView(child);
    }

    private Preview createPreview() {
        return new TexturePreview(this);
    }

    public Camera getOpeningCamera() {
        return mOpeningCamera;
    }

    void releaseCamera() {

        final Camera camera = mOpeningCamera;
        if (camera == null) return;
        camera.release();
        mOpeningCamera = null;
    }

    public int getOpeningCameraId() {
        return mOpeningCameraId;
    }

    Camera openCameraIfNeeded() {
        if (mOpeningCamera != null) return mOpeningCamera;
        return openCameraSafely(mCameraId);
    }

    public void setCameraListener(Listener listener) {
        mListener = listener;
    }

    public static interface Listener {
        void onCameraInitialized(Camera camera);
    }

    public boolean isAutoFocusing() {
        return mAutoFocusing;
    }

    public boolean isAutoFocusSupported() {
        final Camera camera = getOpeningCamera();
        if (camera == null) return false;
        final Camera.Parameters parameters = camera.getParameters();
        return parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO);
    }


    public boolean touchFocus(MotionEvent event, Camera.AutoFocusCallback callback) {
        if (mAutoFocusing) return false;
        final Rect bounds = new Rect();
        final Camera camera = getOpeningCamera();
        final Camera.Size size = getPreviewSize();
        if (camera == null || !getDisplayBounds(bounds) || size == null) return false;
        final Camera.Parameters parameters = camera.getParameters();
        if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO))
            return false;
        if (event != null) {
            final int rotation = getCameraRotation();
            final boolean swap = rotation % 180 != 0;
            final int cameraWidth = swap ? size.height : size.width;
            final int cameraHeight = swap ? size.width : size.height;
            final int viewWidth = getWidth(), viewHeight = getHeight();
            final float xRatio = event.getX() / viewWidth;
            final float yRatio = event.getY() / viewHeight;
            final int touchW = Math.round(event.getTouchMajor() / 2 * bounds.width() / viewWidth);
            final int touchH = Math.round(event.getTouchMinor() / 2 * bounds.height() / viewHeight);
            final float pointLeft = bounds.left + bounds.width() * xRatio;
            final float pointTop = bounds.top + bounds.height() * yRatio;
            final Rect focusRect = new Rect();
            final int l = CameraUtils.clamp(Math.round((pointLeft - touchW / 2) / cameraWidth * 2000 - 1000), 1000, -1000);
            final int t = CameraUtils.clamp(Math.round((pointTop - touchH / 2) / cameraHeight * 2000 - 1000), 1000, -1000);
            final int r = CameraUtils.clamp(Math.round((pointLeft + touchW / 2) / cameraWidth * 2000 - 1000), 1000, -1000);
            final int b = CameraUtils.clamp(Math.round((pointTop + touchH / 2) / cameraHeight * 2000 - 1000), 1000, -1000);
            switch (rotation) {
                case 270: {
                    focusRect.set(-b, l, -t, r);
                    break;
                }
                case 90: {
                    focusRect.set(t, -r, b, -l);
                    break;
                }
                case 180: {
                    focusRect.set(-r, -b, -l, -t);
                    break;
                }
                default: {
                    focusRect.set(l, t, r, b);
                    break;
                }
            }
            final ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
            areas.add(new Camera.Area(focusRect, 1000));
            parameters.setFocusAreas(areas);
            parameters.setMeteringAreas(areas);
        } else {
            parameters.setFocusAreas(null);
            parameters.setMeteringAreas(null);
        }
        camera.setParameters(parameters);
        setAutoFocusing(true);
        camera.autoFocus(new InternalAutoFocusCallback(this, callback));
        return true;
    }

    private static class InternalAutoFocusCallback implements Camera.AutoFocusCallback {

        private final CameraView cameraView;
        private final Camera.AutoFocusCallback callback;

        InternalAutoFocusCallback(CameraView cameraView, Camera.AutoFocusCallback callback) {
            this.cameraView = cameraView;
            this.callback = callback;
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (callback != null) {
                callback.onAutoFocus(success, camera);
            }
            cameraView.setAutoFocusing(false);
        }
    }

    public void takePicture(final Camera.ShutterCallback shutter, final Camera.PictureCallback jpeg) {
        final Camera camera = getOpeningCamera();
        if (camera == null) return;
        camera.takePicture(shutter, null, new InternalPictureCallback(jpeg, mSingleShot));
    }

    public void setSingleShot(boolean singleShot) {
        mSingleShot = singleShot;
    }

    public boolean isSingleShot() {
        return mSingleShot;
    }

    public int getPictureRotation() {
        if (mOpeningCameraId == -1) return 0;
        return CameraUtils.getPictureRotation(CameraUtils.getDisplayRotation(getContext()), mOpeningCameraId);
    }

    private static class InternalPictureCallback implements Camera.PictureCallback {
        private final Camera.PictureCallback callback;
        private final boolean singleShot;

        InternalPictureCallback(Camera.PictureCallback callback, boolean singleShot) {
            this.callback = callback;
            this.singleShot = singleShot;
        }

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (callback != null) {
                callback.onPictureTaken(data, camera);
            }
            if (!singleShot) {
                camera.startPreview();
            }
        }
    }
}
