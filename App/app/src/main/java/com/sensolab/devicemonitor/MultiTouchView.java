package com.sensolab.devicemonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;

/** Draws a numbered circle under every finger and reports the most simultaneous touches seen. */
public class MultiTouchView extends View {

    public interface Listener { void onTouches(int current, int max); }

    private static final int[] COLORS = {
            0xFF4285F4, 0xFFDB4437, 0xFFF4B400, 0xFF0F9D58, 0xFFAB47BC,
            0xFF00ACC1, 0xFFFF7043, 0xFF9E9D24, 0xFF5C6BC0, 0xFFEC407A};

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private MotionEvent last;
    private int max = 0;
    @Nullable private Listener listener;

    public MultiTouchView(Context ctx) { this(ctx, null); }
    public MultiTouchView(Context ctx, @Nullable AttributeSet a) {
        super(ctx, a);
        radius = 44 * getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                22, getResources().getDisplayMetrics()));
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    @SuppressLint("ClickableViewAccessibility") // hardware touch test, no click semantics
    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (last != null) last.recycle();
        int action = ev.getActionMasked();
        boolean ending = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        last = ending ? null : MotionEvent.obtain(ev);
        int current = ending ? 0 : ev.getPointerCount() - (action == MotionEvent.ACTION_POINTER_UP ? 1 : 0);
        max = Math.max(max, current);
        if (listener != null) listener.onTouches(current, max);
        invalidate();
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        MotionEvent ev = last;
        if (ev == null) return;
        boolean pointerUp = ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP;
        for (int i = 0; i < ev.getPointerCount(); i++) {
            if (pointerUp && i == ev.getActionIndex()) continue;
            int id = ev.getPointerId(i);
            circlePaint.setColor(COLORS[id % COLORS.length]);
            float x = ev.getX(i), y = ev.getY(i);
            canvas.drawCircle(x, y, radius, circlePaint);
            canvas.drawText(String.valueOf(id + 1), x, y + textPaint.getTextSize() / 3f, textPaint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (last != null) { last.recycle(); last = null; }
    }
}
