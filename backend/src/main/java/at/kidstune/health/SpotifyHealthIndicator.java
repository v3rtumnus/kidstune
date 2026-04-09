package at.kidstune.health;

import at.kidstune.config.SpotifyCircuitBreaker;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator endpoint exposing Spotify circuit breaker state.
 *
 * <p>Accessible at {@code GET /actuator/spotify}.  Must be listed in
 * {@code management.endpoints.web.exposure.include}.
 */
@Component
@Endpoint(id = "spotify")
public class SpotifyHealthIndicator {

    private final SpotifyCircuitBreaker circuitBreaker;

    public SpotifyHealthIndicator(SpotifyCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @ReadOperation
    public Map<String, Object> status() {
        SpotifyCircuitBreaker.State state = circuitBreaker.getState();
        return Map.of(
            "status",              state == SpotifyCircuitBreaker.State.OPEN ? "DOWN" : "UP",
            "circuitBreakerState", state.name(),
            "failureCount",        circuitBreaker.getFailureCount()
        );
    }
}
