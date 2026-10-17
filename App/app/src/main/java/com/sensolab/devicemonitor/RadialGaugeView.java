package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;

/**
 * 240° arc gauge with the reading printed in the middle. Same contract as
 * {@link CompassView}: paints preallocated once, internal smoothing toward the target,
 * updated on the main thread only.
 */
public class RadialGaugeView extends View {

    private static final float START_ANGLE = 150f;
    private static final float SWEEP = 240f;
    private static final float SMOOTHING = 0.25f;

    private float min = 0f, max = 100f;
    private float warn = Float.NaN, hot = Float.NaN;
    private float target = Float.NaN;   // NaN = no reading
    private float shownFraction = 0f;
    private String valueText = "—", unitText = "";

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int colAccent, colOk, colWarn, colHot;

    public RadialGaugeView(Context ctx) { this(ctx, null); }
    public RadialGaugeView(Context ctx, @Nullable AttributeSet a) { this(ctx, a, 0); }
    public RadialGaugeView(Context ctx, @Nullable AttributeSet a, int s) {
        super(ctx, a, s);
        colAccent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        colOk   = ContextCompat.getColor(ctx, R.color.gauge_ok);
        colWarn = ContextCompat.getColor(ctx, R.color.gauge_warn);
        colHot  = ContextCompat.getColor(ctx, R.color.gauge_hot);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest));
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeCap(Paint.Cap.ROUND);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);
        valuePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    public void setRange(float min, float max) { this.min = min; this.max = max; invalidate(); }

    /** NaN disables a band. */
    public void setBands(float warn, float hot) { this.warn = warn; this.hot = hot; invalidate(); }

    public void setReading(float value, String valueText, String unitText) {
        this.target = value;
        this.valueText = valueText;
        this.unitText = unitText == null ? "" : unitText;
        setContentDescription(valueText + " " + this.unitText);
        postInvalidateOnAnimation();
    }

    public void clearReading(String placeholder) {
        this.target = Float.NaN;
        this.valueText = placeholder;
        this.unitText = "";
        setContentDescription(placeholder);
        postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int desiredH = (int) (w * 0.82f);
        int h = resolveSize(desiredH, hSpec);
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float stroke = Math.max(6f, Math.min(w, h) * 0.09f);
        trackPaint.setStrokeWidth(stroke);
        fillPaint.setStrokeWidth(stroke);
        float radius = Math.min(w / 2f, h / 1.64f) - stroke;
        float cx = w / 2f, cy = stroke + radius;
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        valuePaint.setTextSize(radius * 0.42f);
        unitPaint.setTextSize(radius * 0.2f);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float targetFraction = GaugeMath.fraction(target, min, max);
        shownFraction = GaugeMath.approach(shownFraction, targetFraction, SMOOTHING);

        canvas.drawArc(arcRect, START_ANGLE, SWEEP, false, trackPaint);
        if (!Float.isNaN(target) && shownFraction > 0f) {
            fillPaint.setColor(colorFor(target));
            canvas.drawArc(arcRect, START_ANGLE, SWEEP * shownFraction, false, fillPaint);
        }
        float cx = arcRect.centerX(), cy = arcRect.centerY();
        canvas.drawText(valueText, cx, cy + valuePaint.getTextSize() * 0.3f, valuePaint);
        if (!unitText.isEmpty()) {
            canvas.drawText(unitText, cx, cy + valuePaint.getTextSize() * 0.3f + unitPaint.getTextSize() * 1.3f, unitPaint);
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
