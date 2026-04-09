package at.kidstune.health;

import at.kidstune.AbstractIntTest;
import at.kidstune.config.SpotifyCircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that all custom Actuator endpoints are reachable and return the
 * expected shape of data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthIndicatorsIntTest extends AbstractIntTest {

    @Autowired SpotifyCircuitBreaker circuitBreaker;

    @LocalServerPort int serverPort;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();
        circuitBreaker.reset();
    }

    @Test
    void spotifyEndpoint_reportsUp_whenCircuitIsClosed() {
        client.get().uri("/actuator/spotify")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.circuitBreakerState").isEqualTo("CLOSED")
            .jsonPath("$.failureCount").isEqualTo(0);
    }

    @Test
    void spotifyEndpoint_reportsDown_whenCircuitIsOpen() {
        for (int i = 0; i < 5; i++) { // FAILURE_THRESHOLD = 5
            circuitBreaker.onFailure();
        }

        client.get().uri("/actuator/spotify")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
            .jsonPath("$.circuitBreakerState").isEqualTo("OPEN");
    }

    @Test
    void sseEndpoint_isReachable_andReportsConnectedClients() {
        client.get().uri("/actuator/sse")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.connectedClients").isNumber();
    }

    @Test
    void contentResolverEndpoint_isReachable_andReportsActiveJobs() {
        client.get().uri("/actuator/contentResolver")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.activeJobs").isNumber();
    }
}
