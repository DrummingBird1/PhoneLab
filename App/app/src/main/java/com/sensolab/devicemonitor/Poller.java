package com.sensolab.devicemonitor;

import android.os.Handler;

/**
 * Small composable "poll every N ms while resumed" helper. Not a base Fragment —
 * SystemFragment already extends ModeAwareFragment and Java has no multiple inheritance,
 * so this is owned as a field and driven from onResume/onPause instead.
 *
 * The instance is reusable across start/stop cycles (survives multiple onResume/onPause
 * pairs without needing to be recreated each time).
 */
public final class Poller {

    /** One poll tick. Scheduling of the next tick is handled by the Poller itself. */
    public interface Action { void poll(); }

    private final Handler handler;
    private final long intervalMs;
    private final Action action;
    private volatile boolean running = false;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            action.poll();
            if (running) handler.postDelayed(this, intervalMs);
        }
    };

    public Poller(Handler handler, long intervalMs, Action action) {
        this.handler = handler;
        this.intervalMs = intervalMs;
        this.action = action;
    }

    public void start() { start(0L); }

    public void start(long initialDelayMs) {
        if (running) return;
        running = true;
        handler.postDelayed(loop, initialDelayMs);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(loop);
    }
}
