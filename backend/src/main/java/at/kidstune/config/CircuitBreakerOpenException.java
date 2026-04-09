package at.kidstune.config;

/**
 * Thrown by {@link SpotifyCircuitBreaker} when the circuit is OPEN and no cached
 * fallback value is available.  Maps to HTTP 503 in {@link at.kidstune.common.GlobalExceptionHandler}.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException() {
        super("Spotify API temporarily unavailable – circuit breaker is OPEN");
    }
}
