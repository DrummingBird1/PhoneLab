package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.sensolab.devicemonitor.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    // Registered in onCreate() per the Fragment lifecycle contract. Purely a courtesy
    // nudge — ThermalAlerts.postAlert() already self-guards on missing permission, so
    // the toggle still follows the user's tap regardless of the request's outcome.
    private ActivityResultLauncher<String> notificationsPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { /* no-op either way */ });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);

        updateUI();

        binding.btnTheme.setOnClickListener(x -> {
            // N1: cycle dark → light → system → dark
            AppPrefs.setTheme(requireContext(), AppPrefs.nextTheme(requireContext()));
            ((MainActivity) requireActivity()).recreateWithTheme();
        });

        binding.btnDisplayMode.setOnClickListener(x -> {
            boolean vis = AppPrefs.isVisual(requireContext());
            AppPrefs.setDisplayMode(requireContext(),
                    vis ? AppPrefs.MODE_TEXTUAL : AppPrefs.MODE_VISUAL);
            updateUI();
        });

        binding.btnUnits.setOnClickListener(x -> {
            boolean imp = AppPrefs.isImperial(requireContext());
            AppPrefs.setUnitSystem(requireContext(),
                    imp ? AppPrefs.UNITS_METRIC : AppPrefs.UNITS_IMPERIAL);
            updateUI();
        });

        binding.btnThermalAlerts.setOnClickListener(x -> {
            boolean on = !AppPrefs.isThermalAlertsEnabled(requireContext());
            AppPrefs.setThermalAlertsEnabled(requireContext(), on);
            // Batch 4: (de)schedule the background check to match the toggle.
            ThermalCheckWorker.reschedule(requireContext());
            // Batch 3: nudge for POST_NOTIFICATIONS right when the user opts in — a
            // natural, contextual moment instead of asking at launch before they've
            // shown any interest in the feature.
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            updateUI();
        });

        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onResume() { super.onResume(); if (isAdded()) updateUI(); }

    private void updateUI() {
        if (!isAdded() || binding == null) return;
        String theme = AppPrefs.getTheme(requireContext());
        boolean vis  = AppPrefs.isVisual(requireContext());
        boolean imp  = AppPrefs.isImperial(requireContext());

        // N1: theme is 3-way; button label shows where the next tap will take you.
        // Cycle is dark → light → system → dark.
        switch (theme) {
            case AppPrefs.THEME_LIGHT:
                binding.btnTheme.setText(R.string.settings_btn_to_system);
                binding.tvThemeDesc.setText(R.string.settings_now_light);
                break;
            case AppPrefs.THEME_SYSTEM:
                binding.btnTheme.setText(R.string.settings_btn_to_dark);
                binding.tvThemeDesc.setText(R.string.settings_now_system);
                break;
            case AppPrefs.THEME_DARK:
            default:
                binding.btnTheme.setText(R.string.settings_btn_to_light);
                binding.tvThemeDesc.setText(R.string.settings_now_dark);
                break;
        }

        binding.btnDisplayMode.setText(vis ? R.string.settings_btn_to_textual : R.string.settings_btn_to_visual);
        binding.tvModeDesc.setText(vis     ? R.string.settings_now_visual     : R.string.settings_now_textual);

        binding.btnUnits.setText(imp   ? R.string.settings_btn_to_metric : R.string.settings_btn_to_imperial);
        binding.tvUnitsDesc.setText(imp ? R.string.settings_now_imperial : R.string.settings_now_metric);

        boolean thermalOn = AppPrefs.isThermalAlertsEnabled(requireContext());
        binding.btnThermalAlerts.setText(thermalOn ? R.string.settings_btn_thermal_disable : R.string.settings_btn_thermal_enable);
        binding.tvThermalDesc.setText(thermalOn ? R.string.settings_now_thermal_on : R.string.settings_now_thermal_off);
    }
}
