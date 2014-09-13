package org.mariotaku.simplecamera;

import android.graphics.Rect;
import android.view.View;

/**
 * Created by mariotaku on 14-9-9.
 */
public interface Preview {
    View getView();

    void layoutPreview(boolean changed, int l, int t, int r, int b);

    boolean isAttachedToCameraView();

    boolean getDisplayBounds(Rect bounds);
}
