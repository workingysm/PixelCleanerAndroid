package com.openai.pixelcleaner;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float scale = 1f;
    private float fitScale = 1f;
    private float lastX, lastY;
    private boolean dragging;

    public ZoomImageView(Context context) { super(context); init(context); }
    public ZoomImageView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(matrix);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = scale * factor;
                float min = fitScale;
                float max = Math.max(8f, fitScale * 8f);
                if (next < min) factor = min / scale;
                if (next > max) factor = max / scale;
                scale *= factor;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (scale > fitScale * 1.2f) resetToFit();
                else zoomAt(e.getX(), e.getY(), fitScale * 2f);
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetToFit();
    }

    @Override public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::resetToFit);
    }

    public void resetToFit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() <= 0 || getHeight() <= 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        fitScale = Math.min(getWidth() / dw, getHeight() / dh);
        scale = fitScale;
        matrix.reset();
        float tx = (getWidth() - dw * fitScale) * 0.5f;
        float ty = (getHeight() - dh * fitScale) * 0.5f;
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate(tx, ty);
        setImageMatrix(matrix);
    }

    private void zoomAt(float x, float y, float targetAbsoluteScale) {
        float target = Math.max(fitScale, Math.min(Math.max(8f, fitScale * 8f), targetAbsoluteScale));
        float factor = target / scale;
        scale = target;
        matrix.postScale(factor, factor, x, y);
        setImageMatrix(matrix);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(); lastY = event.getY(); dragging = true; break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && scale > fitScale * 1.01f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    lastX = event.getX(); lastY = event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false; break;
        }
        return true;
    }
}
