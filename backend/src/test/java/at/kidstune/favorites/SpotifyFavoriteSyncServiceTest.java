package at.kidstune.favorites;

import at.kidstune.auth.SpotifyTokenService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpotifyFavoriteSyncServiceTest {

    private MockWebServer mockServer;
    private SpotifyFavoriteSyncService service;
    private SpotifyTokenService tokenService;

    private static final String PROFILE_ID   = "profile-123";
    private static final String TRACK_URI    = "spotify:track:abc123def456";
    private static final String TRACK_ID     = "abc123def456";
    private static final String ACCESS_TOKEN = "mock-access-token";

    @BeforeEach
    void setUp() throws IOException {
        mockServer   = new MockWebServer();
        mockServer.start();
        tokenService = mock(SpotifyTokenService.class);

        // Use the package-private constructor to point at the mock server
        service = new SpotifyFavoriteSyncService(
                tokenService,
                mockServer.url("/").toString(),
                WebClient.builder()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // ── mirrorAdd ─────────────────────────────────────────────────────────────

    @Test
    void mirrorAdd_unlinked_profile_completes_without_spotify_call() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(false);

        StepVerifier.create(service.mirrorAdd(PROFILE_ID, TRACK_URI))
                .verifyComplete();

        assertThat(mockServer.getRequestCount()).isZero();
    }

    @Test
    void mirrorAdd_linked_profile_sends_PUT_with_correct_trackId() throws InterruptedException {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(new MockResponse().setResponseCode(200));

        StepVerifier.create(service.mirrorAdd(PROFILE_ID, TRACK_URI))
                .verifyComplete();

        RecordedRequest req = mockServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PUT");
        assertThat(req.getPath()).contains(TRACK_ID);
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    @Test
    void mirrorAdd_non_track_uri_completes_without_spotify_call() {
        // Album URI must never be mirrored
        StepVerifier.create(service.mirrorAdd(PROFILE_ID, "spotify:album:xyz"))
                .verifyComplete();

        assertThat(mockServer.getRequestCount()).isZero();
    }

    @Test
    void mirrorAdd_spotify_returns_500_swallows_error_completes_normally() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(new MockResponse().setResponseCode(500));

        // Must NOT propagate an error – fire-and-forget contract
        StepVerifier.create(service.mirrorAdd(PROFILE_ID, TRACK_URI))
                .verifyComplete();
    }

    // ── mirrorRemove ──────────────────────────────────────────────────────────

    @Test
    void mirrorRemove_linked_profile_sends_DELETE_with_correct_trackId() throws InterruptedException {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(new MockResponse().setResponseCode(200));

        StepVerifier.create(service.mirrorRemove(PROFILE_ID, TRACK_URI))
                .verifyComplete();

        RecordedRequest req = mockServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).contains(TRACK_ID);
    }

    @Test
    void mirrorRemove_non_track_uri_completes_without_spotify_call() {
        StepVerifier.create(service.mirrorRemove(PROFILE_ID, "spotify:artist:xyz"))
                .verifyComplete();

        assertThat(mockServer.getRequestCount()).isZero();
    }
}
