package org.mariotaku.simplecamera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
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
        mCameraId = -1;
    }

    private void initPreview() {
        if (getChildCount() > 0) throw new IllegalStateException("Preview has already initialized");
        mPreview = createPreview();
        addViewInternal(mPreview.getView());
    }

    private void restartPreview() {
        removeAllViews();
        initPreview();
    }

    public void openCamera(int cameraId) {
        if (mCameraId == cameraId) return;
        mCameraId = cameraId;
        restartPreview();
    }

    private Camera openCameraSafely(final int cameraId) {
        final Camera oldCamera = mOpeningCamera;
        if (oldCamera != null) {
            if (mOpeningCameraId == cameraId) return oldCamera;
            oldCamera.release();
            mOpeningCamera = null;
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
            if (mListener != null) {
                mListener.onCameraOpeningError(e);
            }
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
                if (config.maxDuration != 0) {
                    recorder.setMaxDuration(config.maxDuration);
                }
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
        private int maxDuration;

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

        public int getMaxDuration() {
            return maxDuration;
        }

        public void setMaxDuration(int maxDuration) {
            checkReadable();

            this.maxDuration = maxDuration;
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
        if (camera != null && !isInEditMode()) {
            camera.stopPreview();
            final Camera.Parameters parameters = camera.getParameters();
            final int rotation = CameraUtils.getCameraRotation(CameraUtils.getDisplayRotation(getContext()), getOpeningCameraId());
            final List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
            final Point overrideMeasureSize = getOverrideMeasureSize();
            final Point previewSize;
            if (overrideMeasureSize != null) {
                previewSize = CameraUtils.getBestSize(previewSizes, overrideMeasureSize.x,
                        overrideMeasureSize.y, rotation);
            } else {
                previewSize = CameraUtils.getBestSize(previewSizes, measuredWidth, measuredHeight,
                        rotation);
            }
            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.x, previewSize.y);
            } else {
                final Point largest = CameraUtils.getLargestSize(previewSizes);
                parameters.setPreviewSize(largest.x, largest.y);
            }
            camera.setDisplayOrientation(rotation);
//            parameters.setRotation(rotation);
            camera.setParameters(parameters);
            camera.startPreview();
            mCameraRotation = rotation;
        }
        final View child = getChildAt(0);
        if (child == null) return;
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
    }

    protected Point getOverrideMeasureSize() {
        return null;
    }

    public boolean getCameraBounds(RectF bounds, RectF displayBounds) {
        final Camera camera = getOpeningCamera();
        final int width = getWidth(), height = getHeight();
        if (camera == null || width == 0 || height == 0) return false;
        final Camera.Size size = camera.getParameters().getPreviewSize();
        if (size == null) return false;
        if (displayBounds != null) {
            final int rotation = getCameraRotation();
            final int cameraWidth = size.width, cameraHeight = size.height;
            final float viewRatio = rotation % 180 == 0 ? (float) width / height : (float) height / width;
            final float cameraRatio = (float) cameraWidth / cameraHeight;
            if (viewRatio > cameraRatio) {
                // fit width
                final int displayHeight = Math.round(cameraWidth / viewRatio);
                final int top = (cameraHeight - displayHeight) / 2;
                displayBounds.set(0, top, cameraWidth, top + displayHeight);
            } else {
                // fit height
                final int displayWidth = Math.round(cameraHeight * viewRatio);
                final int left = (cameraWidth - displayWidth) / 2;
                displayBounds.set(left, 0, left + displayWidth, cameraHeight);
            }
        }
        if (bounds != null) {
            bounds.set(0, 0, size.width, size.height);
        }
        return true;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mPreview == null || !mPreview.isAttachedToCameraView()) return;
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

    public void releaseCamera() {
        final Camera camera = mOpeningCamera;
        if (camera == null) return;
        camera.release();
        mOpeningCamera = null;
    }

    public boolean isCameraAvailable() {
        return mOpeningCamera != null;
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

        void onCameraOpeningError(Exception e);
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
        final RectF cameraBounds = new RectF(), cameraDisplayBounds = new RectF();
        final Camera camera = getOpeningCamera();
        final Camera.Size size = getPreviewSize();
        if (camera == null || !getCameraBounds(cameraBounds, cameraDisplayBounds) || size == null)
            return false;
        final Camera.Parameters parameters = camera.getParameters();
        if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO))
            return false;
        final int maxFocusAreas = parameters.getMaxNumFocusAreas(), maxMeteringAreas = parameters.getMaxNumMeteringAreas();
        final ArrayList<Camera.Area> areas = new ArrayList<Camera.Area>();
        if (event != null && maxFocusAreas > 0 && maxMeteringAreas > 0) {
            final int viewWidth = getWidth(), viewHeight = getHeight();
            final int rotation = 360 - getCameraRotation();
            final Matrix matrix = new Matrix();
            final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            final RectF touchRect = new RectF(0, 0, event.getTouchMajor() / 2, event.getTouchMinor() / 2);
            touchRect.offsetTo(event.getX() - touchRect.centerX(), event.getY() - touchRect.centerY());
            matrix.setRotate(rotation);
            matrix.mapRect(viewRect);
            final float offsetX = -viewRect.left, offsetY = -viewRect.top;
            viewRect.offset(offsetX, offsetY);
            matrix.reset();
            matrix.setRotate(rotation);
            matrix.mapRect(touchRect);
            touchRect.offset(offsetX, offsetY);


            final float sizeRatioX = viewRect.width() / cameraDisplayBounds.width();
            final float sizeRatioY = viewRect.height() / cameraDisplayBounds.height();

            CameraUtils.scaleRect(cameraDisplayBounds, sizeRatioX, sizeRatioY);
            CameraUtils.scaleRect(cameraBounds, sizeRatioX, sizeRatioY);

            touchRect.offset(cameraDisplayBounds.left - cameraBounds.left,
                    cameraDisplayBounds.top - cameraBounds.top);

            final float areaRatioX = 2000f / cameraBounds.width();
            final float areaRatioY = 2000f / cameraBounds.height();

            CameraUtils.scaleRect(touchRect, areaRatioX, areaRatioY);


            touchRect.offset(-1000, -1000);
            final Rect focusRect = new Rect();
            focusRect.left = CameraUtils.clamp(Math.round(touchRect.left), 1000, -1000);
            focusRect.top = CameraUtils.clamp(Math.round(touchRect.top), 1000, -1000);
            focusRect.right = CameraUtils.clamp(Math.round(touchRect.right), 1000, -1000);
            focusRect.bottom = CameraUtils.clamp(Math.round(touchRect.bottom), 1000, -1000);

            if (focusRect.left < focusRect.right && focusRect.top < focusRect.bottom) {
                areas.add(new Camera.Area(focusRect, 1000));
            } else {
                Log.w(LOGTAG, String.format("Invalid focus area: %s", focusRect));
            }
        }
        if (areas.isEmpty()) {
            parameters.setFocusAreas(null);
            parameters.setMeteringAreas(null);
        } else {
            parameters.setFocusAreas(areas);
            parameters.setMeteringAreas(areas);
        }
        try {
            camera.setParameters(parameters);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Error while auto-focus, areas: %s", areas), e);
        }
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
