package com.sensolab.devicemonitor;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

/**
 * Live compass rose with rotating needle.
 * Call setBearing(degrees) to update.
 */
public class CompassView extends View {

    private float bearing = 0f;
    private float displayBearing = 0f; // smoothed

    private final Paint circlePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleNorth   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleSouth   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardinalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint degreePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    // D1: shadowPaint was here but never used — needleNorth.setShadowLayer() handles shadows.
    private final Paint triPaint      = new Paint(Paint.ANTI_ALIAS_FLAG); // P6: was allocated per-frame in onDraw

    private final Path needlePath = new Path();
    private final Path triPath    = new Path(); // P6: reused across frames

    public CompassView(Context ctx) { super(ctx); init(ctx); }
    public CompassView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }
    public CompassView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(ctx); }

    private int colNorth, colNorthShadow, colCardinal;

    private void init(Context ctx) {
        int colBg       = ContextCompat.getColor(ctx, R.color.compass_background);
        int colRing     = ContextCompat.getColor(ctx, R.color.compass_ring);
        int colTick     = ContextCompat.getColor(ctx, R.color.compass_tick);
        int colLabel    = ContextCompat.getColor(ctx, R.color.compass_label);
        int colSouth    = ContextCompat.getColor(ctx, R.color.compass_needle_south);
        int colCenter   = ContextCompat.getColor(ctx, R.color.compass_center);
        int colText     = ContextCompat.getColor(ctx, R.color.compass_text);
        colNorth        = ContextCompat.getColor(ctx, R.color.compass_needle_north);
        colNorthShadow  = ContextCompat.getColor(ctx, R.color.compass_needle_north_shadow);
        colCardinal     = ContextCompat.getColor(ctx, R.color.compass_cardinal);

        // Background
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(colBg);

        // Outer ring
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(5f);
        ringPaint.setColor(colRing);

        // Tick marks
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2f);
        tickPaint.setColor(colTick);

        // Needle - north (red)
        needleNorth.setStyle(Paint.Style.FILL);
        needleNorth.setColor(colNorth);
        needleNorth.setShadowLayer(6f, 0, 2f, colNorthShadow);

        // Needle - south (blue)
        needleSouth.setStyle(Paint.Style.FILL);
        needleSouth.setColor(colSouth);

        // Center dot
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(colCenter);

        // Cardinal (N/S/E/W) text
        cardinalPaint.setColor(colCardinal);
        cardinalPaint.setTextAlign(Paint.Align.CENTER);
        cardinalPaint.setTextSize(36f);
        cardinalPaint.setTypeface(Typeface.DEFAULT_BOLD);
        cardinalPaint.setAntiAlias(true);

        // Degree/label text
        labelPaint.setColor(colLabel);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(22f);
        labelPaint.setAntiAlias(true);

        // Degree display in center
        degreePaint.setColor(colText);
        degreePaint.setTextAlign(Paint.Align.CENTER);
        degreePaint.setTextSize(30f);
        degreePaint.setTypeface(Typeface.DEFAULT_BOLD);
        degreePaint.setAntiAlias(true);

        // P6: triangle pointer paint (was allocated per draw call)
        triPaint.setStyle(Paint.Style.FILL);
        triPaint.setColor(colNorth);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // for shadow

        // Accessibility (U6): TalkBack reads this
        setContentDescription(ctx.getString(R.string.cd_compass));
    }

    public void setBearing(float degrees) {
        this.bearing = degrees;
        // Smooth rotation - handle 359→1 wrap
        float diff = degrees - displayBearing;
        if (diff > 180)  diff -= 360;
        if (diff < -180) diff += 360;
        displayBearing += diff * 0.25f; // lerp factor
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float R  = Math.min(w, h) / 2f - 12f; // outer radius

        // ── Background circle ──────────────────────────────────────────
        canvas.drawCircle(cx, cy, R, circlePaint);

        // ── Concentric rings ──────────────────────────────────────────
        ringPaint.setAlpha(255);
        canvas.drawCircle(cx, cy, R, ringPaint);
        ringPaint.setAlpha(60);
        canvas.drawCircle(cx, cy, R * 0.65f, ringPaint);
        ringPaint.setAlpha(30);
        canvas.drawCircle(cx, cy, R * 0.35f, ringPaint);

        // ── Tick marks (rotate with compass) ─────────────────────────
        canvas.save();
        canvas.rotate(-displayBearing, cx, cy);

        for (int i = 0; i < 360; i += 5) {
            float angle = (float) Math.toRadians(i);
            boolean isMajor = (i % 45 == 0);
            boolean isMed   = (i % 15 == 0);
            float len = isMajor ? R * 0.14f : isMed ? R * 0.09f : R * 0.05f;
            tickPaint.setStrokeWidth(isMajor ? 3f : 1.5f);
            tickPaint.setAlpha(isMajor ? 255 : isMed ? 160 : 80);
            float x1 = cx + (R - 2f) * (float) Math.sin(angle);
            float y1 = cy - (R - 2f) * (float) Math.cos(angle);
            float x2 = cx + (R - 2f - len) * (float) Math.sin(angle);
            float y2 = cy - (R - 2f - len) * (float) Math.cos(angle);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // ── Cardinal labels (N/E/S/W) ─────────────────────────────────
        float labelR = R * 0.80f;
        Paint.FontMetrics fm = cardinalPaint.getFontMetrics();
        float fh = (fm.descent - fm.ascent) / 2f - fm.descent;
        String[] cardinals = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[] angles       = {  0,  45, 90, 135, 180,  225, 270, 315};
        for (int i = 0; i < cardinals.length; i++) {
            boolean isCard = (i % 2 == 0);
            float ang = (float) Math.toRadians(angles[i]);
            float lx  = cx + labelR * (float) Math.sin(ang);
            float ly  = cy - labelR * (float) Math.cos(ang) + fh;
            if (isCard) {
                cardinalPaint.setColor(angles[i] == 0 ? colNorth : colCardinal);
                cardinalPaint.setTextSize(36f);
                canvas.drawText(cardinals[i], lx, ly, cardinalPaint);
            } else {
                labelPaint.setTextSize(20f);
                canvas.drawText(cardinals[i], lx, ly - 4f, labelPaint);
            }
        }
        canvas.restore();

        // ── Triangle pointer at top (fixed) ───────────────────────────
        // P6: reuse paint + path instead of allocating each frame
        triPath.reset();
        triPath.moveTo(cx, cy - R - 10f);
        triPath.lineTo(cx - 10f, cy - R + 14f);
        triPath.lineTo(cx + 10f, cy - R + 14f);
        triPath.close();
        canvas.drawPath(triPath, triPaint);

        // ── Needle (rotates with bearing) ─────────────────────────────
        canvas.save();
        canvas.rotate(displayBearing, cx, cy);

        float needleW = R * 0.06f;
        float needleHN = R * 0.55f;  // north arm length
        float needleHS = R * 0.35f;  // south arm length

        // North (red)
        needlePath.reset();
        needlePath.moveTo(cx, cy - needleHN);
        needlePath.lineTo(cx - needleW, cy);
        needlePath.lineTo(cx + needleW, cy);
        needlePath.close();
        canvas.drawPath(needlePath, needleNorth);

        // South (blue)
        needlePath.reset();
        needlePath.moveTo(cx, cy + needleHS);
        needlePath.lineTo(cx - needleW, cy);
        needlePath.lineTo(cx + needleW, cy);
        needlePath.close();
        canvas.drawPath(needlePath, needleSouth);

        canvas.restore();

        // ── Center pivot ──────────────────────────────────────────────
        canvas.drawCircle(cx, cy, R * 0.06f, needleNorth); // outer red
        canvas.drawCircle(cx, cy, R * 0.04f, centerPaint); // white center

        // ── Bearing text below center ─────────────────────────────────
        int deg = ((int) bearing + 360) % 360;
        String[] dir = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                        "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        String label = dir[Math.round(deg / 22.5f) % 16] + "  " + deg + "°";
        canvas.drawText(label, cx, cy + R * 0.25f, degreePaint);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int size = Math.min(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        setMeasuredDimension(size, size);
    }
}
