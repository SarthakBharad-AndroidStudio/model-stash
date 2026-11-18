package com.sarthak.modelstash.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * An image you can pinch to zoom (up to 5×), drag around while zoomed, and
 * double-tap to zoom in on a spot or back out. Used by the full-screen photo viewer.
 *
 * While zoomed in, the view keeps the finger to itself, so dragging moves the
 * photo instead of swiping to the next one; at normal size, a swipe goes to the
 * next photo as usual. A single tap is passed on as a click (the viewer uses it to
 * show or hide its buttons).
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float MAX_ZOOM = 5f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;

    /** Fits the photo in the view (like fitCenter). */
    private final Matrix base = new Matrix();
    /** Your zoom and pan, on top of base. */
    private final Matrix user = new Matrix();
    private final Matrix combined = new Matrix();
    private final RectF bounds = new RectF();
    private float zoom = 1f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    @Nullable
    private ValueAnimator animator;

    public ZoomImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                if (zoom <= 1.01f) {
                    return false;
                }
                user.postTranslate(-dx, -dy);
                keepInBounds();
                apply();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                animateZoomTo(zoom > 1.01f ? 1f : DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                performClick();
                return true;
            }
        });
    }

    /** Back to normal size (the viewer calls this when you swipe to another photo). */
    public void resetZoom() {
        if (user == null) {
            return; // called by ImageView's constructor, before our fields exist
        }
        if (animator != null) {
            animator.cancel();
        }
        user.reset();
        zoom = 1f;
        fitBase();
        apply();
    }

    public boolean isZoomed() {
        return zoom > 1.01f;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        resetZoom();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetZoom();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        // Zoomed in, or two fingers down: keep the swipe from turning the page.
        boolean keep = isZoomed() || event.getPointerCount() > 1 || scaleDetector.isInProgress();
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(keep);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ---- Matrix work ----

    private void fitBase() {
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        base.reset();
        if (d == null || vw == 0 || vh == 0 || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) {
            return;
        }
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        float s = Math.min(vw / dw, vh / dh);
        base.setScale(s, s);
        base.postTranslate((vw - dw * s) / 2f, (vh - dh * s) / 2f);
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        float target = Math.max(1f, Math.min(MAX_ZOOM, zoom * factor));
        float applied = target / zoom;
        zoom = target;
        user.postScale(applied, applied, focusX, focusY);
        keepInBounds();
        apply();
    }

    private void animateZoomTo(float target, float focusX, float focusY) {
        if (animator != null) {
            animator.cancel();
        }
        float start = zoom;
        animator = ValueAnimator.ofFloat(start, target);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> zoomBy((Float) a.getAnimatedValue() / zoom, focusX, focusY));
        animator.start();
    }

    /** Never shows empty space at an edge while there is photo to fill it; centres it when smaller than the view. */
    private void keepInBounds() {
        Drawable d = getDrawable();
        if (d == null) {
            return;
        }
        combined.set(base);
        combined.postConcat(user);
        bounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        combined.mapRect(bounds);
        float vw = getWidth();
        float vh = getHeight();
        float dx = 0;
        float dy = 0;
        if (bounds.width() <= vw) {
            dx = vw / 2f - bounds.centerX();
        } else if (bounds.left > 0) {
            dx = -bounds.left;
        } else if (bounds.right < vw) {
            dx = vw - bounds.right;
        }
        if (bounds.height() <= vh) {
            dy = vh / 2f - bounds.centerY();
        } else if (bounds.top > 0) {
            dy = -bounds.top;
        } else if (bounds.bottom < vh) {
            dy = vh - bounds.bottom;
        }
        user.postTranslate(dx, dy);
    }

    private void apply() {
        combined.set(base);
        combined.postConcat(user);
        setImageMatrix(combined);
    }
}
