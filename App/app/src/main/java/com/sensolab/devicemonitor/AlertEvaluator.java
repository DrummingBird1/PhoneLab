package com.sensolab.devicemonitor;

/**
 * Pure hysteresis logic for {@link AlertRule}s: a rule fires once when its condition
 * becomes true, then stays quiet until the value moves back past the threshold by the
 * metric's margin — so a reading hovering at the line doesn't spam notifications.
 */
public final class AlertEvaluator {
    private AlertEvaluator() {}

    public static final class Decision {
        public final boolean notify;
        public final boolean tripped;
        Decision(boolean notify, boolean tripped) { this.notify = notify; this.tripped = tripped; }
    }

    public static Decision evaluate(AlertRule rule, float value, boolean wasTripped) {
        if (!rule.enabled || Float.isNaN(value)) return new Decision(false, wasTripped);
        float margin = rule.metric.marginFor(rule.threshold);
        boolean conditionMet = rule.above ? value >= rule.threshold : value <= rule.threshold;
        if (!wasTripped) {
            return conditionMet ? new Decision(true, true) : new Decision(false, false);
        }
        boolean rearm = rule.above ? value <= rule.threshold - margin : value >= rule.threshold + margin;
        return new Decision(false, !rearm);
    }
}
