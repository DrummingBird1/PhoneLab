package com.sensolab.devicemonitor;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public class PhoneLabApp extends Application {

    @Override public void onCreate() {
        super.onCreate();
        // Apply the saved theme before any Activity is created, so every screen (not just
        // MainActivity) starts in the right day/night configuration.
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getNightMode(this));
        // Material You wallpaper colors on Android 12+, only when the user opted in
        // (Settings → Appearance). Older devices / opted-out users keep the brand palette.
        DynamicColors.applyToActivitiesIfAvailable(this, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> AppPrefs.isDynamicColor(activity))
                .build());
    }
}
