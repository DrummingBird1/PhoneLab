package com.sensolab.devicemonitor;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayoutMediator;
import com.sensolab.devicemonitor.databinding.ActivityMainBinding;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    /** Batch 6: which tab a launcher shortcut should open to. */
    public static final String EXTRA_START_TAB = "start_tab";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ThermalAlerts.ensureChannel(this); // pre-create notification channel
        // Permissions are requested contextually per-feature (Sensors tab cards,
        // Settings thermal-alerts toggle) instead of blasted here at launch.
        setupTabs();
        setupShortcuts();
        // Batch 4: idempotent — re-syncs the periodic background check to the current
        // toggle state on every launch, self-healing if it was ever lost (e.g. app data
        // partially cleared) without needing a boot receiver.
        ThermalCheckWorker.reschedule(this);
    }

    private void setupTabs() {
        ViewPager2 vp = binding.viewPager;
        vp.setAdapter(new FragmentStateAdapter(this) {
            @Override public int getItemCount() { return 5; }
            @Override public Fragment createFragment(int pos) {
                switch (pos) {
                    case 0: return new SensorsFragment();
                    case 1: return new SystemFragment();
                    case 2: return new HardwareFragment();   // v1.2.0
                    case 3: return new AboutFragment();
                    default: return new SettingsFragment();
                }
            }
        });
        // P2: was 4 (all fragments resident → sensors+mic+GPS always running, battery hog).
        // Default (off-screen kept = 1) means neighbours stay alive but distant tabs unload.
        vp.setOffscreenPageLimit(ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);
        int[] tabTitles = {
                R.string.tab_sensors, R.string.tab_system, R.string.tab_hardware,
                R.string.tab_about,   R.string.tab_settings};
        new TabLayoutMediator(binding.tabLayout, vp,
                (tab, pos) -> tab.setText(tabTitles[pos])
        ).attach();

        // Batch 6: a launcher shortcut requests a starting tab. Only honoured on
        // cold/warm start (onCreate) — re-tapping a shortcut while the app is already
        // running just resumes the existing task per standard launcher-intent behaviour,
        // a reasonable v1 scope limit rather than adding singleTask/onNewIntent handling.
        int startTab = getIntent().getIntExtra(EXTRA_START_TAB, -1);
        if (startTab >= 0 && startTab < tabTitles.length) {
            vp.setCurrentItem(startTab, false);
        }
    }

    /** Batch 6: dynamic (not static XML) shortcuts — this app ships debug builds under a
     *  different package id (applicationIdSuffix ".debug"), and static shortcut XML needs
     *  a hardcoded target package/class. Building the Intent from MainActivity.class here
     *  resolves correctly under either package id with no placeholder gymnastics. */
    private void setupShortcuts() {
        ShortcutInfoCompat record = buildShortcut("record",
                R.string.shortcut_record_short, R.string.shortcut_record_long, R.drawable.ic_recording, 0);
        ShortcutInfoCompat benchmark = buildShortcut("benchmark",
                R.string.shortcut_benchmark_short, R.string.shortcut_benchmark_long, R.drawable.ic_thermal_alert, 1);
        ShortcutInfoCompat hardware = buildShortcut("hardware",
                R.string.shortcut_hardware_short, R.string.shortcut_hardware_long, R.drawable.ic_thermal_alert, 2);
        ShortcutManagerCompat.setDynamicShortcuts(this, Arrays.asList(record, benchmark, hardware));
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
        // N1: supports 3-way theme (dark / light / follow system)
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getNightMode(this));
    }

    public void recreateWithTheme() {
        applyTheme();
        recreate();
    }
}
