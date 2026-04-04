package at.kidstune.resolver;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link ContentResolver}.
 *
 * Uses:
 *  - Testcontainers MariaDB for Spring context / JPA / Liquibase
 *  - MockWebServer to serve fixture Spotify API responses
 *  - @MockitoBean SpotifyTokenService to bypass real OAuth
 *
 * All tests call {@link ContentResolver#resolve} synchronously (not the @Async wrapper)
 * so assertions can be made immediately without polling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ContentResolverIntTest {

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

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @MockitoBean
    SpotifyTokenService spotifyTokenService;

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Autowired ContentResolver          contentResolver;
    @Autowired ContentRepository        contentRepo;
    @Autowired ResolvedAlbumRepository  albumRepo;
    @Autowired ResolvedTrackRepository  trackRepo;
    @Autowired FamilyRepository         familyRepo;
    @Autowired ProfileRepository        profileRepo;

    // ── Test data ─────────────────────────────────────────────────────────────

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(spotifyTokenService.getValidAccessToken(anyString()))
                .thenReturn(Mono.just("fake-access-token"));

        if (!familyRepo.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("resolver-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setSpotifyUserId("resolver-spotify-user");
            familyRepo.save(f);
        }

        if (!profileRepo.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Test Child");
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            profileRepo.save(p);
        }

        // Clean allowed_content rows for this profile – DB cascade removes resolved_albums + tracks
        contentRepo.deleteAll(contentRepo.findByProfileId(PROFILE_ID));
    }

    // ── ARTIST scope ──────────────────────────────────────────────────────────

    @Test
    void resolveArtist_creates_3_albums_and_15_tracks() throws Exception {
        // Spotify returns 3 albums; 5 tracks per album = 15 tracks total
        mockSpotify.enqueue(json(fixture("resolver-artist-albums-3.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json"))); // album 1
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json"))); // album 2
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json"))); // album 3

        AllowedContent content = saveContent("spotify:artist:res-artist-1", ContentScope.ARTIST);

        contentResolver.resolve(content);

        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(3);

        long totalTracks = albums.stream()
                .mapToLong(a -> trackRepo.findByResolvedAlbumId(a.getId()).size())
                .sum();
        assertThat(totalTracks).isEqualTo(15);
    }

    @Test
    void resolveArtist_sets_resolvedAt_on_allowedContent() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-artist-albums-3.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));

        AllowedContent content = saveContent("spotify:artist:res-artist-1", ContentScope.ARTIST);
        assertThat(content.getResolvedAt()).isNull();

        contentResolver.resolve(content);

        AllowedContent refreshed = contentRepo.findById(content.getId()).orElseThrow();
        assertThat(refreshed.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveArtist_skips_failing_album_and_continues() throws Exception {
        // 3 albums: first album track call fails, other two succeed
        mockSpotify.enqueue(json(fixture("resolver-artist-albums-3.json")));
        mockSpotify.enqueue(new MockResponse().setResponseCode(500));        // album 1 fails
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json"))); // album 2 ok
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json"))); // album 3 ok

        AllowedContent content = saveContent("spotify:artist:res-artist-1", ContentScope.ARTIST);

        contentResolver.resolve(content);

        // 2 albums saved (one skipped), 10 tracks
        assertThat(albumRepo.findByAllowedContentId(content.getId())).hasSize(2);
    }

    // ── ALBUM scope ───────────────────────────────────────────────────────────

    @Test
    void resolveAlbum_creates_1_album_and_correct_track_count() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-album-details-hoerspiel.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-3.json")));

        AllowedContent content = saveContent("spotify:album:res-hoerspiel-album", ContentScope.ALBUM);

        contentResolver.resolve(content);

        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(1);

        ResolvedAlbum album = albums.get(0);
        assertThat(album.getTitle()).isEqualTo("Bibi Blocksberg Folge 12");
        assertThat(album.getReleaseDate()).isEqualTo("2019-09-01");

        assertThat(trackRepo.findByResolvedAlbumId(album.getId())).hasSize(3);
    }

    @Test
    void resolveAlbum_hoerspiel_genre_classifies_as_audiobook() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-album-details-hoerspiel.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-3.json")));

        AllowedContent content = saveContent("spotify:album:res-hoerspiel-album", ContentScope.ALBUM);

        contentResolver.resolve(content);

        ResolvedAlbum album = albumRepo.findByAllowedContentId(content.getId()).get(0);
        assertThat(album.getContentType()).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── PLAYLIST scope ────────────────────────────────────────────────────────

    @Test
    void resolvePlaylist_groups_tracks_into_2_albums() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-playlist-tracks.json")));

        AllowedContent content = saveContent("spotify:playlist:test-playlist", ContentScope.PLAYLIST);

        contentResolver.resolve(content);

        // Fixture has tracks from 2 distinct albums
        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(2);

        long totalTracks = albums.stream()
                .mapToLong(a -> trackRepo.findByResolvedAlbumId(a.getId()).size())
                .sum();
        assertThat(totalTracks).isEqualTo(3); // 2 tracks in album 1, 1 in album 2
    }

    // ── TRACK scope ───────────────────────────────────────────────────────────

    @Test
    void resolveTrack_creates_1_album_and_1_track() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-track.json")));

        AllowedContent content = saveContent("spotify:track:single-track-1", ContentScope.TRACK);

        contentResolver.resolve(content);

        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(1);
        assertThat(albums.get(0).getTitle()).isEqualTo("Single Album Title");

        List<ResolvedTrack> tracks = trackRepo.findByResolvedAlbumId(albums.get(0).getId());
        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).getSpotifyTrackUri()).isEqualTo("spotify:track:single-track-1");
        assertThat(tracks.get(0).getDurationMs()).isEqualTo(240_000L);
        assertThat(tracks.get(0).getTrackNumber()).isEqualTo(3);
    }

    // ── Cascade delete ────────────────────────────────────────────────────────

    @Test
    void deleteAllowedContent_cascades_resolved_albums_and_tracks() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-album-details-hoerspiel.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-3.json")));

        AllowedContent content = saveContent("spotify:album:res-hoerspiel-album", ContentScope.ALBUM);
        contentResolver.resolve(content);

        // Verify rows exist
        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(1);
        String albumId = albums.get(0).getId();
        assertThat(trackRepo.findByResolvedAlbumId(albumId)).hasSize(3);

        // Delete the AllowedContent row → DB cascade should remove ResolvedAlbum + ResolvedTrack
        contentRepo.deleteById(content.getId());

        assertThat(albumRepo.findByAllowedContentId(content.getId())).isEmpty();
        assertThat(trackRepo.findByResolvedAlbumId(albumId)).isEmpty();
    }

    // ── Re-resolution diff ─────────────────────────────────────────────────────

    @Test
    void reResolve_adds_new_album_and_removes_deleted_album() throws Exception {
        // Initial resolution: albums 1, 2, 3
        mockSpotify.enqueue(json(fixture("resolver-artist-albums-3.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-5.json")));

        AllowedContent content = saveContent("spotify:artist:res-artist-1", ContentScope.ARTIST);
        contentResolver.resolve(content);

        assertThat(albumRepo.findByAllowedContentId(content.getId())).hasSize(3);

        // Re-resolution: Spotify now returns albums 2, 3, 4 (album 1 gone, album 4 new)
        mockSpotify.enqueue(json(fixture("resolver-artist-albums-updated.json")));
        mockSpotify.enqueue(json(fixture("resolver-album-tracks-4.json"))); // tracks for new album 4

        contentResolver.reResolve(content);

        List<ResolvedAlbum> albums = albumRepo.findByAllowedContentId(content.getId());
        assertThat(albums).hasSize(3); // 2 kept + 1 new = 3

        List<String> albumUris = albums.stream().map(ResolvedAlbum::getSpotifyAlbumUri).toList();
        assertThat(albumUris).doesNotContain("spotify:album:res-album-1"); // removed
        assertThat(albumUris).contains("spotify:album:res-album-2");       // kept
        assertThat(albumUris).contains("spotify:album:res-album-3");       // kept
        assertThat(albumUris).contains("spotify:album:res-album-4");       // added
    }

    // ── Track data integrity ──────────────────────────────────────────────────

    @Test
    void resolvedTrack_has_duration_and_track_number() throws Exception {
        mockSpotify.enqueue(json(fixture("resolver-track.json")));

        AllowedContent content = saveContent("spotify:track:single-track-1", ContentScope.TRACK);
        contentResolver.resolve(content);

        ResolvedTrack track = trackRepo.findByResolvedAlbumId(
                albumRepo.findByAllowedContentId(content.getId()).get(0).getId()).get(0);

        assertThat(track.getDurationMs()).isEqualTo(240_000L);
        assertThat(track.getTrackNumber()).isEqualTo(3);
        assertThat(track.getDiscNumber()).isEqualTo(1);
        assertThat(track.getArtistName()).isEqualTo("Solo Artist");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AllowedContent saveContent(String uri, ContentScope scope) {
        AllowedContent c = new AllowedContent();
        c.setProfileId(PROFILE_ID);
        c.setSpotifyUri(uri);
        c.setScope(scope);
        c.setTitle("Test Content");
        c.setContentType(ContentType.MUSIC);
        return contentRepo.save(c);
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    private static String fixture(String name) throws IOException, java.net.URISyntaxException {
        var url = ContentResolverIntTest.class.getClassLoader()
                .getResource("spotify-fixtures/" + name);
        assertThat(url).as("fixture not found: " + name).isNotNull();
        return Files.readString(Path.of(Objects.requireNonNull(url).toURI()), StandardCharsets.UTF_8);
    }
}
