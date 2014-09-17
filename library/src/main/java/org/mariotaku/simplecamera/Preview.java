package org.mariotaku.simplecamera;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.View;

/**
 * Created by mariotaku on 14-9-9.
 */
public interface Preview {
    View getView();

    void layoutPreview(boolean changed, int l, int t, int r, int b);

    boolean isAttachedToCameraView();

    void onPreReleaseCamera(Camera camera);

    void attachMediaRecorder(MediaRecorder recorder);

    void detachMediaRecorder(MediaRecorder recorder);
}
