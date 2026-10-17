package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;

/**
 * Round spirit level. {@link #setTilt(float, float)} takes roll / pitch in degrees; the
 * bubble moves opposite to the tilt (like a real vial) and turns green within ±1°.
 * Same contract as {@link CompassView}: preallocated paints, smoothing, main thread.
 */
public class BubbleLevelView extends View {

    private static final float MAX_DEG = 20f;   // tilt that puts the bubble at the rim
    private static final float LEVEL_DEG = 1f;  // "level" tolerance
    private static final float SMOOTHING = 0.2f;

    private float targetX, targetY, shownX, shownY;
    private boolean level;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int colAccent, colLevel;

    public BubbleLevelView(Context ctx) { this(ctx, null); }
    public BubbleLevelView(Context ctx, @Nullable AttributeSet a) {
        super(ctx, a);
        colAccent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        colLevel = ContextCompat.getColor(ctx, R.color.gauge_ok);
        fillPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);
        ringPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline));
        crossPaint.setStrokeWidth(2f);
        crossPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        setContentDescription(ctx.getString(R.string.tool_level));
    }

    /** roll: positive = right edge down; pitch: positive = top edge down (degrees). */
    public void setTilt(float rollDeg, float pitchDeg) {
        targetX = clamp(-rollDeg / MAX_DEG);
        targetY = clamp(pitchDeg / MAX_DEG);
        level = Math.abs(rollDeg) <= LEVEL_DEG && Math.abs(pitchDeg) <= LEVEL_DEG;
        postInvalidateOnAnimation();
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int size = Math.min(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED) size = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 4f;
        float bubbleR = r * 0.16f;
        float travel = r - bubbleR - 4f;

        shownX = GaugeMath.approach(shownX, targetX, SMOOTHING);
        shownY = GaugeMath.approach(shownY, targetY, SMOOTHING);
        // Keep the bubble inside the circle even at diagonal extremes.
        float bx = shownX, by = shownY;
        float len = (float) Math.hypot(bx, by);
        if (len > 1f) { bx /= len; by /= len; }

        canvas.drawCircle(cx, cy, r, fillPaint);
        canvas.drawCircle(cx, cy, r, ringPaint);
        canvas.drawCircle(cx, cy, bubbleR * 1.5f, crossPaint);
        canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint);
        canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint);
        bubblePaint.setColor(level ? colLevel : colAccent);
        canvas.drawCircle(cx + bx * travel, cy + by * travel, bubbleR, bubblePaint);

        if (shownX != targetX || shownY != targetY) postInvalidateOnAnimation();
    }
}
