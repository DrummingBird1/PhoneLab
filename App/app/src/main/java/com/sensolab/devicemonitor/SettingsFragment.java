package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.sensolab.devicemonitor.databinding.DialogAlertRuleBinding;
import com.sensolab.devicemonitor.databinding.FragmentSettingsBinding;
import com.sensolab.devicemonitor.databinding.ItemAlertRuleBinding;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    // Registered in onCreate() per the Fragment lifecycle contract. A courtesy nudge only —
    // both notifiers self-guard on the permission, so toggles always follow the user's tap.
    private ActivityResultLauncher<String> notificationsPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { /* no-op either way */ });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        Context ctx = requireContext();

        binding.btnTheme.setOnClickListener(x -> {
            // N1: cycle dark → light → system → dark
            AppPrefs.setTheme(requireContext(), AppPrefs.nextTheme(requireContext()));
            ((MainActivity) requireActivity()).recreateWithTheme();
        });

        if (DynamicColors.isDynamicColorAvailable()) {
            binding.switchDynamicColor.setVisibility(View.VISIBLE);
            binding.switchDynamicColor.setChecked(AppPrefs.isDynamicColor(ctx));
            binding.switchDynamicColor.setOnCheckedChangeListener((b, on) -> {
                if (on == AppPrefs.isDynamicColor(requireContext())) return;
                AppPrefs.setDynamicColor(requireContext(), on);
                requireActivity().recreate(); // DynamicColors applies on activity creation
            });
        }

        // Android 13+: per-app language lives in system settings (list generated from our
        // translations via generateLocaleConfig).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.btnLanguage.setVisibility(View.VISIBLE);
            binding.btnLanguage.setOnClickListener(x -> {
                Intent i = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null));
                try { startActivity(i); } catch (Exception ignored) {}
            });
        }

        binding.btnDisplayMode.setOnClickListener(x -> {
            boolean vis = AppPrefs.isVisual(requireContext());
            AppPrefs.setDisplayMode(requireContext(), vis ? AppPrefs.MODE_TEXTUAL : AppPrefs.MODE_VISUAL);
            updateUI();
        });

        binding.btnUnits.setOnClickListener(x -> {
            boolean imp = AppPrefs.isImperial(requireContext());
            AppPrefs.setUnitSystem(requireContext(), imp ? AppPrefs.UNITS_METRIC : AppPrefs.UNITS_IMPERIAL);
            updateUI();
            renderRules(); // thresholds are displayed in the selected units
        });

        binding.btnThermalAlerts.setOnClickListener(x -> {
            boolean on = !AppPrefs.isThermalAlertsEnabled(requireContext());
            AppPrefs.setThermalAlertsEnabled(requireContext(), on);
            ThermalCheckWorker.reschedule(requireContext());
            if (on) askForNotifications();
            updateUI();
        });

        binding.btnAddAlert.setOnClickListener(x -> showAddRuleDialog());
        binding.btnCheckAlerts.setOnClickListener(x -> checkAlertsNow());

        binding.switchBatteryHistory.setChecked(AppPrefs.isBatteryHistoryEnabled(ctx));
        binding.switchBatteryHistory.setOnCheckedChangeListener((b, on) -> {
            AppPrefs.setBatteryHistoryEnabled(requireContext(), on);
            ThermalCheckWorker.reschedule(requireContext());
        });
        binding.btnClearBatteryHistory.setOnClickListener(x -> {
            BatteryHistory.clear(requireContext());
            snack(R.string.settings_battery_history_cleared);
        });

        updateUI();
        renderRules();
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        if (!isAdded() || binding == null) return;
        Context ctx = requireContext();
        boolean vis = AppPrefs.isVisual(ctx);
        boolean imp = AppPrefs.isImperial(ctx);

        // N1: label shows where the next tap goes; cycle is dark → light → system → dark.
        switch (AppPrefs.getTheme(ctx)) {
            case AppPrefs.THEME_LIGHT:
                binding.btnTheme.setText(R.string.settings_btn_to_system);
                binding.tvThemeDesc.setText(R.string.settings_now_light);
                break;
            case AppPrefs.THEME_SYSTEM:
                binding.btnTheme.setText(R.string.settings_btn_to_dark);
                binding.tvThemeDesc.setText(R.string.settings_now_system);
                break;
            default:
                binding.btnTheme.setText(R.string.settings_btn_to_light);
                binding.tvThemeDesc.setText(R.string.settings_now_dark);
                break;
        }
        binding.btnDisplayMode.setText(vis ? R.string.settings_btn_to_textual : R.string.settings_btn_to_visual);
        binding.tvModeDesc.setText(vis ? R.string.settings_now_visual : R.string.settings_now_textual);
        binding.btnUnits.setText(imp ? R.string.settings_btn_to_metric : R.string.settings_btn_to_imperial);
        binding.tvUnitsDesc.setText(imp ? R.string.settings_now_imperial : R.string.settings_now_metric);
        boolean thermalOn = AppPrefs.isThermalAlertsEnabled(ctx);
        binding.btnThermalAlerts.setText(thermalOn ? R.string.settings_btn_thermal_disable : R.string.settings_btn_thermal_enable);
        binding.tvThermalDesc.setText(thermalOn ? R.string.settings_now_thermal_on : R.string.settings_now_thermal_off);
    }

    // ── Custom alert rules ────────────────────────────────────────────────
    private void renderRules() {
        if (binding == null) return;
        Context ctx = requireContext();
        List<AlertRule> rules = AlertRules.load(ctx);
        binding.alertRulesList.removeAllViews();
        binding.tvAlertsEmpty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (AlertRule rule : rules) {
            ItemAlertRuleBinding row = ItemAlertRuleBinding.inflate(inf, binding.alertRulesList, false);
            row.switchRule.setText(rule.describe(ctx));
            row.switchRule.setChecked(rule.enabled);
            row.switchRule.setOnCheckedChangeListener((b, on) -> {
                AlertRules.replace(requireContext(), rule.withEnabled(on));
                ThermalCheckWorker.reschedule(requireContext());
            });
            row.btnDeleteRule.setOnClickListener(v -> {
                AlertRules.remove(requireContext(), rule.id);
                ThermalCheckWorker.reschedule(requireContext());
                renderRules();
            });
            binding.alertRulesList.addView(row.getRoot());
        }
    }

    private void showAddRuleDialog() {
        Context ctx = requireContext();
        DialogAlertRuleBinding d = DialogAlertRuleBinding.inflate(LayoutInflater.from(ctx));
        AlertRule.Metric[] metrics = AlertRule.Metric.values();
        String[] labels = new String[metrics.length];
        for (int i = 0; i < metrics.length; i++) labels[i] = getString(metrics[i].label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        d.spinnerMetric.setAdapter(adapter);
        d.spinnerMetric.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                d.tilThreshold.setSuffixText(AlertRule.inputUnit(requireContext(), metrics[pos]));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        d.tilThreshold.setSuffixText(AlertRule.inputUnit(ctx, metrics[0]));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_alerts_add)
                .setView(d.getRoot())
                .setPositiveButton(R.string.alert_dialog_save, null) // set below so invalid input keeps the dialog open
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = d.etThreshold.getText() == null ? "" : d.etThreshold.getText().toString().trim();
                    float userValue;
                    try { userValue = Float.parseFloat(text.replace(',', '.')); }
                    catch (NumberFormatException e) {
                        d.tilThreshold.setError(getString(R.string.alert_dialog_invalid));
                        return;
                    }
                    AlertRule.Metric m = metrics[d.spinnerMetric.getSelectedItemPosition()];
                    boolean above = d.toggleDirection.getCheckedButtonId() == R.id.btn_above;
                    AlertRules.add(requireContext(), new AlertRule(System.currentTimeMillis(), m, above,
                            AlertRule.toCanonical(requireContext(), m, userValue), true));
                    ThermalCheckWorker.reschedule(requireContext());
                    askForNotifications();
                    renderRules();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /** Runs the rule check immediately (off the main thread — it may sample sensors). */
    private void checkAlertsNow() {
        Context app = requireContext().getApplicationContext();
        ExecutorService ex = NamedThreads.singleThread("AlertCheck");
        ex.execute(() -> AlertEngine.checkAll(app));
        ex.shutdown();
        snack(R.string.settings_alerts_checked);
    }

    private void askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void snack(int res) {
        View v = getView();
        if (v != null) Snackbar.make(v, res, Snackbar.LENGTH_SHORT).show();
    }
}
