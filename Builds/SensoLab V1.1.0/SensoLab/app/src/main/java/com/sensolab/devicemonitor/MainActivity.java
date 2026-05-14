package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERMS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        setupTabs();
    }

    private void setupTabs() {
        ViewPager2 vp = findViewById(R.id.view_pager);
        TabLayout tl  = findViewById(R.id.tab_layout);
        vp.setAdapter(new FragmentStateAdapter(this) {
            @Override public int getItemCount() { return 4; }
            @Override public Fragment createFragment(int pos) {
                switch (pos) {
                    case 0: return new SensorsFragment();
                    case 1: return new SystemFragment();
                    case 2: return new AboutFragment();
                    default: return new SettingsFragment();
                }
            }
        });
        vp.setOffscreenPageLimit(4);
        new TabLayoutMediator(tl, vp,
                (tab, pos) -> tab.setText(new String[]{
                        "📡 Sensors","⚙️ System","ℹ️ About","🔧 Settings"}[pos])
        ).attach();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] base = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.RECORD_AUDIO
        };
        for (String p : base)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMS);
    }

    public void applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
                AppPrefs.isDark(this)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public void recreateWithTheme() {
        applyTheme();
        recreate();
    }
}
