package com.example.tube.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public final class IpRateLimiter {

    private final int maxRequests;
    private final Duration window;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public IpRateLimiter(int maxRequestsPerWindow, Duration window) {
        this.maxRequests = maxRequestsPerWindow;
        this.window = window;
    }

    public boolean allow(String ip) {
        Instant now = Instant.now();

        WindowCounter wc = counters.computeIfAbsent(ip, k -> new WindowCounter(now));
        wc.rotateIfNeeded(now, window);

        return wc.count.incrementAndGet() <= maxRequests;
    }

    private static final class WindowCounter {
        private volatile Instant windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(Instant start) {
            this.windowStart = start;
        }

        private void rotateIfNeeded(Instant now, Duration window) {
            if (now.isAfter(windowStart.plus(window))) {
                synchronized (this) {
                    if (now.isAfter(windowStart.plus(window))) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
        }
    }
}

