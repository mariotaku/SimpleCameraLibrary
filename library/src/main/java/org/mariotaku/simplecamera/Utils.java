package org.mariotaku.simplecamera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.Closeable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by mariotaku on 14-9-9.
 */
class Utils {

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

    static Point getMaxSize(final List<Camera.Size> list, final int width, final int height, int rotation) {
        if (list.isEmpty()) return null;
        final boolean swap = rotation % 180 != 0;
        final int requiredWidth = swap ? height : width, requiredHeight = swap ? width : height;

        Collections.sort(list, CAMERA_SIZE_COMPARATOR);
        for (final Camera.Size size : list) {
            if (size.width >= requiredWidth && size.height >= requiredHeight)
                return new Point(size.width, size.height);
        }

        final Camera.Size max = list.get(list.size() - 1);
        return new Point(max.width, max.height);
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

    public static void closeSilently(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            //Ignore
        }
    }


    static int getPhotoRotation(final int rotation, final int cameraId) {
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

}
