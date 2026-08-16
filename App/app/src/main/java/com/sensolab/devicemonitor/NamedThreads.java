package com.sensolab.devicemonitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Z1: tiny helper for named, daemon background executors. Helps profilers,
 * heap dumps and crash logs identify which subsystem owns a hung thread.
 */
public final class NamedThreads {
    private NamedThreads() {}

    public static ExecutorService singleThread(String name) {
        return Executors.newSingleThreadExecutor(daemonFactory(name));
    }

    private static ThreadFactory daemonFactory(String name) {
        final AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "PhoneLab-" + name + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
