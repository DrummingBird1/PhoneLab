package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import com.google.android.material.card.MaterialCardView;

/**
 * Material 3 readout card with a real type hierarchy: small label, large value + unit,
 * optional secondary detail line (e.g. the X/Y/Z components). Replaces the old
 * CardView + single monospace TextView pairs.
 *
 * <p>Declared in XML with {@code app:sensorIcon} and {@code app:sensorTitle}. Must be
 * updated on the main thread.
 */
public class SensorCardView extends MaterialCardView {

    private ImageView icon;
    private TextView title, value, unit, detail;
    private CharSequence lastAnnounced;

    public SensorCardView(Context ctx) { this(ctx, null); }
    public SensorCardView(Context ctx, @Nullable AttributeSet attrs) {
        this(ctx, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }
    public SensorCardView(Context ctx, @Nullable AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        LayoutInflater.from(ctx).inflate(R.layout.view_sensor_card, this, true);
        icon   = findViewById(R.id.card_icon);
        title  = findViewById(R.id.card_title);
        value  = findViewById(R.id.card_value);
        unit   = findViewById(R.id.card_unit);
        detail = findViewById(R.id.card_detail);

        if (attrs != null) {
            TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.SensorCardView);
            int iconRes = a.getResourceId(R.styleable.SensorCardView_sensorIcon, 0);
            CharSequence t = a.getText(R.styleable.SensorCardView_sensorTitle);
            a.recycle();
            if (iconRes != 0) icon.setImageResource(iconRes); else icon.setVisibility(GONE);
            if (t != null) title.setText(t);
        }
    }

    public void setTitle(CharSequence t) { title.setText(t); }
    public CharSequence getTitle()       { return title.getText(); }

    public void setIcon(@DrawableRes int res) {
        icon.setImageResource(res);
        icon.setVisibility(res != 0 ? VISIBLE : GONE);
    }

    /** Main reading: large value plus its unit (unit may be empty). */
    public void setValue(CharSequence v, @Nullable CharSequence u) {
        value.setText(v);
        unit.setText(u == null ? "" : u);
        unit.setVisibility(TextUtils.isEmpty(u) ? GONE : VISIBLE);
    }

    /** Secondary line(s) under the value; empty hides it. */
    public void setDetail(@Nullable CharSequence d) {
        detail.setText(d);
        detail.setVisibility(TextUtils.isEmpty(d) ? GONE : VISIBLE);
    }

    public void setReading(CharSequence v, @Nullable CharSequence u, @Nullable CharSequence d) {
        setValue(v, u);
        setDetail(d);
    }

    /** No live value — show a status message (Not available, Waiting…, …) instead. */
    public void setStatus(CharSequence message) {
        setValue(getContext().getString(R.string.value_placeholder), null);
        setDetail(message);
    }

    /** Status message plus a tap target, used for "Permission needed — tap to grant". */
    public void setActionStatus(CharSequence message, @Nullable View.OnClickListener onTap) {
        setStatus(message);
        setOnClickListener(onTap);
        setClickable(onTap != null);
    }

    /** Appends a warning to the detail line and reads it out to TalkBack once. */
    public void appendWarning(CharSequence warning) {
        CharSequence cur = detail.getText();
        if (cur != null && cur.toString().contains(warning)) return;
        setDetail(TextUtils.isEmpty(cur) ? warning : cur + "\n" + warning);
        if (!TextUtils.equals(lastAnnounced, warning)) {
            lastAnnounced = warning;
            announceForAccessibility(warning);
        }
    }
}
