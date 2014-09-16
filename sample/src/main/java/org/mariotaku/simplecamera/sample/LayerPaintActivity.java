package org.mariotaku.simplecamera.sample;

import android.app.Activity;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import org.mariotaku.simplecamera.CameraView;

import java.util.Collections;
import java.util.List;


public class LayerPaintActivity extends Activity implements View.OnClickListener, CameraView.Listener {

    private CameraView mCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layer_paint);
        mCameraView.setCameraListener(this);
        findViewById(R.id.apply).setOnClickListener(this);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        mCameraView = (CameraView) findViewById(R.id.camera_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mCameraView.isCameraAvailable()) {
            mCameraView.openCamera(0);
        }
    }

    @Override
    public void onCameraInitialized(Camera camera) {
        final Camera.Parameters parameters = camera.getParameters();
        final List<int[]> fpsRanges = parameters.getSupportedPreviewFpsRange();
        Collections.sort(fpsRanges, Utils.FPS_RANGE_COMPARATOR);
        if (!fpsRanges.isEmpty()) {
            final int[] fpsRange = fpsRanges.get(0);
            parameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        }
        camera.setParameters(parameters);

    }

    @Override
    public void onCameraOpeningError(Exception e) {

    }


    @Override
    protected void onPause() {
        mCameraView.releaseCamera();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.apply: {
                final Paint paint = new Paint();
                final ColorMatrix matrix = new ColorMatrix();
                final SeekBar satBar = (SeekBar) findViewById(R.id.saturation);
                final SeekBar rSclBar = (SeekBar) findViewById(R.id.r_scale);
                final SeekBar gSclBar = (SeekBar) findViewById(R.id.g_scale);
                final SeekBar bSclBar = (SeekBar) findViewById(R.id.b_scale);
                final float rScale = rSclBar.getProgress() / 100f;
                final float gScale = gSclBar.getProgress() / 100f;
                final float bScale = bSclBar.getProgress() / 100f;
                final SeekBar rRotBar = (SeekBar) findViewById(R.id.r_rotate);
                final SeekBar gRotBar = (SeekBar) findViewById(R.id.g_rotate);
                final SeekBar bRotBar = (SeekBar) findViewById(R.id.b_rotate);
//                matrix.setSaturation(satBar.getProgress() / (float) satBar.getMax());
                matrix.setScale(rScale, gScale, bScale, 1);
//                matrix.setRotate(0, rRotBar.getProgress());
//                matrix.setRotate(1, gRotBar.getProgress());
//                matrix.setRotate(2, bRotBar.getProgress());
                paint.setColorFilter(new ColorMatrixColorFilter(matrix));
                mCameraView.getPreview().getView().setLayerType(View.LAYER_TYPE_HARDWARE, paint);
                break;
            }
        }
    }
}
