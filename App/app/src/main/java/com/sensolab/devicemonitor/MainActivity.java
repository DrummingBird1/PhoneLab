package com.sensolab.devicemonitor;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayoutMediator;
import com.sensolab.devicemonitor.databinding.ActivityMainBinding;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    /** Batch 6: which tab a launcher shortcut should open to. */
    public static final String EXTRA_START_TAB = "start_tab";

    public static final int TAB_SENSORS = 0, TAB_SYSTEM = 1, TAB_HARDWARE = 2, TAB_TOOLS = 3,
                            TAB_DIAGNOSTICS = 4, TAB_ABOUT = 5, TAB_SETTINGS = 6;

    private static final int[] TAB_TITLES = {
            R.string.tab_sensors, R.string.tab_system, R.string.tab_hardware, R.string.tab_tools,
            R.string.tab_diagnostics, R.string.tab_about, R.string.tab_settings};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_sensors, R.drawable.ic_tab_system, R.drawable.ic_chip, R.drawable.ic_tab_tools,
            R.drawable.ic_tab_diagnostics, R.drawable.ic_tab_about, R.drawable.ic_tab_settings};

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        // targetSdk 35+ draws edge-to-edge on Android 15+ regardless; opting in explicitly
        // gives the same look on older versions and transparent, contrast-aware bars.
        // Must run after super.onCreate(): only then has AppCompat applied the app's own
        // night mode, which EdgeToEdge reads to pick light or dark status-bar icons.
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyWindowInsets();
        ThermalAlerts.ensureChannel(this);
        AlertNotifier.ensureChannel(this);
        // Permissions are requested contextually per feature, never blasted at launch.
        setupTabs();
        setupShortcuts();
        // Idempotent — re-syncs the periodic background check (thermal / alert rules /
        // battery history / widget) with current settings on every launch.
        ThermalCheckWorker.reschedule(this);
    }

    /** Edge-to-edge: tabs sit below the status bar / cutout, and the pager stops above the
     *  navigation bar so no content ends up hidden behind system UI. */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            binding.viewPager.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupTabs() {
        ViewPager2 vp = binding.viewPager;
        vp.setAdapter(new FragmentStateAdapter(this) {
            @Override public int getItemCount() { return TAB_TITLES.length; }
            @Override public Fragment createFragment(int pos) {
                switch (pos) {
                    case TAB_SENSORS:     return new SensorsFragment();
                    case TAB_SYSTEM:      return new SystemFragment();
                    case TAB_HARDWARE:    return new HardwareFragment();
                    case TAB_TOOLS:       return new ToolsFragment();
                    case TAB_DIAGNOSTICS: return new DiagnosticsFragment();
                    case TAB_ABOUT:       return new AboutFragment();
                    default:              return new SettingsFragment();
                }
            }
        });
        // P2: only neighbouring tabs stay resident, so far-off tabs' sensors/mic/GPS stop.
        vp.setOffscreenPageLimit(ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);
        new TabLayoutMediator(binding.tabLayout, vp, (tab, pos) -> {
            tab.setText(TAB_TITLES[pos]);
            tab.setIcon(TAB_ICONS[pos]);
        }).attach();

        // Batch 6: launcher shortcuts request a starting tab (honoured on cold/warm start).
        int startTab = getIntent().getIntExtra(EXTRA_START_TAB, -1);
        if (startTab >= 0 && startTab < TAB_TITLES.length) vp.setCurrentItem(startTab, false);
    }

    /** Batch 6: dynamic shortcuts — built from MainActivity.class so they resolve under both
     *  the release and the ".debug" package id without hardcoded targets. */
    private void setupShortcuts() {
        ShortcutInfoCompat record = buildShortcut("record",
                R.string.shortcut_record_short, R.string.shortcut_record_long, R.drawable.ic_recording, TAB_SENSORS);
        ShortcutInfoCompat benchmark = buildShortcut("benchmark",
                R.string.shortcut_benchmark_short, R.string.shortcut_benchmark_long, R.drawable.ic_play, TAB_SYSTEM);
        ShortcutInfoCompat diagnostics = buildShortcut("diagnostics",
                R.string.shortcut_diagnostics_short, R.string.shortcut_diagnostics_long,
                R.drawable.ic_tab_diagnostics, TAB_DIAGNOSTICS);
        ShortcutInfoCompat tools = buildShortcut("tools",
                R.string.shortcut_tools_short, R.string.shortcut_tools_long, R.drawable.ic_tab_tools, TAB_TOOLS);
        ShortcutManagerCompat.setDynamicShortcuts(this, Arrays.asList(record, benchmark, diagnostics, tools));
    }

    private ShortcutInfoCompat buildShortcut(String id, int shortLabelRes, int longLabelRes, int iconRes, int tabIndex) {
        Intent intent = new Intent(Intent.ACTION_VIEW, null, this, MainActivity.class)
                .putExtra(EXTRA_START_TAB, tabIndex);
        return new ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(getString(shortLabelRes))
                .setLongLabel(getString(longLabelRes))
                .setIcon(IconCompat.createWithResource(this, iconRes))
                .setIntent(intent)
                .build();
    }

    public void applyTheme() {
        // N1: 3-way theme (dark / light / follow system)
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getNightMode(this));
    }

    public void recreateWithTheme() {
        applyTheme();
        recreate();
    }
}
