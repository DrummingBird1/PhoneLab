package com.sensolab.devicemonitor;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Base for any fragment that has a "textual" and "visual" version of its
 * content side-by-side and toggles between them based on
 * {@link AppPrefs#isVisual(android.content.Context)}.
 *
 * <p>Subclasses call {@link #bindModeLayouts(LinearLayout, LinearLayout)} from
 * onCreateView, then everything is automatic: the fragment listens to
 * SharedPreferences and re-applies the visibility immediately when the user
 * toggles display mode in Settings (U2 — was tab-switch-only before).
 *
 * <p>Subclasses can override {@link #onUnitsChanged()} to refresh any
 * formatted values when the metric/imperial system changes.
 */
public abstract class ModeAwareFragment extends Fragment {

    private LinearLayout layoutTextual, layoutVisual;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sp, key) -> {
                if (!isAdded() || key == null) return;
                if (AppPrefs.KEY_DISPLAY_MODE.equals(key)) applyMode();
                else if (AppPrefs.KEY_UNIT_SYSTEM.equals(key)) onUnitsChanged();
            };

    protected final void bindModeLayouts(LinearLayout textual, LinearLayout visual) {
        this.layoutTextual = textual;
        this.layoutVisual  = visual;
    }

    /** Apply the current display-mode pref to the bound layouts. */
    protected final void applyMode() {
        if (layoutTextual == null || layoutVisual == null) return;
        boolean vis = AppPrefs.isVisual(requireContext());
        layoutTextual.setVisibility(vis ? View.GONE    : View.VISIBLE);
        layoutVisual .setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    /** Hook for subclasses that need to re-render values when units change. */
    protected void onUnitsChanged() { /* default: do nothing */ }

    @Override public void onResume() {
        super.onResume();
        applyMode();
        AppPrefs.get(requireContext()).registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override public void onPause() {
        super.onPause();
        try {
            AppPrefs.get(requireContext()).unregisterOnSharedPreferenceChangeListener(prefsListener);
        } catch (IllegalStateException ignored) { /* context lost — fragment tearing down */ }
    }

    @Nullable protected LinearLayout getTextualLayout() { return layoutTextual; }
    @Nullable protected LinearLayout getVisualLayout()  { return layoutVisual; }
}
