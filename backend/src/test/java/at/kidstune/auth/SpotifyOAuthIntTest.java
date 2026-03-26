package at.kidstune.auth;

import at.kidstune.family.FamilyRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Spotify OAuth PKCE flow.
 *
 * Uses:
 *  - Testcontainers MariaDB for real DB persistence
 *  - MockWebServer for mocking Spotify's /api/token and /v1/me endpoints
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SpotifyOAuthIntTest {

    // ── Infrastructure ────────────────────────────────────────────────────────

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("kidstune")
            .withUsername("kidstune")
            .withPassword("kidstune");

    // Must be started before @DynamicPropertySource fires (static initializer runs first)
    static MockWebServer mockSpotify;

    static {
        mockSpotify = new MockWebServer();
        try {
            mockSpotify.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockSpotify.shutdown();
    }

    @DynamicPropertySource
    static void overrideSpotifyUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + mockSpotify.getPort();
        registry.add("spotify.accounts-base-url", () -> base);
        registry.add("spotify.api-base-url",      () -> base);
        registry.add("spotify.client-id",         () -> "test-client-id");
        registry.add("spotify.client-secret",     () -> "test-client-secret");
        registry.add("spotify.redirect-uri",      () -> "http://localhost/callback");
        registry.add("kidstune.jwt-secret",       () -> "test-jwt-secret-32-characters-!!");
    }

    @LocalServerPort
    int serverPort;

    WebTestClient webTestClient;
    int requestCountAtStart;

    @Autowired SpotifyTokenService  tokenService;
    @Autowired FamilyRepository     familyRepository;

    @BeforeEach
    void setUp() throws InterruptedException {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();
        // Drain leftover recorded requests from previous tests so each test
        // sees a clean queue when calling mockSpotify.takeRequest().
        while (mockSpotify.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
            // discard
        }
        requestCountAtStart = mockSpotify.getRequestCount();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MockResponse tokenResponse(String accessToken, String refreshToken) {
        String body = """
                {
                  "access_token":  "%s",
                  "token_type":    "Bearer",
                  "expires_in":    3600,
                  "refresh_token": "%s",
                  "scope":         "user-read-playback-state"
                }
                """.formatted(accessToken, refreshToken);
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static MockResponse userProfileResponse(String userId) {
        String body = """
                {
                  "id":           "%s",
                  "display_name": "Test User"
                }
                """.formatted(userId);
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void login_redirects_to_spotify_with_pkce_params() {
        webTestClient.get()
                .uri("/api/v1/auth/spotify/login")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().value("Location", location -> {
                    assertThat(location).contains("response_type=code");
                    assertThat(location).contains("code_challenge_method=S256");
                    assertThat(location).contains("code_challenge=");
                    assertThat(location).contains("state=");
                    assertThat(location).contains("client_id=test-client-id");
                });
    }

    @Test
    void callback_creates_family_and_stores_encrypted_refresh_token() throws Exception {
        // 1. Start the login flow to get a valid state + code_verifier
        String locationHeader = webTestClient.get()
                .uri("/api/v1/auth/spotify/login")
                .exchange()
                .expectStatus().isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");

        assertThat(locationHeader).isNotNull();
        String state = extractQueryParam(locationHeader, "state");

        // 2. Enqueue Spotify mock responses: token exchange then user profile
        mockSpotify.enqueue(tokenResponse("mock-access-token-abc", "mock-refresh-token-xyz"));
        mockSpotify.enqueue(userProfileResponse("spotify-user-123"));

        // 3. GET callback with code + state as query params (Spotify's redirect)
        webTestClient.get()
                .uri("/api/v1/auth/spotify/callback?code=test-auth-code&state={state}", state)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.familyId").isNotEmpty()
                .jsonPath("$.accessToken").isEqualTo("mock-access-token-abc")
                .jsonPath("$.expiresIn").isNumber();

        // 4. Verify MockWebServer received the token exchange request
        RecordedRequest tokenRequest = mockSpotify.takeRequest(3, TimeUnit.SECONDS);
        assertThat(tokenRequest).isNotNull();
        assertThat(tokenRequest.getPath()).isEqualTo("/api/token");
        assertThat(tokenRequest.getBody().readUtf8()).contains("grant_type=authorization_code");

        // 5. Verify Family was persisted with an encrypted (non-plaintext) refresh token
        var family = familyRepository.findBySpotifyUserId("spotify-user-123");
        assertThat(family).isPresent();
        assertThat(family.get().getSpotifyRefreshToken()).isNotEqualTo("mock-refresh-token-xyz");
        assertThat(family.get().getSpotifyRefreshToken()).isNotBlank();

        // 6. Verify the stored token decrypts back to the original
        String decrypted = tokenService.decrypt(family.get().getSpotifyRefreshToken());
        assertThat(decrypted).isEqualTo("mock-refresh-token-xyz");
    }

    @Test
    void callback_rejects_invalid_state() {
        webTestClient.get()
                .uri("/api/v1/auth/spotify/callback?code=any-code&state=this-state-was-never-issued")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getValidAccessToken_returns_token_after_successful_callback() throws Exception {
        // Full callback flow first
        String locationHeader = webTestClient.get()
                .uri("/api/v1/auth/spotify/login")
                .exchange()
                .expectStatus().isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");

        String state = extractQueryParam(locationHeader, "state");
        mockSpotify.enqueue(tokenResponse("live-access-token-999", "live-refresh-token-888"));
        mockSpotify.enqueue(userProfileResponse("spotify-user-456"));

        String familyId = webTestClient.get()
                .uri("/api/v1/auth/spotify/callback?code=test-auth-code&state={state}", state)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("familyId")
                .toString();

        // getValidAccessToken returns the cached token without calling Spotify again
        String token = tokenService.getValidAccessToken(familyId).block();
        assertThat(token).isEqualTo("live-access-token-999");
        // Callback made 2 requests; getValidAccessToken must not add more
        assertThat(mockSpotify.getRequestCount()).isEqualTo(requestCountAtStart + 2);
    }

    @Test
    void getValidAccessToken_refreshes_when_token_is_expired() throws Exception {
        // Full callback flow
        String locationHeader = webTestClient.get()
                .uri("/api/v1/auth/spotify/login")
                .exchange()
                .expectStatus().isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");

        String state = extractQueryParam(locationHeader, "state");
        mockSpotify.enqueue(tokenResponse("initial-access-token", "initial-refresh-token"));
        mockSpotify.enqueue(userProfileResponse("spotify-user-789"));

        String familyId = webTestClient.get()
                .uri("/api/v1/auth/spotify/callback?code=test-auth-code&state={state}", state)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("familyId")
                .toString();

        // Simulate expiry by replacing the cache entry with an expired one
        tokenService.accessTokenCache.put(familyId, new SpotifyTokenService.AccessTokenEntry(
                "initial-access-token",
                java.time.Instant.now().minusSeconds(60) // already expired
        ));

        // Enqueue the refresh response
        mockSpotify.enqueue(tokenResponse("refreshed-access-token", null));

        // getValidAccessToken should trigger a refresh
        String refreshed = tokenService.getValidAccessToken(familyId).block();
        assertThat(refreshed).isEqualTo("refreshed-access-token");

        // Verify MockWebServer received a refresh_token grant request
        mockSpotify.takeRequest(1, TimeUnit.SECONDS); // consume token exchange
        mockSpotify.takeRequest(1, TimeUnit.SECONDS); // consume user profile
        RecordedRequest refreshRequest = mockSpotify.takeRequest(3, TimeUnit.SECONDS);
        assertThat(refreshRequest).isNotNull();
        assertThat(refreshRequest.getBody().readUtf8()).contains("grant_type=refresh_token");
    }

    @Test
    void status_returns_unauthenticated_when_no_family_id_header() {
        webTestClient.get()
                .uri("/api/v1/auth/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authenticated").isEqualTo(false)
                .jsonPath("$.spotifyConnected").isEqualTo(false);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String extractQueryParam(String url, String param) {
        URI uri = URI.create(url);
        String query = uri.getRawQuery();
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(param)) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Query param '" + param + "' not found in: " + url);
    }
}
