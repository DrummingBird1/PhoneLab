package com.sensolab.devicemonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;

/**
 * Touch-screen dead-zone test: the panel is split into a grid; every cell the finger
 * passes over (including historical points between frames) turns green. When every cell
 * has been touched the listener's {@code onComplete} fires.
 */
public class TouchGridView extends View {

    public interface Listener {
        void onProgress(int touched, int total);
        void onComplete();
    }

    private static final int COLUMNS = 8;

    private int rows = 14;
    private boolean[] touched = new boolean[COLUMNS * rows];
    private int touchedCount = 0;
    private boolean completed = false;
    @Nullable private Listener listener;

    private final Paint gridPaint = new Paint();
    private final Paint fillPaint = new Paint();

    public TouchGridView(Context ctx) { this(ctx, null); }
    public TouchGridView(Context ctx, @Nullable AttributeSet a) {
        super(ctx, a);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        fillPaint.setColor(ContextCompat.getColor(ctx, R.color.gauge_ok));
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0) return;
        // Roughly square cells whatever the aspect ratio.
        rows = Math.max(6, Math.round(COLUMNS * (float) h / w));
        touched = new boolean[COLUMNS * rows];
        touchedCount = 0;
        completed = false;
        report();
    }

    @SuppressLint("ClickableViewAccessibility") // a pure touch-hardware test has no click semantics
    @Override public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            for (int p = 0; p < ev.getPointerCount(); p++) {
                for (int h = 0; h < ev.getHistorySize(); h++) mark(ev.getHistoricalX(p, h), ev.getHistoricalY(p, h));
                mark(ev.getX(p), ev.getY(p));
            }
            invalidate();
            report();
        }
        return true;
    }

    private void mark(float x, float y) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x >= w || y >= h) return;
        int col = (int) (x * COLUMNS / w), row = (int) (y * rows / h);
        int i = row * COLUMNS + col;
        if (i >= 0 && i < touched.length && !touched[i]) {
            touched[i] = true;
            touchedCount++;
        }
    }

    private void report() {
        if (listener == null) return;
        listener.onProgress(touchedCount, touched.length);
        if (!completed && touchedCount == touched.length && touched.length > 0) {
            completed = true;
            listener.onComplete();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cw = getWidth() / (float) COLUMNS, ch = getHeight() / (float) rows;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                float l = c * cw, t = r * ch;
                if (touched[r * COLUMNS + c]) canvas.drawRect(l, t, l + cw, t + ch, fillPaint);
                canvas.drawRect(l, t, l + cw, t + ch, gridPaint);
            }
        }
    }
}
