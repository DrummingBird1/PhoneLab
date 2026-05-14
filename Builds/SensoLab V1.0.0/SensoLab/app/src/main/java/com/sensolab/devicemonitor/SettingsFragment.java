package com.sensolab.devicemonitor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private Button btnTheme, btnDisplayMode;
    private TextView tvThemeDesc, tvModeDesc;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        btnTheme       = view.findViewById(R.id.btn_theme);
        btnDisplayMode = view.findViewById(R.id.btn_display_mode);
        tvThemeDesc    = view.findViewById(R.id.tv_theme_desc);
        tvModeDesc     = view.findViewById(R.id.tv_mode_desc);

        updateUI();

        btnTheme.setOnClickListener(v -> {
            SharedPreferences prefs = AppPrefs.get(requireContext());
            boolean isDark = AppPrefs.isDark(requireContext());
            prefs.edit().putString(AppPrefs.KEY_THEME,
                    isDark ? AppPrefs.THEME_LIGHT : AppPrefs.THEME_DARK).apply();
            // Recreate activity to apply theme
            ((MainActivity) requireActivity()).recreateWithTheme();
        });

        btnDisplayMode.setOnClickListener(v -> {
            SharedPreferences prefs = AppPrefs.get(requireContext());
            boolean isVisual = AppPrefs.isVisual(requireContext());
            prefs.edit().putString(AppPrefs.KEY_DISPLAY_MODE,
                    isVisual ? AppPrefs.MODE_TEXTUAL : AppPrefs.MODE_VISUAL).apply();
            updateUI();
            // Notify other tabs (they read prefs on onResume, so a small note is enough)
        });

        return view;
    }

    private void updateUI() {
        if (!isAdded()) return;
        boolean isDark    = AppPrefs.isDark(requireContext());
        boolean isVisual  = AppPrefs.isVisual(requireContext());

        btnTheme.setText(isDark ? "☀️  Switch to Light Mode" : "🌙  Switch to Dark Mode");
        tvThemeDesc.setText(isDark
                ? "Currently: 🌙 Dark Mode"
                : "Currently: ☀️ Light Mode");

        btnDisplayMode.setText(isVisual ? "📝  Switch to Textual Mode" : "🎨  Switch to Visual Mode");
        tvModeDesc.setText(isVisual
                ? "Currently: 🎨 Visual Mode — icons & gauges"
                : "Currently: 📝 Textual Mode — raw numbers & data");
    }
}
