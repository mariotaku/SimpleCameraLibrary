package org.mariotaku.simplecamera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
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

    static final String LOGTAG = "CameraView";

    private Preview mPreview;
    private Camera mOpeningCamera;
    private int mOpeningCameraId;
    private int mRequiredCameraId;
    private CameraListener mListener;
    private int mCameraRotation;
    private boolean mSingleShot;
    private MediaRecorder mRecorder;
    private boolean mVideoRecordStarted;
    private boolean mAutoFocusing;
    private boolean mCameraPreviewStarted;
    private Size mPictureSizeBackup;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
//        setClipChildren(false);
        mOpeningCameraId = -1;
        mRequiredCameraId = -1;
    }

    public Preview getPreview() {
        return mPreview;
    }

    public int getDefaultCameraId() {
        return 0;
    }

    /**
     * Pictures taken by this library will remain original, so you need to rotate or add EXIF tag by
     * yourself.
     * <br>
     * This method returns the rotate angle you needed for post-processing.
     *
     * @return The rotate angle for post-processing of taken picture
     */
    public int getCameraRotation() {
        return mCameraRotation;
    }

    private void initPreview() {
        if (getChildCount() > 0) throw new IllegalStateException("Preview has already initialized");
        mPreview = createPreview();
        addViewInternal(mPreview.getView());
    }

    private void restartPreview() {
        final int requiredCameraId = mRequiredCameraId;
        removeAllViews();
        initPreview();
        mRequiredCameraId = requiredCameraId;
    }

    /**
     * Opens camera with specified ID and starts preview, if the Camera with your specified ID has
     * already opened, your method call will be ignored.
     *
     * @param cameraId ID of the Camera
     * @see android.hardware.Camera#open(int)
     */
    public void openCamera(int cameraId) {
        if (mOpeningCameraId == cameraId) return;
        mRequiredCameraId = cameraId;
        restartPreview();
    }

    private Camera openCameraSafely(final int cameraId) {
        if (cameraId < 0) throw new IllegalStateException();
        final Camera oldCamera = mOpeningCamera;
        final int requiredCameraId = mRequiredCameraId;
        if (oldCamera != null) {
            if (mOpeningCameraId == cameraId) return oldCamera;
            releaseCamera();
        }
        mRequiredCameraId = requiredCameraId;
        try {
            final Camera camera = Camera.open(cameraId);
            mOpeningCameraId = cameraId;
            mOpeningCamera = camera;
            if (mListener != null) {
                mListener.onCameraInitialized(camera);
            }
            camera.setErrorCallback(mListener);
            return camera;
        } catch (Exception e) {
            Log.e(LOGTAG, String.format("Error opening camera %d", cameraId), e);
            if (mListener != null) {
                mListener.onCameraOpeningError(e);
            }
        }
        mOpeningCamera = null;
        mOpeningCameraId = -1;
        return null;
    }

    /**
     * Creates a new {@link VideoRecordConfig}
     *
     * @return A new {@link VideoRecordConfig} for
     * {@link #recordVideo(VideoRecordConfig, VideoRecordCallback)}
     */
    public VideoRecordConfig newVideoRecordConfig() {
        if (mOpeningCameraId == -1) return null;
        return new VideoRecordConfig(mOpeningCameraId);
    }

    private MediaRecorder getCurrentMediaRecorder() {
        return mRecorder;
    }

    private void setCurrentMediaRecorder(MediaRecorder recorder) {
        mRecorder = recorder;
    }

    private int getVideoRotation() {
        if (mOpeningCameraId == -1) return 0;
        return CameraUtils.getPictureRotation(CameraUtils.getDisplayRotation(getContext()), mOpeningCameraId);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public VideoRecordTransaction recordVideo(VideoRecordConfig config, VideoRecordCallback callback) {
        if (mRecorder != null) throw new IllegalStateException();
        final Camera camera = getOpeningCamera();
        if (camera == null) throw new IllegalStateException();
        config.setReadOnly();
        final MediaRecorder recorder = new MediaRecorder();
        setCurrentMediaRecorder(recorder);
        if (shouldSetSizeForRecorder()) {
            setCameraPreviewStarted(false);
            camera.stopPreview();
            final CamcorderProfile profile = config.profile;
            final Camera.Parameters parameters = camera.getParameters();
            mPictureSizeBackup = parameters.getPictureSize();
            parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
            parameters.setPictureSize(profile.videoFrameWidth, profile.videoFrameHeight);
            dispatchSetParameterBeforeStartPreview(camera, parameters);
            camera.setParameters(parameters);
            camera.startPreview();
            setCameraPreviewStarted(true);
            notifyPreviewSizeChanged(0, 0);
        }
        final Thread recordThread = new Thread(new RecordVideoRunnable(this, recorder, config, callback));
        recordThread.start();
//        recordThread.run();
        return new VideoRecordTransaction(this, config, callback);
    }

    private void dispatchSetParameterBeforeStartPreview(Camera camera, Camera.Parameters parameters) {
        if (mListener != null) {
            mListener.setParameterBeforeStartPreview(camera, parameters);
        }
    }

    public Camera.Size getPreviewSize() {
        final Camera camera = getOpeningCamera();
        if (camera == null) return null;
        return camera.getParameters().getPreviewSize();
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int measuredWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int measuredHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
        final Preview preview = getPreview();
        if (preview == null || !preview.isAddedToCameraView()) return;
        final Camera camera = openCameraIfNeeded();
        if (camera != null && !isInEditMode()) {
            if (mCameraPreviewStarted && preview.isAttachedToCamera()) {
//                setCameraPreviewStarted(false);
//                camera.stopPreview();
            }
            final int rotation = CameraUtils.getCameraRotation(CameraUtils.getDisplayRotation(getContext()), getOpeningCameraId());
            camera.setDisplayOrientation(rotation);
            final Camera.Parameters parameters = camera.getParameters();
            final Point previewSize = getPreviewSize(parameters, measuredWidth, measuredHeight, rotation);
            parameters.setPreviewSize(previewSize.x, previewSize.y);
            dispatchSetParameterBeforeStartPreview(camera, parameters);
            camera.setParameters(parameters);
            if (preview.isAttachedToCamera()) {
//                camera.startPreview();
            }
            setCameraPreviewStarted(true);
            mCameraRotation = rotation;
        }
        final View child = getChildAt(0);
        if (child == null) return;
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
    }

    private Point getPreviewSize(Camera.Parameters parameters, int width, int height, int rotation) {
        final List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        final Point overrideMeasureSize = getOverrideMeasureSize();
        final Point previewSize;
        if (overrideMeasureSize != null) {
            previewSize = CameraUtils.getBestSize(previewSizes, overrideMeasureSize.x,
                    overrideMeasureSize.y, rotation);
        } else {
            previewSize = CameraUtils.getBestSize(previewSizes, width, height,
                    rotation);
        }
        if (previewSize != null) return previewSize;
        return CameraUtils.getLargestSize(previewSizes);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final Preview preview = getPreview();
        if (preview == null || !preview.isAddedToCameraView()) return;
        preview.layoutPreview(changed, l, t, r, b);
    }

    private void addViewInternal(View child) {
        super.addView(child);
    }

    protected Preview createPreview() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            return new SurfacePreview(this);
        return new TexturePreview(this);
    }

    public Camera getOpeningCamera() {
        return mOpeningCamera;
    }

    public void releaseCamera() {
        final Camera camera = mOpeningCamera;
        mOpeningCameraId = -1;
        if (camera == null) return;
        final Preview preview = getPreview();
        if (preview != null) {
            preview.onPreReleaseCamera(camera);
        }
        camera.release();
        mOpeningCamera = null;
        mPreview = null;
        mRequiredCameraId = -1;
    }

    public boolean isCameraAvailable() {
        return mOpeningCamera != null;
    }

    public int getOpeningCameraId() {
        return mOpeningCameraId;
    }

    Camera openCameraIfNeeded() {
        if (mOpeningCamera != null) return mOpeningCamera;
        if (mRequiredCameraId == -1) return null;
        return openCameraSafely(mRequiredCameraId);
    }

    public void setCameraListener(CameraListener listener) {
        mListener = listener;
        final Camera camera = getOpeningCamera();
        if (camera != null) {
            camera.setErrorCallback(listener);
        }
    }

    public boolean isAutoFocusing() {
        return mAutoFocusing;
    }

    public void setAutoFocusing(boolean autoFocusing) {
        this.mAutoFocusing = autoFocusing;
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

    /**
     * Convenience call of <code>getOpeningCamera().takePicture(shutter, null, jpeg)</code>
     *
     * @param shutter the callback for image capture moment, or null
     * @param jpeg    the callback for JPEG image data, or null
     */
    public void takePicture(final Camera.ShutterCallback shutter, final Camera.PictureCallback jpeg) {
        final Camera camera = getOpeningCamera();
        if (camera == null) return;
        setCameraPreviewStarted(false);
        camera.takePicture(shutter, null, new InternalPictureCallback(this, jpeg, mSingleShot));
    }

    /**
     * @return Whether CameraView is in single shot mode
     */
    public boolean isSingleShot() {
        return mSingleShot;
    }

    /**
     * Set single shot mode, if set to true, camera preview will stop.
     *
     * @param singleShot Preview will stop after picture taken if true
     */
    public void setSingleShot(boolean singleShot) {
        mSingleShot = singleShot;
    }

    public int getPictureRotation() {
        if (mOpeningCameraId == -1) return 0;
        return CameraUtils.getPictureRotation(CameraUtils.getDisplayRotation(getContext()), mOpeningCameraId);
    }

    private void notifyPreviewSizeChanged(int width, int height) {
        final Preview preview = getPreview();
        if (preview == null) return;
        preview.notifyPreviewSizeChanged(width, height);
    }

    private boolean shouldSetSizeForRecorder() {
        final Preview preview = getPreview();
        return preview != null && preview.shouldSetSizeForRecorder();
    }

    private void detachMediaRecorder(MediaRecorder recorder) {
        final Preview preview = getPreview();
        if (preview != null) {
            preview.detachMediaRecorder(recorder);
        }
    }

    private void attachMediaRecorder(MediaRecorder recorder) {
        final Preview preview = getPreview();
        if (preview != null) {
            preview.attachMediaRecorder(recorder);
        }
    }

    protected void setCameraPreviewStarted(boolean cameraPreviewStarted) {
        mCameraPreviewStarted = cameraPreviewStarted;
    }

    public interface VideoRecordCallback extends MediaRecorder.OnInfoListener {
        void onRecordStarted();

        void onRecordError(Exception e);

        void onRecordStopped();
    }

    public static interface CameraListener extends Camera.ErrorCallback {
        void onCameraInitialized(Camera camera);

        void onCameraOpeningError(Exception e);

        void setParameterBeforeStartPreview(Camera camera, Camera.Parameters parameters);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static final class VideoRecordTransaction {

        private final CameraView cameraView;
        private final VideoRecordConfig config;
        private final VideoRecordCallback callback;
        private Object extra;

        VideoRecordTransaction(CameraView cameraView, VideoRecordConfig config, VideoRecordCallback callback) {
            this.cameraView = cameraView;
            this.config = config;
            this.callback = callback;
        }

        public Object getExtra() {
            return extra;
        }

        public void setExtra(Object extra) {
            this.extra = extra;
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
                cameraView.detachMediaRecorder(recorder);
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
                    Log.w(LOGTAG, e);
                }
                cameraView.setCameraPreviewStarted(false);
                camera.stopPreview();
                final Camera.Parameters parameters = camera.getParameters();
                if (cameraView.shouldSetSizeForRecorder()) {
                    final int width = cameraView.getWidth(), height = cameraView.getHeight();
                    final int rotation = cameraView.getCameraRotation();
                    final Point previewSize = cameraView.getPreviewSize(parameters, width, height,
                            rotation);
                    parameters.setPreviewSize(previewSize.x, previewSize.y);
                }
                cameraView.restorePictureSize(parameters);
                cameraView.dispatchSetParameterBeforeStartPreview(camera, parameters);
                camera.setParameters(parameters);
                camera.startPreview();
                cameraView.setCameraPreviewStarted(true);
                if (cameraView.shouldSetSizeForRecorder()) {
                    cameraView.notifyPreviewSizeChanged(0, 0);
                }
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

    private void restorePictureSize(Parameters parameters) {
        final Camera.Size size = mPictureSizeBackup;
        if (size == null || parameters == null) return;
        parameters.setPictureSize(size.width, size.height);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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

        @Override
        public void run() {
            try {
                final Camera camera = cameraView.getOpeningCamera();
                if (camera == null) return;
                camera.unlock();
                recorder.setCamera(camera);
                recorder.setOnInfoListener(callback);
                recorder.setAudioSource(config.audioSource);
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                recorder.setProfile(config.profile);
                recorder.setOrientationHint(cameraView.getVideoRotation());
                if (config.maxDuration != 0) {
                    recorder.setMaxDuration(config.maxDuration);
                }
                config.applyOutputFile(recorder);

                cameraView.attachMediaRecorder(recorder);
                recorder.prepare();
                recorder.start();
                cameraView.mVideoRecordStarted = true;
                cameraView.post(new NotifyRecordStartRunnable(callback));
            } catch (Exception e) {
                cameraView.mVideoRecordStarted = false;
                cameraView.detachMediaRecorder(recorder);
                recorder.reset();
                recorder.release();
                final Camera camera = cameraView.getOpeningCamera();
                if (camera != null) {
                    camera.lock();
                }
                cameraView.setCurrentMediaRecorder(null);
                cameraView.post(new NotifyRecordFailedRunnable(callback, e));
            }
        }


    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static final class VideoRecordConfig {

        private CamcorderProfile profile;
        private int audioSource;
        private FileDescriptor outputFileDescriptor;
        private boolean readOnly;
        private int maxDuration;
        private String outputPath;

        VideoRecordConfig(int cameraId) {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            setProfile(CameraUtils.getDefaultVideoProfile(cameraId));
        }

        public String getOutputPath() {
            return outputPath;
        }

        /**
         * Sets the path of the output file to be produced.
         *
         * @param outputPath The pathname to use.
         * @see android.media.MediaRecorder#setOutputFile(java.lang.String)
         */
        public void setOutputPath(String outputPath) {
            checkReadable();
            this.outputPath = outputPath;
        }

        /**
         * Returns the file descriptor of the file to be written.
         *
         * @return The file descriptor to be written into.
         */
        public FileDescriptor getOutputFileDescriptor() {
            return outputFileDescriptor;
        }

        /**
         * Pass in the file descriptor of the file to be written.
         *
         * @param outputFileDescriptor an open file descriptor to be written into.
         * @see android.media.MediaRecorder#setOutputFile(java.io.FileDescriptor)
         */
        public void setOutputFileDescriptor(FileDescriptor outputFileDescriptor) {
            checkReadable();
            this.outputFileDescriptor = outputFileDescriptor;
        }

        /**
         * Gets the audio source to be used for recording.
         *
         * @return The audio source to use
         * @see android.media.MediaRecorder#setAudioSource(int)
         */
        public int getAudioSource() {
            return audioSource;
        }

        /**
         * Sets the audio source to be used for recording.
         *
         * @param audioSource the audio source to use
         * @see android.media.MediaRecorder#setAudioSource(int)
         */
        public void setAudioSource(int audioSource) {
            checkReadable();
            this.audioSource = audioSource;
        }

        /**
         * Gets the {@link android.media.CamcorderProfile} object for recording.
         *
         * @return {@link android.media.CamcorderProfile} object for recording.
         */
        public CamcorderProfile getProfile() {
            return profile;
        }

        /**
         * Uses the settings from a CamcorderProfile object for recording.
         * If a time lapse CamcorderProfile is used, audio related source or recording
         * parameters are ignored.
         *
         * @param profile the CamcorderProfile to use
         * @see android.media.MediaRecorder#setProfile(android.media.CamcorderProfile)
         * @see android.media.CamcorderProfile
         */
        public void setProfile(CamcorderProfile profile) {
            checkReadable();
            this.profile = profile;
        }

        /**
         * Make this config read-only, any attempt of changing this config after calling this method
         * will throw an {@link IllegalArgumentException}
         */
        void setReadOnly() {
            this.readOnly = true;
        }

        public int getMaxDuration() {
            return maxDuration;
        }


        /**
         * Sets the maximum duration (in ms) of the recording session.
         * After recording reaches the specified duration, a notification
         * will be sent to the {@link android.media.MediaRecorder.OnInfoListener}
         * with a "what" code of {@link android.media.MediaRecorder#MEDIA_RECORDER_INFO_MAX_DURATION_REACHED}
         * and recording will be stopped. Stopping happens asynchronously, there
         * is no guarantee that the recorder will have stopped by the time the
         * listener is notified.
         *
         * @param maxDuration the maximum duration in ms (if zero or negative, disables the duration limit)
         * @see android.media.MediaRecorder#setMaxDuration(int)
         */
        public void setMaxDuration(int maxDuration) {
            checkReadable();
            this.maxDuration = maxDuration;
        }

        private void checkReadable() {
            if (readOnly) throw new IllegalArgumentException("Config is read only");
        }

        void applyOutputFile(MediaRecorder recorder) {
            if (outputFileDescriptor != null) {
                recorder.setOutputFile(outputFileDescriptor);
            } else if (outputPath != null) {
                recorder.setOutputFile(outputPath);
            }
        }
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

    private static class InternalPictureCallback implements Camera.PictureCallback {
        private final CameraView cameraView;
        private final Camera.PictureCallback callback;
        private final boolean singleShot;

        InternalPictureCallback(CameraView cameraView, Camera.PictureCallback callback, boolean singleShot) {
            this.cameraView = cameraView;
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
                cameraView.setCameraPreviewStarted(true);
            }
        }
    }
}