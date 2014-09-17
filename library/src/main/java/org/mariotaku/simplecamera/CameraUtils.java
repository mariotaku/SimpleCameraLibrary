package org.mariotaku.simplecamera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by mariotaku on 14-9-9.
 */
public final class CameraUtils {

    private static Comparator<Camera.Size> CAMERA_SIZE_COMPARATOR = new Comparator<Camera.Size>() {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return lhs.width * lhs.height - rhs.width * rhs.height;
        }
    };

    static int getCameraRotation(final int rotation, final int cameraId) {
        final Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            return (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            return (info.orientation - degrees + 360) % 360;
        }
    }

    public static Point getLargestSize(final List<Camera.Size> list) {
        final ArrayList<Camera.Size> sorted = new ArrayList<>(list);
        Collections.sort(sorted, CAMERA_SIZE_COMPARATOR);
        final Camera.Size size = sorted.get(sorted.size() - 1);
        return new Point(size.width, size.height);
    }

    public static Point getBestSize(final List<Camera.Size> list, final int width, final int height, int rotation) {
        if (list.isEmpty()) return null;
        final boolean swap = rotation % 180 != 0;
        final int requiredWidth = swap ? height : width, requiredHeight = swap ? width : height;
        final ArrayList<Camera.Size> sorted = new ArrayList<>(list);
        Collections.sort(sorted, CAMERA_SIZE_COMPARATOR);
        for (final Camera.Size size : sorted) {
            if (size.width >= requiredWidth && size.height >= requiredHeight)
                return new Point(size.width, size.height);
        }
        return null;
    }

    public static int clamp(final int num, final int max, final int min) {
        return Math.max(Math.min(num, max), min);
    }

    static void setCameraDisplayOrientation(final int rotation, final int cameraId, final Camera camera,
                                            final Camera.Parameters params) {
        int orientation = getCameraRotation(rotation, cameraId);
        camera.setDisplayOrientation(orientation);
        params.setRotation(orientation);
    }

    static int getDisplayRotation(Context context) {
        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        return display.getRotation();
    }

    public static CamcorderProfile getDefaultVideoProfile(int cameraId) {
        final int[] qualities = {CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P,
                CamcorderProfile.QUALITY_CIF, CamcorderProfile.QUALITY_LOW};
        for (int quality : qualities) {
            if (CamcorderProfile.hasProfile(cameraId, quality)) {
                return CamcorderProfile.get(cameraId, quality);
            }
        }
        return null;
    }

    public static CamcorderProfile getBestVideoProfile(int cameraId, int width, int height, int rotation) {
        final int[] qualities = {CamcorderProfile.QUALITY_LOW, CamcorderProfile.QUALITY_CIF,
                CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_HIGH};
        final boolean swap = rotation % 180 != 0;
        final int requiredWidth = swap ? height : width, requiredHeight = swap ? width : height;
        for (int quality : qualities) {
            if (CamcorderProfile.hasProfile(cameraId, quality)) {
                final CamcorderProfile profile = CamcorderProfile.get(cameraId, quality);
                if (profile.videoFrameWidth >= requiredWidth && profile.videoFrameHeight >= requiredHeight) {
                    return profile;
                }
            }
        }
        return null;
    }


    static int getPictureRotation(final int rotation, final int cameraId) {
        final Camera.CameraInfo info = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(cameraId, info);
        } catch (Exception e) {
        }
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            final int result = (info.orientation + degrees) % 360;
            return (540 - result) % 360; // compensate the mirror
        } else { // back-facing
            return (info.orientation - degrees + 360) % 360;
        }
    }

    static void scaleRect(RectF rectF, float scale) {
        scaleRect(rectF, scale, scale);
    }

    static void scaleRect(RectF rectF, float scaleX, float scaleY) {
        rectF.left *= scaleX;
        rectF.top *= scaleY;
        rectF.right *= scaleX;
        rectF.bottom *= scaleY;
    }
}
