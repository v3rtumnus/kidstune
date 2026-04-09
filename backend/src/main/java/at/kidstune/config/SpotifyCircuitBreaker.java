package at.kidstune.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual three-state circuit breaker for the Spotify Web API.
 *
 * <ul>
 *   <li><b>CLOSED</b> – normal operation; consecutive failure counter is tracked.</li>
 *   <li><b>OPEN</b>   – after {@value #FAILURE_THRESHOLD} consecutive failures; all calls are
 *       blocked immediately (callers should return cached data or throw
 *       {@link CircuitBreakerOpenException}).  The circuit re-evaluates after
 *       {@link #OPEN_DURATION}.</li>
 *   <li><b>HALF_OPEN</b> – a single trial request is allowed through;
 *       success resets to CLOSED, failure returns to OPEN.</li>
 * </ul>
 *
 * All state transitions are thread-safe.
 */
@Component
public class SpotifyCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(SpotifyCircuitBreaker.class);

    static final int      FAILURE_THRESHOLD = 5;
    static final Duration OPEN_DURATION     = Duration.ofSeconds(30);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state        = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger          failureCount  = new AtomicInteger(0);
    /** Timestamp when the circuit last opened (epoch-ms). Volatile for visibility. */
    private volatile long openedAtMs = 0L;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a Spotify request is allowed to proceed.
     * Transitions OPEN → HALF_OPEN when the timeout has elapsed.
     */
    public boolean allowRequest() {
        State current = effectiveState();
        return current != State.OPEN;
    }

    /** Called on every successful Spotify response. Resets the circuit to CLOSED. */
    public void onSuccess() {
        State prev = state.getAndSet(State.CLOSED);
        failureCount.set(0);
        if (prev != State.CLOSED) {
            log.info("Spotify circuit breaker reset to CLOSED after successful response");
        }
    }

    /**
     * Called on every Spotify API error.
     * Increments the failure counter and opens the circuit when the threshold is reached.
     */
    public void onFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && state.get() != State.OPEN) {
            openCircuit();
        }
        log.warn("Spotify API failure #{} recorded (state={})", failures, state.get());
    }

    /** Returns the current state (accounting for timeout expiry). */
    public State getState() {
        return effectiveState();
    }

    /** Returns the current consecutive failure count. */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Resets the breaker to CLOSED with zero failures.
     * Intended for tests and administrative use only.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        openedAtMs = 0L;
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /** Returns the current state, transitioning OPEN → HALF_OPEN when the timer has elapsed. */
    private State effectiveState() {
        if (state.get() == State.OPEN) {
            long elapsedMs = System.currentTimeMillis() - openedAtMs;
            if (elapsedMs >= OPEN_DURATION.toMillis()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Spotify circuit breaker entering HALF_OPEN – will allow one trial request");
                }
            }
        }
        return state.get();
    }

    private void openCircuit() {
        openedAtMs = System.currentTimeMillis();
        state.set(State.OPEN);
        log.error("Spotify circuit breaker OPENED after {} consecutive failures – "
                + "all Spotify calls blocked for {}s",
                FAILURE_THRESHOLD, OPEN_DURATION.getSeconds());
    }
}
