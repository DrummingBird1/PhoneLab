package com.sensolab.devicemonitor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private Button btnTheme, btnDisplayMode, btnUnits;
    private TextView tvThemeDesc, tvModeDesc, tvUnitsDesc;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        btnTheme       = v.findViewById(R.id.btn_theme);
        btnDisplayMode = v.findViewById(R.id.btn_display_mode);
        btnUnits       = v.findViewById(R.id.btn_units);
        tvThemeDesc    = v.findViewById(R.id.tv_theme_desc);
        tvModeDesc     = v.findViewById(R.id.tv_mode_desc);
        tvUnitsDesc    = v.findViewById(R.id.tv_units_desc);

        updateUI();

        btnTheme.setOnClickListener(x -> {
            boolean dark = AppPrefs.isDark(requireContext());
            AppPrefs.get(requireContext()).edit()
                    .putString(AppPrefs.KEY_THEME,
                            dark ? AppPrefs.THEME_LIGHT : AppPrefs.THEME_DARK)
                    .apply();
            ((MainActivity) requireActivity()).recreateWithTheme();
        });

        btnDisplayMode.setOnClickListener(x -> {
            boolean vis = AppPrefs.isVisual(requireContext());
            AppPrefs.get(requireContext()).edit()
                    .putString(AppPrefs.KEY_DISPLAY_MODE,
                            vis ? AppPrefs.MODE_TEXTUAL : AppPrefs.MODE_VISUAL)
                    .apply();
            updateUI();
        });

        btnUnits.setOnClickListener(x -> {
            boolean imp = AppPrefs.isImperial(requireContext());
            AppPrefs.get(requireContext()).edit()
                    .putString(AppPrefs.KEY_UNIT_SYSTEM,
                            imp ? AppPrefs.UNITS_METRIC : AppPrefs.UNITS_IMPERIAL)
                    .apply();
            updateUI();
        });

        return v;
    }

    @Override public void onResume() { super.onResume(); if (isAdded()) updateUI(); }

    private void updateUI() {
        if (!isAdded()) return;
        boolean dark = AppPrefs.isDark(requireContext());
        boolean vis  = AppPrefs.isVisual(requireContext());
        boolean imp  = AppPrefs.isImperial(requireContext());

        btnTheme.setText(dark ? "☀️  Switch to Light Mode" : "🌙  Switch to Dark Mode");
        tvThemeDesc.setText("Currently: " + (dark ? "🌙 Dark" : "☀️ Light") + " Mode");

        btnDisplayMode.setText(vis ? "📝  Switch to Textual Mode" : "🎨  Switch to Visual Mode");
        tvModeDesc.setText("Currently: " + (vis ? "🎨 Visual" : "📝 Textual") + " Mode");

        btnUnits.setText(imp ? "🌍  Switch to Metric (European)" : "🇺🇸  Switch to Imperial (American)");
        tvUnitsDesc.setText(imp
                ? "Currently: 🇺🇸 Imperial\n°F · mph · ft · inHg"
                : "Currently: 🌍 Metric\n°C · km/h · m · hPa");
    }
}
