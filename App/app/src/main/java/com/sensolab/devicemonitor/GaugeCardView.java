package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.card.MaterialCardView;

/**
 * Visual-mode card: icon + title header over a real gauge (arc or bar), with an optional
 * one-line caption (state words such as "Comfortable", "Moving"). Configure in XML with
 * {@code app:gaugeType}, {@code app:gaugeMin/Max} and optional {@code app:gaugeWarn/Hot}.
 */
public class GaugeCardView extends MaterialCardView {

    public static final int TYPE_RADIAL = 0, TYPE_BAR = 1;

    private final int type;
    private final ImageView icon;
    private final TextView title, barValue, barUnit, caption;
    private final RadialGaugeView radial;
    private final BarMeterView bar;

    public GaugeCardView(Context ctx) { this(ctx, null); }
    public GaugeCardView(Context ctx, @Nullable AttributeSet attrs) {
        this(ctx, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }
    public GaugeCardView(Context ctx, @Nullable AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        LayoutInflater.from(ctx).inflate(R.layout.view_gauge_card, this, true);
        icon     = findViewById(R.id.gauge_icon);
        title    = findViewById(R.id.gauge_title);
        radial   = findViewById(R.id.gauge_radial);
        bar      = findViewById(R.id.gauge_bar);
        barValue = findViewById(R.id.bar_value);
        barUnit  = findViewById(R.id.bar_unit);
        caption  = findViewById(R.id.gauge_caption);

        int t = TYPE_RADIAL;
        float min = 0f, max = 100f, warn = Float.NaN, hot = Float.NaN;
        if (attrs != null) {
            TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.GaugeCardView);
            int iconRes = a.getResourceId(R.styleable.GaugeCardView_sensorIcon, 0);
            CharSequence ttl = a.getText(R.styleable.GaugeCardView_sensorTitle);
            t    = a.getInt(R.styleable.GaugeCardView_gaugeType, TYPE_RADIAL);
            min  = a.getFloat(R.styleable.GaugeCardView_gaugeMin, 0f);
            max  = a.getFloat(R.styleable.GaugeCardView_gaugeMax, 100f);
            warn = a.getFloat(R.styleable.GaugeCardView_gaugeWarn, Float.NaN);
            hot  = a.getFloat(R.styleable.GaugeCardView_gaugeHot, Float.NaN);
            a.recycle();
            if (iconRes != 0) icon.setImageResource(iconRes); else icon.setVisibility(GONE);
            if (ttl != null) title.setText(ttl);
        }
        type = t;
        radial.setRange(min, max);
        radial.setBands(warn, hot);
        bar.setRange(min, max);
        bar.setBands(warn, hot);
        radial.setVisibility(type == TYPE_RADIAL ? VISIBLE : GONE);
        findViewById(R.id.bar_group).setVisibility(type == TYPE_BAR ? VISIBLE : GONE);
    }

    public void setRange(float min, float max) {
        radial.setRange(min, max);
        bar.setRange(min, max);
    }

    public void setReading(float value, String valueText, @Nullable String unitText, @Nullable CharSequence captionText) {
        if (type == TYPE_RADIAL) {
            radial.setReading(value, valueText, unitText);
        } else {
            bar.setValue(value);
            barValue.setText(valueText);
            barUnit.setText(unitText == null ? "" : unitText);
        }
        setCaption(captionText);
    }

    public void setUnavailable(CharSequence message) {
        String dash = getContext().getString(R.string.value_placeholder);
        if (type == TYPE_RADIAL) radial.clearReading(dash);
        else { bar.clearValue(); barValue.setText(dash); barUnit.setText(""); }
        setCaption(message);
    }

    public void setActionStatus(CharSequence message, @Nullable View.OnClickListener onTap) {
        setUnavailable(message);
        setOnClickListener(onTap);
        setClickable(onTap != null);
    }

    private void setCaption(@Nullable CharSequence c) {
        caption.setText(c);
        caption.setVisibility(TextUtils.isEmpty(c) ? GONE : VISIBLE);
    }
}
