package at.kidstune.content;

import at.kidstune.content.dto.ContentCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeResolverTest {

    @Mock ContentRepository contentRepository;
    @Mock SpotifyApiClient  spotifyApiClient;

    ScopeResolver resolver;

    static final String PROFILE_A  = "profile-a";
    static final String PROFILE_B  = "profile-b";
    static final String TRACK_URI  = "spotify:track:abc";
    static final String ALBUM_URI  = "spotify:album:xyz";
    static final String ARTIST_URI = "spotify:artist:art1";
    static final String ARTIST_URI2= "spotify:artist:art2";
    static final String PLAYLIST_URI = "spotify:playlist:pl1";
    static final String PLAYLIST_URI2 = "spotify:playlist:pl2";

    @BeforeEach
    void setUp() {
        resolver = new ScopeResolver(contentRepository, spotifyApiClient);
    }

    // ── TRACK scope ───────────────────────────────────────────────────────────

    @Test
    void track_scope_exact_match_is_allowed() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "TRACK_MATCH");
    }

    @Test
    void track_scope_no_match_proceeds_to_album_check() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void track_scope_different_uri_is_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, "spotify:track:other")).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack("spotify:track:other")).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack("spotify:track:other")).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied("spotify:track:other", PROFILE_A);
    }

    @Test
    void track_match_stops_further_checks() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "TRACK_MATCH");
        verify(spotifyApiClient, never()).getAlbumUriForTrack(anyString());
    }

    // ── ALBUM scope ───────────────────────────────────────────────────────────

    @Test
    void album_scope_track_in_allowed_album_is_allowed() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ALBUM_MATCH");
    }

    @Test
    void album_scope_track_in_other_album_is_denied_unless_later_step_matches() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just("spotify:album:other"));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, "spotify:album:other")).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void album_match_stops_artist_and_playlist_checks() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ALBUM_MATCH");
        verify(spotifyApiClient, never()).getArtistUrisForTrack(anyString());
        verify(spotifyApiClient, never()).getTrackUrisInPlaylist(anyString());
    }

    @Test
    void album_scope_different_profile_is_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_B, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_B);
    }

    // ── ARTIST scope ──────────────────────────────────────────────────────────

    @Test
    void artist_scope_any_track_by_allowed_artist_is_allowed() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");
    }

    @Test
    void artist_scope_track_by_other_artist_is_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of("spotify:artist:other")));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, "spotify:artist:other")).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void artist_scope_multiple_artists_one_allowed_is_allowed() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI, ARTIST_URI2)));
        // Only ARTIST_URI2 is allowed
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI2)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");
    }

    @Test
    void artist_scope_three_artists_only_last_allowed() {
        String art3 = "spotify:artist:art3";
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI, ARTIST_URI2, art3)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI2)).thenReturn(false);
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, art3)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");
    }

    @Test
    void artist_scope_allowed_for_profile_a_denied_for_profile_b() {
        // Profile A: artist allowed
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(true);
        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");

        // Profile B: artist not allowed
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, TRACK_URI)).thenReturn(false);
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, ALBUM_URI)).thenReturn(false);
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_B, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_B, ContentScope.PLAYLIST)).thenReturn(List.of());
        assertDenied(TRACK_URI, PROFILE_B);
    }

    @Test
    void artist_match_stops_playlist_check() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");
        verify(spotifyApiClient, never()).getTrackUrisInPlaylist(anyString());
    }

    // ── PLAYLIST scope ────────────────────────────────────────────────────────

    @Test
    void playlist_scope_track_in_allowed_playlist_is_allowed() {
        stubDeniedThroughArtist();
        AllowedContent pl = playlistEntry(PLAYLIST_URI);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of(TRACK_URI)));

        assertAllowed(TRACK_URI, PROFILE_A, "PLAYLIST_MATCH");
    }

    @Test
    void playlist_scope_track_not_in_playlist_is_denied() {
        stubDeniedThroughArtist();
        AllowedContent pl = playlistEntry(PLAYLIST_URI);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of("spotify:track:other")));

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void playlist_scope_no_playlists_for_profile_is_denied() {
        stubDeniedThroughArtist();
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void playlist_scope_empty_track_list_is_denied() {
        stubDeniedThroughArtist();
        AllowedContent pl = playlistEntry(PLAYLIST_URI);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of()));

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void playlist_scope_multiple_playlists_track_in_second_one() {
        stubDeniedThroughArtist();
        AllowedContent pl1 = playlistEntry(PLAYLIST_URI);
        AllowedContent pl2 = playlistEntry(PLAYLIST_URI2);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl1, pl2));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of("spotify:track:other")));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI2)).thenReturn(Mono.just(List.of(TRACK_URI)));

        assertAllowed(TRACK_URI, PROFILE_A, "PLAYLIST_MATCH");
    }

    @Test
    void playlist_scope_multiple_playlists_track_in_none_is_denied() {
        stubDeniedThroughArtist();
        AllowedContent pl1 = playlistEntry(PLAYLIST_URI);
        AllowedContent pl2 = playlistEntry(PLAYLIST_URI2);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl1, pl2));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of("spotify:track:x")));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI2)).thenReturn(Mono.just(List.of("spotify:track:y")));

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void playlist_allowed_for_profile_a_denied_for_profile_b() {
        // Profile A: in playlist
        stubDeniedThroughArtistForProfile(PROFILE_A);
        AllowedContent pl = playlistEntry(PLAYLIST_URI);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of(TRACK_URI)));
        assertAllowed(TRACK_URI, PROFILE_A, "PLAYLIST_MATCH");

        // Profile B: no playlists
        stubDeniedThroughArtistForProfile(PROFILE_B);
        when(contentRepository.findByProfileIdAndScope(PROFILE_B, ContentScope.PLAYLIST)).thenReturn(List.of());
        assertDenied(TRACK_URI, PROFILE_B);
    }

    // ── Algorithm order ───────────────────────────────────────────────────────

    @Test
    void denied_at_all_steps_returns_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void allowed_at_step2_album_after_step1_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ALBUM_MATCH");
    }

    @Test
    void allowed_at_step3_artist_after_steps1_2_denied() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(true);

        assertAllowed(TRACK_URI, PROFILE_A, "ARTIST_MATCH");
    }

    @Test
    void allowed_at_step4_playlist_after_steps1_2_3_denied() {
        stubDeniedThroughArtist();
        AllowedContent pl = playlistEntry(PLAYLIST_URI);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of(pl));
        when(spotifyApiClient.getTrackUrisInPlaylist(PLAYLIST_URI)).thenReturn(Mono.just(List.of(TRACK_URI)));

        assertAllowed(TRACK_URI, PROFILE_A, "PLAYLIST_MATCH");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void empty_allowed_content_returns_denied_immediately() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of()));
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    @Test
    void track_uri_is_case_sensitive() {
        String upperCaseUri = TRACK_URI.toUpperCase();
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, upperCaseUri)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(upperCaseUri)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(upperCaseUri)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ARTIST_URI)).thenReturn(false);
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(upperCaseUri, PROFILE_A);
    }

    @Test
    void no_artist_uris_skips_artist_check_proceeds_to_playlist() {
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(PROFILE_A, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of()));
        when(contentRepository.findByProfileIdAndScope(PROFILE_A, ContentScope.PLAYLIST)).thenReturn(List.of());

        assertDenied(TRACK_URI, PROFILE_A);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertAllowed(String trackUri, String profileId, String expectedReason) {
        ContentCheckResponse result = resolver.isAllowed(trackUri, profileId).block();
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isEqualTo(expectedReason);
    }

    private void assertDenied(String trackUri, String profileId) {
        ContentCheckResponse result = resolver.isAllowed(trackUri, profileId).block();
        assertThat(result).isNotNull();
        assertThat(result.allowed()).isFalse();
    }

    /** Stubs the full deny chain (steps 1-3) for PROFILE_A so playlist tests start from step 4. */
    private void stubDeniedThroughArtist() {
        stubDeniedThroughArtistForProfile(PROFILE_A);
    }

    private void stubDeniedThroughArtistForProfile(String profileId) {
        when(contentRepository.existsByProfileIdAndSpotifyUri(profileId, TRACK_URI)).thenReturn(false);
        when(spotifyApiClient.getAlbumUriForTrack(TRACK_URI)).thenReturn(Mono.just(ALBUM_URI));
        when(contentRepository.existsByProfileIdAndSpotifyUri(profileId, ALBUM_URI)).thenReturn(false);
        when(spotifyApiClient.getArtistUrisForTrack(TRACK_URI)).thenReturn(Mono.just(List.of(ARTIST_URI)));
        when(contentRepository.existsByProfileIdAndSpotifyUri(profileId, ARTIST_URI)).thenReturn(false);
    }

    private AllowedContent playlistEntry(String playlistUri) {
        AllowedContent pl = new AllowedContent();
        pl.setId("pl-" + playlistUri.hashCode());
        pl.setSpotifyUri(playlistUri);
        pl.setScope(ContentScope.PLAYLIST);
        return pl;
    }
}
