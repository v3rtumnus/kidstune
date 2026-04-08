package at.kidstune.spotify;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.resolver.ResolvedTrack;
import at.kidstune.resolver.ResolvedTrackRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SpotifyImportServiceTest {

    private MockWebServer mockServer;
    private SpotifyImportService service;

    private SpotifyTokenService     tokenService;
    private FavoriteRepository      favoriteRepository;
    private ResolvedTrackRepository resolvedTrackRepository;

    private static final String PROFILE_ID   = "profile-test-1";
    private static final String ACCESS_TOKEN = "mock-profile-token";

    @BeforeEach
    void setUp() throws IOException {
        mockServer              = new MockWebServer();
        mockServer.start();

        tokenService            = mock(SpotifyTokenService.class);
        favoriteRepository      = mock(FavoriteRepository.class);
        resolvedTrackRepository = mock(ResolvedTrackRepository.class);

        service = new SpotifyImportService(
                tokenService,
                favoriteRepository,
                resolvedTrackRepository,
                mockServer.url("/").toString(),
                WebClient.builder()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // ── getImportSuggestions – unlinked profile ───────────────────────────────

    @Test
    @DisplayName("getImportSuggestions with unlinked profile throws ProfileSpotifyNotLinkedException")
    void getImportSuggestions_unlinkedProfile_throwsException() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(false);

        StepVerifier.create(service.getImportSuggestions(PROFILE_ID))
                .expectError(ProfileSpotifyNotLinkedException.class)
                .verify();

        assertThat(mockServer.getRequestCount()).isZero();
    }

    // ── importLikedSongsAsFavorites – unlinked profile ────────────────────────

    @Test
    @DisplayName("importLikedSongsAsFavorites with unlinked profile returns 0 without exception")
    void importLikedSongs_unlinkedProfile_returnsZeroWithoutException() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(false);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(0)
                .verifyComplete();

        assertThat(mockServer.getRequestCount()).isZero();
        verifyNoInteractions(favoriteRepository, resolvedTrackRepository);
    }

    // ── importLikedSongsAsFavorites – matched tracks ──────────────────────────

    @Test
    @DisplayName("liked track URI matching resolved content creates Favorite row")
    void importLikedSongs_matchedTrack_createsFavorite() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(
                List.of("spotify:track:liked-1", "spotify:track:liked-2")));

        ResolvedTrack track1 = resolvedTrack("spotify:track:liked-1", "Song 1", "Bibi & Tina");
        when(resolvedTrackRepository.findByProfileIdAndSpotifyTrackUriIn(eq(PROFILE_ID), any()))
                .thenReturn(List.of(track1));
        when(favoriteRepository.existsByProfileIdAndSpotifyTrackUri(PROFILE_ID, "spotify:track:liked-1"))
                .thenReturn(false);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(1)
                .verifyComplete();

        verify(favoriteRepository).save(any(Favorite.class));
    }

    @Test
    @DisplayName("liked track URI not in resolved content is silently skipped")
    void importLikedSongs_unwhitelistedTrack_isSkipped() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(
                List.of("spotify:track:not-whitelisted")));

        when(resolvedTrackRepository.findByProfileIdAndSpotifyTrackUriIn(eq(PROFILE_ID), any()))
                .thenReturn(List.of());

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(0)
                .verifyComplete();

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("already-existing Favorite is not duplicated")
    void importLikedSongs_existingFavorite_notDuplicated() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(List.of("spotify:track:existing")));

        ResolvedTrack track = resolvedTrack("spotify:track:existing", "Existing Song", "Artist");
        when(resolvedTrackRepository.findByProfileIdAndSpotifyTrackUriIn(eq(PROFILE_ID), any()))
                .thenReturn(List.of(track));
        when(favoriteRepository.existsByProfileIdAndSpotifyTrackUri(PROFILE_ID, "spotify:track:existing"))
                .thenReturn(true);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(0)
                .verifyComplete();

        verify(favoriteRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockResponse likedSongsResponse(List<String> trackUris) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < trackUris.size(); i++) {
            if (i > 0) sb.append(',');
            String uri = trackUris.get(i);
            String id  = uri.substring(uri.lastIndexOf(':') + 1);
            sb.append("{\"track\":{\"id\":\"").append(id)
              .append("\",\"name\":\"Track ").append(id)
              .append("\",\"uri\":\"").append(uri)
              .append("\",\"artists\":[]}}");
        }
        sb.append("],\"next\":null}");
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(sb.toString());
    }

    private ResolvedTrack resolvedTrack(String uri, String title, String artist) {
        ResolvedTrack t = new ResolvedTrack();
        t.setSpotifyTrackUri(uri);
        t.setTitle(title);
        t.setArtistName(artist);
        return t;
    }
}
