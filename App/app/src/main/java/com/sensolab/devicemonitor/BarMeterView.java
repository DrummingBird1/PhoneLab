package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;

/**
 * Horizontal fill meter for bounded scalars (lux, hPa, %RH, °C, RAM %…). Same contract as
 * {@link CompassView}: preallocated paints, internal smoothing, main thread only.
 */
public class BarMeterView extends View {

    private static final float SMOOTHING = 0.25f;

    private float min = 0f, max = 100f;
    private float warn = Float.NaN, hot = Float.NaN;
    private float target = Float.NaN;
    private float shownFraction = 0f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int colAccent, colOk, colWarn, colHot;

    public BarMeterView(Context ctx) { this(ctx, null); }
    public BarMeterView(Context ctx, @Nullable AttributeSet a) { this(ctx, a, 0); }
    public BarMeterView(Context ctx, @Nullable AttributeSet a, int s) {
        super(ctx, a, s);
        colAccent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        colOk   = ContextCompat.getColor(ctx, R.color.gauge_ok);
        colWarn = ContextCompat.getColor(ctx, R.color.gauge_warn);
        colHot  = ContextCompat.getColor(ctx, R.color.gauge_hot);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest));
    }

    public void setRange(float min, float max) { this.min = min; this.max = max; invalidate(); }
    public void setBands(float warn, float hot) { this.warn = warn; this.hot = hot; invalidate(); }

    public void setValue(float value) {
        target = value;
        postInvalidateOnAnimation();
    }

    public void clearValue() {
        target = Float.NaN;
        postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int defaultH = (int) (10 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(MeasureSpec.getSize(wSpec), resolveSize(defaultH, hSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float r = h / 2f;
        float targetFraction = GaugeMath.fraction(target, min, max);
        shownFraction = GaugeMath.approach(shownFraction, targetFraction, SMOOTHING);

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, trackPaint);
        if (!Float.isNaN(target) && shownFraction > 0f) {
            fillPaint.setColor(colorFor(target));
            float fillW = Math.max(h, w * shownFraction);
            // RTL: grow from the start edge, which is the right side in Hebrew/Arabic.
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) rect.set(w - fillW, 0, w, h);
            else rect.set(0, 0, fillW, h);
            canvas.drawRoundRect(rect, r, r, fillPaint);
        }
        if (shownFraction != targetFraction) postInvalidateOnAnimation();
    }

    private int colorFor(float v) {
        switch (GaugeMath.band(v, warn, hot)) {
            case GaugeMath.BAND_OK:   return colOk;
            case GaugeMath.BAND_WARN: return colWarn;
            case GaugeMath.BAND_HOT:  return colHot;
            default:                  return colAccent;
        }
    }
}
