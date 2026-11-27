package com.sensolab.devicemonitor;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE super.onCreate
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override public int getItemCount() { return 4; }
            @Override
            public Fragment createFragment(int pos) {
                if (pos == 0) return new SensorsFragment();
                if (pos == 1) return new SystemFragment();
                if (pos == 2) return new AboutFragment();
                return new SettingsFragment();
            }
        });
        viewPager.setOffscreenPageLimit(4);

        String[] tabs = {"📡 Sensors", "⚙️ System", "ℹ️ About", "🔧 Settings"};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(tabs[pos])).attach();
    }

    public void applyTheme() {
        boolean dark = AppPrefs.isDark(this);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES
                     : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** Called from SettingsFragment after theme change */
    public void recreateWithTheme() {
        applyTheme();
        recreate();
    }
}
