package com.example.tube.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

public final class RetryPolicy {

    private final int maxAttempts;
    private final long baseDelayMs;

    public RetryPolicy(int maxAttempts, long baseDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    public <T> T execute(Callable<T> action) throws Exception {
        int attempt = 0;

        while (true) {
            try {
                return action.call();
            } catch (Exception e) {
                attempt++;

                // Don't retry certain errors
                if (!shouldRetry(e) || attempt >= maxAttempts) {
                    throw e;
                }

                long delay = nextDelayMs(attempt);
                System.out.println("retry attempt " + attempt + " (sleep " + delay + "ms)");
                Thread.sleep(delay);
            }
        }
    }

    private boolean shouldRetry(Exception e) {

        // NEVER retry when circuit breaker is open
        if (e instanceof CallNotPermittedException) {
            return false;
        }

        // Retry only on upstream 5xx
        if (e instanceof HttpStatusException hs) {
            return hs.statusCode() >= 500;
        }

        // Retry IO / timeout failures
        return true;
    }


    private long nextDelayMs(int attempt) {
        // Exponential backoff: base * attempt (simple) + jitter
        long base = baseDelayMs * attempt;

        // jitter: 0..(base/4)
        long jitter = (base > 0) ? ThreadLocalRandom.current().nextLong(0, Math.max(1, base / 4)) : 0;

        // cap to avoid runaway sleeps
        long capped = Math.min(base + jitter, 5_000);
        return capped;
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0);
    }
}
