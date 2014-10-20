package org.mariotaku.simplecamera;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.View;

/**
 * Created by mariotaku on 14-9-9.
 */
public interface Preview {
    /**
     * @return View for camera preview
     */
    View getView();

    void layoutPreview(boolean changed, int l, int t, int r, int b);

    boolean isAddedToCameraView();

    boolean isAttachedToCamera();

    /**
     * Called before releasing {@link android.hardware.Camera}
     * @param camera
     */
    void onPreReleaseCamera(Camera camera);

    /**
     * Called when record started, you may need to attach {@link android.media.MediaRecorder} to your preview.
     *
     * @param recorder {@link android.media.MediaRecorder} to attach
     */
    void attachMediaRecorder(MediaRecorder recorder);

    /**
     * Called when record stopped, you may need to detach it from your preview and re-attach {@link android.hardware.Camera} to your preview.
     *
     * @param recorder {@link android.media.MediaRecorder} to detach
     */
    void detachMediaRecorder(MediaRecorder recorder);

    /**
     * When this method returns true, preview size will set to video size during recording.
     * On most devices under JellyBean, this method should usually returns true.
     */
    boolean shouldSetSizeForRecorder();

    void notifyPreviewSizeChanged(int width, int height);
}
