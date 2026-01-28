package com.example.tube.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failuresToOpen;
    private final Duration openDuration;
    private final int halfOpenPermits;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(Instant.EPOCH);
    private final AtomicInteger halfOpenInFlight = new AtomicInteger(0);

    public CircuitBreaker(int failuresToOpen, Duration openDuration, int halfOpenPermits) {
        this.failuresToOpen = failuresToOpen;
        this.openDuration = openDuration;
        this.halfOpenPermits = halfOpenPermits;
    }

    public void acquirePermission() {
        System.out.println("CB state=" + state + " failures=" + failuresToOpen);
        State s = state.get();
        if (s == State.CLOSED) return;

        if (s == State.OPEN) {
            if (Instant.now().isAfter(openedAt.get().plus(openDuration))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) halfOpenInFlight.set(0);
            } else {
                throw new CallNotPermittedException("Circuit breaker is OPEN");
            }
        }

        if (state.get() == State.HALF_OPEN) {
            int cur = halfOpenInFlight.incrementAndGet();
            if (cur > halfOpenPermits) {
                halfOpenInFlight.decrementAndGet();
                throw new CallNotPermittedException("Circuit breaker is HALF_OPEN and permits exhausted");
            }
        }

    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        if (state.get() == State.HALF_OPEN) state.set(State.CLOSED);
        if (halfOpenInFlight.get() > 0) halfOpenInFlight.decrementAndGet();
    }

    public void onFailure() {
        System.out.println("CB onFailure failures=" + failuresToOpen);
        if (halfOpenInFlight.get() > 0) halfOpenInFlight.decrementAndGet();

        int fails = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN) { open(); return; }
        if (state.get() == State.CLOSED && fails >= failuresToOpen) open();


    }

    private void open() {
        state.set(State.OPEN);
        openedAt.set(Instant.now());
        consecutiveFailures.set(0);
        halfOpenInFlight.set(0);
    }
}
