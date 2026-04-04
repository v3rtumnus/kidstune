package at.kidstune.resolver;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.content.ContentTypeClassifier;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentResolver} diff logic and per-album content-type
 * classification.  Uses Mockito (no Spring context, no DB, no network).
 */
@ExtendWith(MockitoExtension.class)
class ContentResolverDiffTest {

    @Mock ResolvedAlbumRepository      albumRepo;
    @Mock ResolvedTrackRepository      trackRepo;
    @Mock ContentRepository            contentRepo;
    @Mock ProfileRepository            profileRepo;
    @Mock ContentResolverSpotifyClient spotifyClient;

    ContentResolver resolver;

    static final String FAMILY_ID  = "family-unit-test";
    static final String PROFILE_ID = "profile-unit-test";
    static final String CONTENT_ID = "content-unit-test";

    @BeforeEach
    void setUp() {
        resolver = new ContentResolver(
                albumRepo, trackRepo, contentRepo, profileRepo,
                spotifyClient, new ContentTypeClassifier());

        ChildProfile profile = new ChildProfile();
        profile.setId(PROFILE_ID);
        profile.setFamilyId(FAMILY_ID);
        lenient().when(profileRepo.findById(PROFILE_ID)).thenReturn(Optional.of(profile));

        // albumRepo.save / trackRepo.save are lenient: some tests verify they are NOT called
        lenient().when(albumRepo.save(any(ResolvedAlbum.class))).thenAnswer(inv -> {
            ResolvedAlbum a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID().toString());
            return a;
        });
        lenient().when(trackRepo.save(any(ResolvedTrack.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contentRepo.save(any(AllowedContent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Diff logic ────────────────────────────────────────────────────────────

    @Test
    void reResolve_removes_album_A_and_adds_album_D_keeping_B_and_C() {
        AllowedContent content = artistContent();

        // Existing albums: A, B, C
        ResolvedAlbum albumA = storedAlbum("spotify:album:A");
        ResolvedAlbum albumB = storedAlbum("spotify:album:B");
        ResolvedAlbum albumC = storedAlbum("spotify:album:C");
        when(albumRepo.findByAllowedContentId(CONTENT_ID))
                .thenReturn(List.of(albumA, albumB, albumC));

        // Spotify now returns: B, C, D
        when(spotifyClient.getArtistAlbums(eq(FAMILY_ID), anyString()))
                .thenReturn(Mono.just(List.of(
                        albumData("spotify:album:B"),
                        albumData("spotify:album:C"),
                        albumData("spotify:album:D"))));

        // Tracks for the new album D
        when(spotifyClient.getAlbumTracks(eq(FAMILY_ID), eq("D"), anyString(), anyString(), any()))
                .thenReturn(Mono.just(List.of(
                        trackData("spotify:track:d1", "spotify:album:D"),
                        trackData("spotify:track:d2", "spotify:album:D"))));

        resolver.reResolve(content);

        // A was removed
        verify(albumRepo).delete(albumA);
        // B and C were NOT removed
        verify(albumRepo, never()).delete(albumB);
        verify(albumRepo, never()).delete(albumC);
        // D was added (saved as new ResolvedAlbum)
        ArgumentCaptor<ResolvedAlbum> savedCaptor = ArgumentCaptor.forClass(ResolvedAlbum.class);
        verify(albumRepo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getSpotifyAlbumUri()).isEqualTo("spotify:album:D");
    }

    @Test
    void reResolve_with_identical_albums_does_not_delete_or_add_anything() {
        AllowedContent content = artistContent();

        ResolvedAlbum albumA = storedAlbum("spotify:album:A");
        ResolvedAlbum albumB = storedAlbum("spotify:album:B");
        when(albumRepo.findByAllowedContentId(CONTENT_ID))
                .thenReturn(List.of(albumA, albumB));

        // Spotify returns the same albums
        when(spotifyClient.getArtistAlbums(eq(FAMILY_ID), anyString()))
                .thenReturn(Mono.just(List.of(
                        albumData("spotify:album:A"),
                        albumData("spotify:album:B"))));

        resolver.reResolve(content);

        verify(albumRepo, never()).delete(any(ResolvedAlbum.class));
        verify(albumRepo, never()).save(any(ResolvedAlbum.class));
    }

    // ── Per-album content-type classification ─────────────────────────────────

    @Test
    void resolve_album_with_hoerspiel_genre_classifies_as_AUDIOBOOK() {
        AllowedContent content = albumContent("spotify:album:hoerspiel-1");

        when(spotifyClient.getAlbumDetails(eq(FAMILY_ID), eq("hoerspiel-1")))
                .thenReturn(Mono.just(new AlbumData(
                        "hoerspiel-1", "spotify:album:hoerspiel-1",
                        "Bibi Blocksberg Folge 5",
                        null, "2020-01-01", 3,
                        List.of("hörspiel"), "Bibi")));

        when(spotifyClient.getAlbumTracks(eq(FAMILY_ID), eq("hoerspiel-1"), anyString(), anyString(), any()))
                .thenReturn(Mono.just(List.of(
                        trackData("spotify:track:h1", "spotify:album:hoerspiel-1"),
                        trackData("spotify:track:h2", "spotify:album:hoerspiel-1"),
                        trackData("spotify:track:h3", "spotify:album:hoerspiel-1"))));

        resolver.resolve(content);

        ArgumentCaptor<ResolvedAlbum> cap = ArgumentCaptor.forClass(ResolvedAlbum.class);
        verify(albumRepo).save(cap.capture());
        assertThat(cap.getValue().getContentType()).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test
    void resolve_album_with_no_genres_defaults_to_MUSIC() {
        AllowedContent content = albumContent("spotify:album:music-1");

        when(spotifyClient.getAlbumDetails(eq(FAMILY_ID), eq("music-1")))
                .thenReturn(Mono.just(new AlbumData(
                        "music-1", "spotify:album:music-1",
                        "Pop Hits Vol. 1",
                        null, "2022-05-01", 10,
                        List.of(), "Pop Artist")));

        when(spotifyClient.getAlbumTracks(eq(FAMILY_ID), eq("music-1"), anyString(), anyString(), any()))
                .thenReturn(Mono.just(List.of(
                        trackData("spotify:track:m1", "spotify:album:music-1"))));

        resolver.resolve(content);

        ArgumentCaptor<ResolvedAlbum> cap = ArgumentCaptor.forClass(ResolvedAlbum.class);
        verify(albumRepo).save(cap.capture());
        assertThat(cap.getValue().getContentType()).isEqualTo(ContentType.MUSIC);
    }

    // ── idFromUri helper ──────────────────────────────────────────────────────

    @Test
    void idFromUri_extracts_id_from_spotify_uri() {
        assertThat(ContentResolver.idFromUri("spotify:album:6G9fHYDCoyEErUkHrFYfs4"))
                .isEqualTo("6G9fHYDCoyEErUkHrFYfs4");
        assertThat(ContentResolver.idFromUri("spotify:artist:4dpARuHxo51G3z768sgnrY"))
                .isEqualTo("4dpARuHxo51G3z768sgnrY");
        assertThat(ContentResolver.idFromUri("spotify:track:5ghIJDpPoe3CfHMGu71E6T"))
                .isEqualTo("5ghIJDpPoe3CfHMGu71E6T");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AllowedContent artistContent() {
        AllowedContent c = new AllowedContent();
        c.setId(CONTENT_ID);
        c.setProfileId(PROFILE_ID);
        c.setSpotifyUri("spotify:artist:test-artist");
        c.setScope(ContentScope.ARTIST);
        c.setTitle("Test Artist");
        c.setContentType(ContentType.MUSIC);
        return c;
    }

    private AllowedContent albumContent(String uri) {
        AllowedContent c = new AllowedContent();
        c.setId(CONTENT_ID);
        c.setProfileId(PROFILE_ID);
        c.setSpotifyUri(uri);
        c.setScope(ContentScope.ALBUM);
        c.setTitle("Test Album");
        c.setContentType(ContentType.MUSIC);
        return c;
    }

    private ResolvedAlbum storedAlbum(String uri) {
        ResolvedAlbum a = new ResolvedAlbum();
        a.setId(UUID.randomUUID().toString());
        a.setAllowedContentId(CONTENT_ID);
        a.setSpotifyAlbumUri(uri);
        a.setTitle("Album " + uri);
        a.setContentType(ContentType.MUSIC);
        return a;
    }

    private AlbumData albumData(String uri) {
        String id = ContentResolver.idFromUri(uri);
        return new AlbumData(id, uri, "Album " + id, null, null, 0, List.of(), null);
    }

    private TrackData trackData(String trackUri, String albumUri) {
        return new TrackData(trackUri, "Track " + trackUri, "Artist",
                180_000L, 1, 1, albumUri, "Album", null);
    }
}
