package at.kidstune.spotify;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
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
import static org.mockito.Mockito.*;

class SpotifyImportServiceTest {

    private MockWebServer mockServer;
    private SpotifyImportService service;

    private SpotifyTokenService tokenService;
    private FavoriteRepository  favoriteRepository;
    private ContentRepository   contentRepository;

    private static final String PROFILE_ID   = "profile-test-1";
    private static final String ACCESS_TOKEN = "mock-profile-token";

    @BeforeEach
    void setUp() throws IOException {
        mockServer         = new MockWebServer();
        mockServer.start();

        tokenService       = mock(SpotifyTokenService.class);
        favoriteRepository = mock(FavoriteRepository.class);
        contentRepository  = mock(ContentRepository.class);

        service = new SpotifyImportService(
                tokenService,
                favoriteRepository,
                contentRepository,
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
        verifyNoInteractions(favoriteRepository, contentRepository);
    }

    // ── importLikedSongsAsFavorites – all liked tracks saved ─────────────────

    @Test
    @DisplayName("all liked tracks are added as AllowedContent and Favorites regardless of prior whitelist")
    void importLikedSongs_allTracksCreateFavoriteAndAllowedContent() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(
                List.of("spotify:track:liked-1", "spotify:track:liked-2")));

        when(contentRepository.existsByProfileIdAndSpotifyUri(any(), any())).thenReturn(false);
        when(favoriteRepository.existsByProfileIdAndSpotifyTrackUri(any(), any())).thenReturn(false);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(2)
                .verifyComplete();

        verify(contentRepository, times(2)).save(any(AllowedContent.class));
        verify(favoriteRepository, times(2)).save(any(Favorite.class));
    }

    @Test
    @DisplayName("already-existing Favorite is not duplicated")
    void importLikedSongs_existingFavorite_notDuplicated() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(List.of("spotify:track:existing")));

        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_ID, "spotify:track:existing"))
                .thenReturn(true);
        when(favoriteRepository.existsByProfileIdAndSpotifyTrackUri(PROFILE_ID, "spotify:track:existing"))
                .thenReturn(true);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(0)
                .verifyComplete();

        verify(favoriteRepository, never()).save(any());
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("track not yet in AllowedContent is added during liked songs import")
    void importLikedSongs_newTrack_addedToAllowedContent() {
        when(tokenService.isProfileSpotifyLinked(PROFILE_ID)).thenReturn(true);
        when(tokenService.getValidProfileAccessToken(PROFILE_ID))
                .thenReturn(reactor.core.publisher.Mono.just(ACCESS_TOKEN));

        mockServer.enqueue(likedSongsResponse(List.of("spotify:track:new-track")));

        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_ID, "spotify:track:new-track"))
                .thenReturn(false);
        when(favoriteRepository.existsByProfileIdAndSpotifyTrackUri(PROFILE_ID, "spotify:track:new-track"))
                .thenReturn(false);

        StepVerifier.create(service.importLikedSongsAsFavorites(PROFILE_ID))
                .expectNext(1)
                .verifyComplete();

        verify(contentRepository).save(any(AllowedContent.class));
        verify(favoriteRepository).save(any(Favorite.class));
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
              .append("\",\"artists\":[],\"album\":{\"images\":[]}}}");
        }
        sb.append("],\"next\":null}");
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(sb.toString());
    }
}
