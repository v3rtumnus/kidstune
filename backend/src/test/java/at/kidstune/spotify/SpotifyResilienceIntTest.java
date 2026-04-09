package at.kidstune.spotify;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.config.RequestThrottleService;
import at.kidstune.config.SpotifyCircuitBreaker;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Spotify resilience: rate-limit retry with jitter,
 * circuit breaker state transitions, and per-device search throttling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SpotifyResilienceIntTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("kidstune")
            .withUsername("kidstune")
            .withPassword("kidstune");

    static MockWebServer mockSpotify;

    static {
        mockSpotify = new MockWebServer();
        try { mockSpotify.start(); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockSpotify.shutdown();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + mockSpotify.getPort();
        registry.add("spotify.api-base-url",      () -> base);
        registry.add("spotify.accounts-base-url", () -> base);
        registry.add("spotify.client-id",         () -> "test-client-id");
        registry.add("spotify.client-secret",     () -> "test-client-secret");
        registry.add("spotify.redirect-uri",      () -> "http://localhost/callback");
        registry.add("kidstune.jwt-secret",       () -> "test-jwt-secret-32-characters-!!");
        registry.add("kidstune.base-url",         () -> "http://localhost");
    }

    @MockitoBean
    SpotifyTokenService spotifyTokenService;

    @Autowired JwtTokenService       jwtTokenService;
    @Autowired FamilyRepository      familyRepository;
    @Autowired SpotifyCircuitBreaker circuitBreaker;
    @Autowired RequestThrottleService throttle;

    @LocalServerPort int serverPort;

    static final String FAMILY_ID = UUID.randomUUID().toString();

    WebTestClient client;
    String        parentToken;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "test-device-resilience", DeviceType.PARENT);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("resilience-" + FAMILY_ID + "@kidstune.test");
            f.setPasswordHash("irrelevant");
            familyRepository.save(f);
        }

        when(spotifyTokenService.getValidAccessToken(anyString()))
                .thenReturn(Mono.just("mock-access-token"));

        circuitBreaker.reset();
        throttle.resetAll();
    }

    // ── Spotify 429 retry ─────────────────────────────────────────────────────

    @Test
    void spotify429IsRetriedAndEventuallySucceeds() {
        // First response: 429 with Retry-After: 0 (no actual wait in tests)
        mockSpotify.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "0"));
        // Second response: 200 OK
        mockSpotify.enqueue(new MockResponse()
                .setBody("{\"albums\":{\"items\":[]},\"artists\":{\"items\":[]},\"playlists\":{\"items\":[]}}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.get()
            .uri("/api/v1/spotify/search?q=RetryTest")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk();

        assertThat(mockSpotify.getRequestCount()).isGreaterThanOrEqualTo(2);
    }

    // ── Circuit breaker ───────────────────────────────────────────────────────

    @Test
    void circuitBreakerOpensAfterFiveFailures_andReturns503() {
        // Queue enough 500 errors to trip the circuit breaker
        for (int i = 0; i < 5; i++) { // FAILURE_THRESHOLD = 5
            mockSpotify.enqueue(new MockResponse().setResponseCode(500));
        }

        // All 5 calls fail → breaker opens
        for (int i = 0; i < 5; i++) {
            client.get()
                .uri("/api/v1/spotify/search?q=FailTest" + i)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
                .exchange(); // status may vary while accumulating failures
        }

        assertThat(circuitBreaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.OPEN);

        // Next call should be blocked by the open circuit → 503
        client.get()
            .uri("/api/v1/spotify/search?q=OpenCircuit")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void circuitBreakerResetsToClosed_afterSuccessfulResponse() {
        // Trip the circuit (FAILURE_THRESHOLD = 5)
        for (int i = 0; i < 5; i++) {
            mockSpotify.enqueue(new MockResponse().setResponseCode(500));
        }
        for (int i = 0; i < 5; i++) {
            client.get()
                .uri("/api/v1/spotify/search?q=Trip" + i)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
                .exchange();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.OPEN);

        // Reset manually (simulates OPEN duration elapsed)
        circuitBreaker.reset();

        mockSpotify.enqueue(new MockResponse()
                .setBody("{\"albums\":{\"items\":[]},\"artists\":{\"items\":[]},\"playlists\":{\"items\":[]}}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        client.get()
            .uri("/api/v1/spotify/search?q=AfterReset")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk();

        assertThat(circuitBreaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
    }

    // ── Per-device search throttle ────────────────────────────────────────────

    @Test
    void exceedingSearchRateLimitReturns429_withoutHittingSpotify() {
        // Queue a response for each allowed request (SEARCH_LIMIT = 10)
        for (int i = 0; i <= 10; i++) {
            mockSpotify.enqueue(new MockResponse()
                    .setBody("{\"albums\":{\"items\":[]},\"artists\":{\"items\":[]},\"playlists\":{\"items\":[]}}")
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        }

        // Use all allowed requests (SEARCH_LIMIT = 10)
        for (int i = 0; i < 10; i++) {
            client.get()
                .uri("/api/v1/spotify/search?q=ThrottleTest" + i)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk();
        }

        // 11th request must be throttled
        client.get()
            .uri("/api/v1/spotify/search?q=Throttled")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().exists("Retry-After");
    }
}
