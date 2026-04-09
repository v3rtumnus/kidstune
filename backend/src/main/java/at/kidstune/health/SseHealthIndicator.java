package at.kidstune.health;

import at.kidstune.sse.SseRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator endpoint exposing active SSE connection count.
 * Accessible at {@code GET /actuator/sse}.
 */
@Component
@Endpoint(id = "sse")
public class SseHealthIndicator {

    private final SseRegistry sseRegistry;

    public SseHealthIndicator(SseRegistry sseRegistry) {
        this.sseRegistry = sseRegistry;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
            "status",           "UP",
            "connectedClients", sseRegistry.getConnectedClientsCount()
        );
    }
}
