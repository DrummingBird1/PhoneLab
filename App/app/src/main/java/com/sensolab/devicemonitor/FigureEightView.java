package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;

/**
 * Animated figure-8 (lemniscate) showing the motion that recalibrates the magnetometer.
 * Animates only while attached and visible; stops when calibration is good.
 */
public class FigureEightView extends View {

    private static final long PERIOD_MS = 2400L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path track = new Path();
    private boolean animating = true;

    public FigureEightView(Context ctx) { this(ctx, null); }
    public FigureEightView(Context ctx, @Nullable AttributeSet a) {
        super(ctx, a);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(5f);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        dotPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setAnimating(boolean on) {
        animating = on;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        track.reset();
        for (int i = 0; i <= 100; i++) {
            float[] p = point(i / 100f, w, h);
            if (i == 0) track.moveTo(p[0], p[1]); else track.lineTo(p[0], p[1]);
        }
    }

    /** Lemniscate of Gerono, t in [0, 1). */
    private static float[] point(float t, float w, float h) {
        double a = t * 2 * Math.PI;
        float x = (float) Math.cos(a), y = (float) (Math.sin(a) * Math.cos(a));
        return new float[]{w / 2f + x * (w / 2f - 12f), h / 2f + y * (h - 24f)};
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(track, trackPaint);
        if (!animating) return;
        float t = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
        float[] p = point(t, getWidth(), getHeight());
        canvas.drawCircle(p[0], p[1], 10f, dotPaint);
        postInvalidateOnAnimation();
    }
}
