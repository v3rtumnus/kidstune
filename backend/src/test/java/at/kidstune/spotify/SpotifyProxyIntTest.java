package at.kidstune.spotify;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.spotify.dto.SearchResultsResponse;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for GET /api/v1/spotify/search.
 *
 * Uses:
 *  - Testcontainers MariaDB for Spring context startup (JPA/Liquibase requires DB)
 *  - MockWebServer to serve Spotify API fixture responses
 *  - @MockitoBean SpotifyTokenService to bypass real Spotify OAuth token fetch
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SpotifyProxyIntTest {

    // ── Infrastructure ────────────────────────────────────────────────────────

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("kidstune")
            .withUsername("kidstune")
            .withPassword("kidstune");

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

    // ── Mocks & beans ─────────────────────────────────────────────────────────

    /** Replaced so no DB token-fetch is needed during tests. */
    @MockitoBean
    SpotifyTokenService spotifyTokenService;

    @Autowired JwtTokenService     jwtTokenService;
    @Autowired FamilyRepository    familyRepository;
    @Autowired SpotifyWebApiClient spotifyWebApiClient;

    @LocalServerPort int serverPort;

    // ── Test state ────────────────────────────────────────────────────────────

    static final String FAMILY_ID = UUID.randomUUID().toString();

    WebTestClient client;
    String        parentToken;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .build();

        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "test-device", DeviceType.PARENT);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setSpotifyUserId("spotify-user-test");
            familyRepository.save(f);
        }

        // Return a fake access token — no DB or HTTP call needed
        when(spotifyTokenService.getValidAccessToken(anyString()))
                .thenReturn(Mono.just("fake-access-token"));

        // Clear all search cache entries so each test starts cold
        spotifyWebApiClient.searchCache.invalidateAll();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void search_returnsGroupedArtistsAlbumsAndPlaylists() throws Exception {
        mockSpotify.enqueue(jsonResponse(loadFixture("search-bibi.json")));

        SearchResultsResponse result = client.get()
            .uri("/api/v1/spotify/search?q=BibiGrouped")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(SearchResultsResponse.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.artists()).isNotEmpty();
        assertThat(result.albums()).isNotEmpty();
        assertThat(result.playlists()).isNotEmpty();

        assertThat(result.artists()).extracting("id").contains("bibi-artist-1", "bibi-artist-2");
        assertThat(result.albums()).extracting("id").contains("bibi-album-1");
        assertThat(result.playlists()).extracting("id").contains("bibi-playlist-1");
    }

    @Test
    void search_filtersExplicitContent() throws Exception {
        mockSpotify.enqueue(jsonResponse(loadFixture("search-bibi.json")));

        SearchResultsResponse result = client.get()
            .uri("/api/v1/spotify/search?q=BibiExplicit")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(SearchResultsResponse.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        // bibi-album-explicit has explicit=true — must be absent
        assertThat(result.albums()).extracting("id").doesNotContain("bibi-album-explicit");
        // bibi-album-1 has explicit=false — must be present
        assertThat(result.albums()).extracting("id").contains("bibi-album-1");
    }

    @Test
    void search_cachesResult_secondCallMakesNoHttpRequest() throws Exception {
        // Only enqueue ONE response; if the cache works the second call never hits MockWebServer
        mockSpotify.enqueue(jsonResponse(loadFixture("search-bibi.json")));

        int requestsBefore = mockSpotify.getRequestCount();

        // First call — hits MockWebServer
        client.get()
            .uri("/api/v1/spotify/search?q=BibiCache")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk();

        // Second call with the same query — must be served from cache
        client.get()
            .uri("/api/v1/spotify/search?q=BibiCache")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk();

        // Exactly one new HTTP request should have reached MockWebServer
        assertThat(mockSpotify.getRequestCount() - requestsBefore).isEqualTo(1);
    }

    @Test
    void search_enrichedDtoContainsAllFields() throws Exception {
        mockSpotify.enqueue(jsonResponse(loadFixture("search-bibi.json")));

        SearchResultsResponse result = client.get()
            .uri("/api/v1/spotify/search?q=BibiFields")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(SearchResultsResponse.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        var album = result.albums().get(0);
        assertThat(album.id()).isEqualTo("bibi-album-1");
        assertThat(album.title()).isEqualTo("Bibi Blocksberg Hörspielbox");
        assertThat(album.spotifyUri()).isEqualTo("spotify:album:bibi-album-1");
        assertThat(album.imageUrl()).isNotBlank();
        assertThat(album.artistName()).isEqualTo("Bibi");
    }

    @Test
    void search_requiresAuthentication() {
        client.get()
            .uri("/api/v1/spotify/search?q=Bibi")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
            .setBody(body)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    private static String loadFixture(String filename) throws IOException {
        var url = SpotifyProxyIntTest.class.getClassLoader()
                .getResource("spotify-fixtures/" + filename);
        assertThat(url).as("fixture not found: " + filename).isNotNull();
        return Files.readString(Path.of(Objects.requireNonNull(url).getPath()), StandardCharsets.UTF_8);
    }
}
