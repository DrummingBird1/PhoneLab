package com.sensolab.devicemonitor;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/** The guided device checks, in the order "Start full check" runs them. */
public enum DiagTest {
    TOUCH      (R.string.diag_touch_title,      R.string.diag_touch_desc,      R.drawable.ic_touch,       true),
    MULTITOUCH (R.string.diag_multitouch_title, R.string.diag_multitouch_desc, R.drawable.ic_gesture,     true),
    DISPLAY    (R.string.diag_display_title,    R.string.diag_display_desc,    R.drawable.ic_palette,     true),
    SPEAKER    (R.string.diag_speaker_title,    R.string.diag_speaker_desc,    R.drawable.ic_speaker,     false),
    MICROPHONE (R.string.diag_mic_title,        R.string.diag_mic_desc,        R.drawable.ic_mic,         false),
    VIBRATION  (R.string.diag_vibration_title,  R.string.diag_vibration_desc,  R.drawable.ic_vibration,   false),
    BUTTONS    (R.string.diag_buttons_title,    R.string.diag_buttons_desc,    R.drawable.ic_device,      false),
    PROXIMITY  (R.string.diag_proximity_title,  R.string.diag_proximity_desc,  R.drawable.ic_proximity,   false),
    FLASHLIGHT (R.string.diag_flashlight_title, R.string.diag_flashlight_desc, R.drawable.ic_flashlight,  false),
    SENSORS    (R.string.diag_sensors_title,    R.string.diag_sensors_desc,    R.drawable.ic_tab_sensors, false),
    BATTERY    (R.string.diag_battery_title,    R.string.diag_battery_desc,    R.drawable.ic_battery,     false);

    @StringRes public final int title;
    @StringRes public final int description;
    @DrawableRes public final int icon;
    /** Runs edge-to-edge with system bars hidden (needs the whole panel). */
    public final boolean fullscreen;

    DiagTest(int title, int description, int icon, boolean fullscreen) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.fullscreen = fullscreen;
    }
}
