package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

/**
 * Small reusable line-chart view — following the same custom-View pattern as
 * {@link CompassView}: Paint/Path objects preallocated in init() and reused across
 * onDraw() calls, no per-frame allocation. Call {@link #setValues(float[])} whenever
 * the data changes; empty/null data just draws a flat baseline.
 */
public class SparklineView extends View {

    private float[] values = new float[0];

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public SparklineView(Context ctx) { super(ctx); init(ctx); }
    public SparklineView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }
    public SparklineView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(ctx); }

    private void init(Context ctx) {
        int accent = ContextCompat.getColor(ctx, R.color.primary);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(accent);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(accent);
        fillPaint.setAlpha(40);
    }

    /** Oldest value first, newest last (drawn left → right). */
    public void setValues(float[] newValues) {
        this.values = (newValues != null) ? newValues : new float[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = 6f;
        if (values.length < 2) {
            // Not enough data for a line — draw a flat baseline so the view isn't blank.
            canvas.drawLine(pad, h - pad, w - pad, h - pad, linePaint);
            return;
        }

        float min = values[0], max = values[0];
        for (float v : values) { if (v < min) min = v; if (v > max) max = v; }
        float range = Math.max(max - min, 0.001f); // avoid divide-by-zero on a flat series

        linePath.reset();
        fillPath.reset();
        float stepX = (w - 2 * pad) / (values.length - 1);
        for (int i = 0; i < values.length; i++) {
            float x = pad + i * stepX;
            float y = pad + (1f - (values[i] - min) / range) * (h - 2 * pad);
            if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(x, h - pad); fillPath.lineTo(x, y); }
            else        { linePath.lineTo(x, y); fillPath.lineTo(x, y); }
        }
        fillPath.lineTo(pad + (values.length - 1) * stepX, h - pad);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}
