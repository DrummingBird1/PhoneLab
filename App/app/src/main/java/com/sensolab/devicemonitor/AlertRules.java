package com.sensolab.devicemonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** Persistence for {@link AlertRule}s (JSON array in AppPrefs) plus per-rule trip state. */
public final class AlertRules {
    private AlertRules() {}

    public static List<AlertRule> load(Context ctx) {
        List<AlertRule> out = new ArrayList<>();
        String raw = AppPrefs.get(ctx).getString(AppPrefs.KEY_ALERT_RULES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                try { out.add(AlertRule.fromJson(arr.getJSONObject(i))); }
                catch (JSONException | IllegalArgumentException ignored) { /* skip a corrupt row */ }
            }
        } catch (JSONException ignored) {
            // corrupt blob — treat as no rules rather than crash the worker
        }
        return out;
    }

    public static void save(Context ctx, List<AlertRule> rules) {
        JSONArray arr = new JSONArray();
        for (AlertRule r : rules) {
            try { arr.put(r.toJson()); } catch (JSONException ignored) {}
        }
        AppPrefs.get(ctx).edit().putString(AppPrefs.KEY_ALERT_RULES, arr.toString()).apply();
    }

    public static void add(Context ctx, AlertRule rule) {
        List<AlertRule> rules = load(ctx);
        rules.add(rule);
        save(ctx, rules);
    }

    public static void replace(Context ctx, AlertRule rule) {
        List<AlertRule> rules = load(ctx);
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).id == rule.id) rules.set(i, rule);
        save(ctx, rules);
        setTripped(ctx, rule.id, false); // a changed rule starts armed
    }

    public static void remove(Context ctx, long id) {
        List<AlertRule> rules = load(ctx);
        rules.removeIf(r -> r.id == id);
        save(ctx, rules);
        AppPrefs.get(ctx).edit().remove(trippedKey(id)).apply();
    }

    public static boolean anyEnabled(Context ctx) {
        for (AlertRule r : load(ctx)) if (r.enabled) return true;
        return false;
    }

    static boolean isTripped(Context ctx, long id) {
        return AppPrefs.get(ctx).getBoolean(trippedKey(id), false);
    }

    static void setTripped(Context ctx, long id, boolean tripped) {
        AppPrefs.get(ctx).edit().putBoolean(trippedKey(id), tripped).apply();
    }

    private static String trippedKey(long id) { return "alert_tripped_" + id; }
}
