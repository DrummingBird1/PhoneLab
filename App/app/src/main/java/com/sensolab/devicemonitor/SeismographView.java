package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;

/**
 * Scrolling waveform of a signal (newest sample on the right). Samples go into a fixed
 * ring buffer; the vertical scale auto-ranges to the recent peak so small vibrations are
 * still visible. Preallocated paint/path, main thread only.
 */
public class SeismographView extends View {

    private static final int CAPACITY = 400;
    private final float[] ring = new float[CAPACITY];
    private int count = 0, head = 0;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public SeismographView(Context ctx) { this(ctx, null); }
    public SeismographView(Context ctx, @Nullable AttributeSet a) {
        super(ctx, a);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        axisPaint.setStrokeWidth(2f);
        axisPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        setContentDescription(ctx.getString(R.string.tool_vibration));
    }

    public void push(float sample) {
        ring[head] = sample;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public void clear() {
        count = 0;
        head = 0;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), mid = h / 2f;
        canvas.drawLine(0, mid, w, mid, axisPaint);
        if (count < 2) return;
        float peak = 0.05f;
        int start = (count < CAPACITY) ? 0 : head;
        for (int i = 0; i < count; i++) peak = Math.max(peak, Math.abs(ring[(start + i) % CAPACITY]));
        float scale = (h / 2f - 4f) / peak;
        float step = w / (CAPACITY - 1);
        float x0 = w - (count - 1) * step;
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = x0 + i * step;
            float y = mid - ring[(start + i) % CAPACITY] * scale;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);
    }
}
