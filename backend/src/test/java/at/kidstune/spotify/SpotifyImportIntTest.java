package at.kidstune.spotify;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link SpotifyImportService}.
 *
 * Uses:
 *  - Testcontainers MariaDB for the full Spring context
 *  - MockWebServer to serve fixture Spotify API responses
 *  - @MockitoBean SpotifyTokenService to bypass real OAuth
 *
 * Verifies that all Spotify calls use the PROFILE token (not the family token).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SpotifyImportIntTest {

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

    @MockitoBean
    SpotifyTokenService spotifyTokenService;

    @Autowired SpotifyImportService spotifyImportService;
    @Autowired FamilyRepository     familyRepository;
    @Autowired ProfileRepository    profileRepository;

    static final String FAMILY_ID        = UUID.randomUUID().toString();
    static final String PROFILE_ID       = UUID.randomUUID().toString();
    static final String PROFILE_TOKEN    = "profile-access-token-xyz";

    @BeforeEach
    void setUp() {
        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("import-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setSpotifyUserId("import-spotify-user");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Import Test Child");
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.PRESCHOOL);
            // spotifyUserId + spotifyRefreshToken left null → not linked by default
            profileRepository.save(p);
        }
    }

    // ── Unlinked profile → 409 ────────────────────────────────────────────────

    @Test
    void getImportSuggestions_unlinked_profile_returns_error() {
        // profile has no Spotify credentials → isProfileSpotifyLinked returns false
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(false);

        StepVerifier.create(spotifyImportService.getImportSuggestions(PROFILE_ID))
                .expectError(ProfileSpotifyNotLinkedException.class)
                .verify();
    }

    // ── Linked profile uses PROFILE token (not family token) ─────────────────

    @Test
    void getImportSuggestions_uses_profile_token_not_family_token() throws Exception {
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(spotifyTokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(Mono.just(PROFILE_TOKEN));

        // Use URL-routing dispatcher so parallel Mono.zip calls get the right fixture
        installImportDispatcher();

        StepVerifier.create(spotifyImportService.getImportSuggestions(PROFILE_ID))
                .assertNext(suggestions -> {
                    assertThat(suggestions).isNotNull();
                    // Results present — detailed grouping tested separately
                })
                .verifyComplete();

        // Verify all 4 requests used the PROFILE token
        List<RecordedRequest> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            requests.add(mockSpotify.takeRequest());
        }
        for (RecordedRequest req : requests) {
            assertThat(req.getHeader("Authorization"))
                    .as("Each Spotify call must use the profile token, not the family token")
                    .isEqualTo("Bearer " + PROFILE_TOKEN);
        }
    }

    // ── Suggestions are grouped correctly ─────────────────────────────────────

    @Test
    void getImportSuggestions_groups_children_content_and_playlists_correctly() throws Exception {
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(spotifyTokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(Mono.just(PROFILE_TOKEN));

        // Fixtures: medium has "Bibi & Tina" (known children, min_age 3) and
        // "Die drei ??? Kids" (known children, min_age 6).
        // For PRESCHOOL profile, Bibi & Tina should be pre-selected, Die drei ??? Kids not.
        // Use URL-routing dispatcher so parallel Mono.zip calls get the right fixture.
        installImportDispatcher();

        ImportSuggestionsDto result = spotifyImportService.getImportSuggestions(PROFILE_ID)
                .block();

        assertThat(result).isNotNull();

        // "Bibi & Tina" → detectedChildrenContent (pre-selected for PRESCHOOL)
        assertThat(result.detectedChildrenContent())
                .anyMatch(item -> item.title().equals("Bibi & Tina") && item.preSelected());

        // "Die drei ??? Kids" → detectedChildrenContent but NOT pre-selected
        assertThat(result.detectedChildrenContent())
                .anyMatch(item -> item.title().equals("Die drei ??? Kids") && !item.preSelected());

        // "Popular Artist" → otherArtists
        assertThat(result.otherArtists())
                .anyMatch(item -> item.title().equals("Popular Artist"));

        // Playlists separate bucket
        assertThat(result.playlists()).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Installs a URL-routing dispatcher on MockWebServer so that parallel WebClient calls
     * inside Mono.zip always receive the correct fixture regardless of request order.
     */
    private void installImportDispatcher() throws IOException {
        String recentlyPlayed  = readFixture("import-recently-played.json");
        String topMedium       = readFixture("import-top-artists-medium.json");
        String topLong         = readFixture("import-top-artists-long.json");
        String playlists       = readFixture("import-user-playlists.json");

        mockSpotify.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.startsWith("/v1/me/player/recently-played")) {
                    return jsonResponse(recentlyPlayed);
                } else if (path.startsWith("/v1/me/top/artists") && path.contains("medium_term")) {
                    return jsonResponse(topMedium);
                } else if (path.startsWith("/v1/me/top/artists") && path.contains("long_term")) {
                    return jsonResponse(topLong);
                } else if (path.startsWith("/v1/me/playlists")) {
                    return jsonResponse(playlists);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    private String readFixture(String filename) throws IOException {
        try (var stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("spotify-fixtures/" + filename))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private void enqueueFixture(String filename) throws IOException {
        mockSpotify.enqueue(jsonResponse(readFixture(filename)));
    }
}
