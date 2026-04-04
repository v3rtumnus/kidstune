package at.kidstune.sync;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.content.SpotifyApiClient;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.resolver.ResolvedAlbum;
import at.kidstune.resolver.ResolvedAlbumRepository;
import at.kidstune.resolver.ResolvedTrack;
import at.kidstune.resolver.ResolvedTrackRepository;
import at.kidstune.sync.dto.DeltaSyncPayload;
import at.kidstune.sync.dto.FullSyncPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the sync endpoint.
 *
 * Resolved albums and tracks are inserted directly into the DB (bypassing
 * ContentResolver) to keep these tests focused on the sync output format.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SyncIntTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("kidstune")
            .withUsername("kidstune")
            .withPassword("kidstune");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spotify.client-id",     () -> "test-client-id");
        registry.add("spotify.client-secret", () -> "test-client-secret");
        registry.add("kidstune.jwt-secret",   () -> "test-jwt-secret-32-characters-!!");
        registry.add("kidstune.base-url",     () -> "http://localhost");
    }

    // ContentService (and its @Async resolver trigger) tries to look up Spotify URI info.
    // Mock it here to prevent real HTTP calls when we use the content DELETE endpoint.
    @MockitoBean SpotifyApiClient spotifyApiClient;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    @LocalServerPort int serverPort;
    WebTestClient client;
    String kidsToken;
    String parentToken;

    @Autowired JwtTokenService         jwtTokenService;
    @Autowired FamilyRepository        familyRepo;
    @Autowired ProfileRepository       profileRepo;
    @Autowired ContentRepository       contentRepo;
    @Autowired ResolvedAlbumRepository albumRepo;
    @Autowired ResolvedTrackRepository trackRepo;
    @Autowired FavoriteRepository      favoriteRepo;
    @Autowired DeletionLogRepository   deletionLogRepo;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .build();

        kidsToken   = jwtTokenService.createDeviceToken(FAMILY_ID, "kids-device",   DeviceType.KIDS);
        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "parent-device", DeviceType.PARENT);

        if (!familyRepo.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("sync-test-" + FAMILY_ID + "@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setSpotifyUserId("sync-spotify-" + FAMILY_ID);
            familyRepo.save(f);
        }
        if (!profileRepo.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Luna");
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            profileRepo.save(p);
        }

        // Wipe state for this profile between tests
        contentRepo.deleteAll(contentRepo.findByProfileId(PROFILE_ID));
        favoriteRepo.deleteAll(favoriteRepo.findByProfileId(PROFILE_ID));
        deletionLogRepo.deleteAll(deletionLogRepo.findAll().stream()
                .filter(d -> PROFILE_ID.equals(d.getProfileId())).toList());

        when(spotifyApiClient.getAlbumUriForTrack(anyString()))
                .thenReturn(Mono.just("spotify:album:no-match"));
        when(spotifyApiClient.getArtistUrisForTrack(anyString()))
                .thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(anyString()))
                .thenReturn(Mono.just(List.of()));
    }

    // ── Full sync ──────────────────────────────────────────────────────────────

    @Test
    void fullSync_returns_profile_metadata() {
        FullSyncPayload payload = getFullSync(PROFILE_ID);

        assertThat(payload.profile()).isNotNull();
        assertThat(payload.profile().name()).isEqualTo("Luna");
        assertThat(payload.profile().ageGroup()).isEqualTo(AgeGroup.SCHOOL);
    }

    @Test
    void fullSync_returns_2_content_entries_with_albums_and_tracks() {
        // Entry 1: 2 albums, 3 tracks each
        AllowedContent entry1 = saveContent("spotify:album:sync-a1", ContentScope.ALBUM, "Album One");
        ResolvedAlbum album1a = saveAlbum(entry1, "spotify:album:sync-a1", "Album One", "2022-03-01");
        ResolvedAlbum album1b = saveAlbum(entry1, "spotify:album:sync-a1b", "Album One B", "2023-01-01");
        saveTracks(album1a, 3);
        saveTracks(album1b, 3);

        // Entry 2: 1 album, 5 tracks
        AllowedContent entry2 = saveContent("spotify:album:sync-a2", ContentScope.ALBUM, "Album Two");
        ResolvedAlbum album2 = saveAlbum(entry2, "spotify:album:sync-a2", "Album Two", "2021-06-15");
        saveTracks(album2, 5);

        FullSyncPayload payload = getFullSync(PROFILE_ID);

        assertThat(payload.content()).hasSize(2);
        assertThat(payload.syncTimestamp()).isNotNull();

        var syncEntry1 = payload.content().stream()
                .filter(e -> e.spotifyUri().equals("spotify:album:sync-a1")).findFirst().orElseThrow();
        assertThat(syncEntry1.albums()).hasSize(2);

        long trackCount1 = syncEntry1.albums().stream().mapToLong(a -> a.tracks().size()).sum();
        assertThat(trackCount1).isEqualTo(6);

        var syncEntry2 = payload.content().stream()
                .filter(e -> e.spotifyUri().equals("spotify:album:sync-a2")).findFirst().orElseThrow();
        assertThat(syncEntry2.albums()).hasSize(1);
        assertThat(syncEntry2.albums().get(0).tracks()).hasSize(5);
    }

    @Test
    void fullSync_albums_ordered_newest_release_date_first() {
        AllowedContent entry = saveContent("spotify:artist:sync-artist", ContentScope.ARTIST, "Test Artist");
        saveAlbum(entry, "spotify:album:old",   "Old Album",   "2018-01-01");
        saveAlbum(entry, "spotify:album:new",   "New Album",   "2023-06-01");
        saveAlbum(entry, "spotify:album:mid",   "Mid Album",   "2020-11-15");

        FullSyncPayload payload = getFullSync(PROFILE_ID);

        List<String> albumUris = payload.content().get(0).albums().stream()
                .map(a -> a.spotifyAlbumUri()).toList();
        assertThat(albumUris).containsExactly(
                "spotify:album:new", "spotify:album:mid", "spotify:album:old");
    }

    @Test
    void fullSync_tracks_ordered_by_disc_and_track_number() {
        AllowedContent entry = saveContent("spotify:album:sync-order", ContentScope.ALBUM, "Ordered Album");
        ResolvedAlbum album = saveAlbum(entry, "spotify:album:sync-order", "Ordered Album", "2023-01-01");

        // Insert in scrambled order
        saveTrack(album, "spotify:track:t3", "Track 3", 1, 3);
        saveTrack(album, "spotify:track:t1", "Track 1", 1, 1);
        saveTrack(album, "spotify:track:d2t1", "Disc2 Track1", 2, 1);
        saveTrack(album, "spotify:track:t2", "Track 2", 1, 2);

        FullSyncPayload payload = getFullSync(PROFILE_ID);

        List<String> trackUris = payload.content().get(0).albums().get(0).tracks().stream()
                .map(t -> t.spotifyTrackUri()).toList();
        assertThat(trackUris).containsExactly(
                "spotify:track:t1", "spotify:track:t2", "spotify:track:t3", "spotify:track:d2t1");
    }

    @Test
    void fullSync_includes_favorites() {
        saveFavorite("spotify:track:fav1", "Fav Song 1");
        saveFavorite("spotify:track:fav2", "Fav Song 2");

        FullSyncPayload payload = getFullSync(PROFILE_ID);

        assertThat(payload.favorites()).hasSize(2);
        assertThat(payload.favorites()).extracting(f -> f.spotifyTrackUri())
                .containsExactlyInAnyOrder("spotify:track:fav1", "spotify:track:fav2");
    }

    // ── Delta sync ─────────────────────────────────────────────────────────────

    @Test
    void deltaSync_added_contains_only_new_entry() {
        // First entry added before delta window
        AllowedContent entry1 = saveContent("spotify:album:before", ContentScope.ALBUM, "Before");
        ResolvedAlbum album1 = saveAlbum(entry1, "spotify:album:before", "Before", "2021-01-01");
        saveTracks(album1, 2);

        Instant since = Instant.now();

        // Second entry added after delta window starts
        AllowedContent entry2 = saveContent("spotify:album:after", ContentScope.ALBUM, "After");
        ResolvedAlbum album2 = saveAlbum(entry2, "spotify:album:after", "After", "2022-01-01");
        saveTracks(album2, 3);

        DeltaSyncPayload delta = getDeltaSync(PROFILE_ID, since);

        assertThat(delta.added()).hasSize(1);
        assertThat(delta.added().get(0).spotifyUri()).isEqualTo("spotify:album:after");
        assertThat(delta.added().get(0).albums()).hasSize(1);
        assertThat(delta.added().get(0).albums().get(0).tracks()).hasSize(3);
        assertThat(delta.updated()).isEmpty();
        assertThat(delta.removed()).isEmpty();
    }

    @Test
    void deltaSync_removed_contains_deleted_entry_uri() {
        AllowedContent entry = saveContent("spotify:album:to-delete", ContentScope.ALBUM, "Delete Me");
        saveAlbum(entry, "spotify:album:to-delete", "Delete Me", "2021-01-01");

        Instant since = Instant.now();

        // Delete via API (ensures DeletionLog is written)
        client.delete()
                .uri("/api/v1/profiles/{pid}/content/{id}", PROFILE_ID, entry.getId())
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isNoContent();

        DeltaSyncPayload delta = getDeltaSync(PROFILE_ID, since);

        assertThat(delta.removed()).containsExactly("spotify:album:to-delete");
        assertThat(delta.added()).isEmpty();
    }

    @Test
    void deltaSync_updated_contains_re_resolved_entry() throws InterruptedException {
        AllowedContent entry = saveContent("spotify:artist:update-test", ContentScope.ARTIST, "Updatable");
        Instant since = Instant.now();

        // Simulate re-resolution: save album with resolvedAt AFTER the `since` timestamp
        Thread.sleep(5); // ensure resolvedAt > since
        saveAlbumWithResolvedAt(entry, "spotify:album:new-release", "New Release", "2024-01-01", Instant.now());

        DeltaSyncPayload delta = getDeltaSync(PROFILE_ID, since);

        assertThat(delta.updated()).hasSize(1);
        assertThat(delta.updated().get(0).spotifyUri()).isEqualTo("spotify:artist:update-test");
        assertThat(delta.added()).isEmpty();
    }

    @Test
    void deltaSync_favoritesAdded_contains_new_favorites() throws InterruptedException {
        saveFavorite("spotify:track:pre", "Pre-existing Fav");

        Instant since = Instant.now();
        Thread.sleep(5); // ensure addedAt > since
        saveFavorite("spotify:track:new-fav", "New Fav");

        DeltaSyncPayload delta = getDeltaSync(PROFILE_ID, since);

        assertThat(delta.favoritesAdded()).hasSize(1);
        assertThat(delta.favoritesAdded().get(0).spotifyTrackUri()).isEqualTo("spotify:track:new-fav");
        assertThat(delta.favoritesRemoved()).isEmpty();
    }

    // ── Performance ────────────────────────────────────────────────────────────

    @Test
    void fullSync_with_50_entries_200_albums_1000_tracks_under_500ms() {
        // 50 entries × 4 albums × 5 tracks = 200 albums, 1000 tracks
        for (int i = 0; i < 50; i++) {
            AllowedContent entry = saveContent(
                    "spotify:album:perf-" + i, ContentScope.ALBUM, "Perf Album " + i);
            for (int j = 0; j < 4; j++) {
                ResolvedAlbum album = saveAlbum(
                        entry, "spotify:album:perf-" + i + "-" + j, "Album " + j, "202" + (j % 4) + "-01-01");
                saveTracks(album, 5);
            }
        }

        long start = System.currentTimeMillis();
        FullSyncPayload payload = getFullSync(PROFILE_ID);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(payload.content()).hasSize(50);
        long totalTracks = payload.content().stream()
                .flatMap(e -> e.albums().stream())
                .mapToLong(a -> a.tracks().size())
                .sum();
        assertThat(totalTracks).isEqualTo(1000);
        assertThat(elapsed).as("Full sync should complete in under 500ms").isLessThan(500);
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    void fullSync_without_token_returns_401() {
        client.get().uri("/api/v1/sync/{id}", PROFILE_ID)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void fullSync_with_parent_token_returns_403() {
        client.get().uri("/api/v1/sync/{id}", PROFILE_ID)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FullSyncPayload getFullSync(String profileId) {
        return client.get().uri("/api/v1/sync/{id}", profileId)
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FullSyncPayload.class)
                .returnResult().getResponseBody();
    }

    private DeltaSyncPayload getDeltaSync(String profileId, Instant since) {
        return client.get()
                .uri("/api/v1/sync/{id}/delta?since={since}", profileId, since.toString())
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeltaSyncPayload.class)
                .returnResult().getResponseBody();
    }

    private AllowedContent saveContent(String uri, ContentScope scope, String title) {
        AllowedContent c = new AllowedContent();
        c.setProfileId(PROFILE_ID);
        c.setSpotifyUri(uri);
        c.setScope(scope);
        c.setTitle(title);
        c.setContentType(ContentType.MUSIC);
        return contentRepo.save(c);
    }

    private ResolvedAlbum saveAlbum(AllowedContent content, String uri, String title, String releaseDate) {
        return saveAlbumWithResolvedAt(content, uri, title, releaseDate, Instant.now());
    }

    private ResolvedAlbum saveAlbumWithResolvedAt(AllowedContent content, String uri, String title,
                                                   String releaseDate, Instant resolvedAt) {
        ResolvedAlbum a = new ResolvedAlbum();
        a.setAllowedContentId(content.getId());
        a.setSpotifyAlbumUri(uri);
        a.setTitle(title);
        a.setReleaseDate(releaseDate);
        a.setContentType(ContentType.MUSIC);
        a.setResolvedAt(resolvedAt);
        return albumRepo.save(a);
    }

    private void saveTracks(ResolvedAlbum album, int count) {
        for (int i = 1; i <= count; i++) {
            saveTrack(album, "spotify:track:" + UUID.randomUUID(), "Track " + i, 1, i);
        }
    }

    private void saveTrack(ResolvedAlbum album, String uri, String title, int discNumber, int trackNumber) {
        ResolvedTrack t = new ResolvedTrack();
        t.setResolvedAlbumId(album.getId());
        t.setSpotifyTrackUri(uri);
        t.setTitle(title);
        t.setDiscNumber(discNumber);
        t.setTrackNumber(trackNumber);
        t.setDurationMs(180_000L);
        trackRepo.save(t);
    }

    private void saveFavorite(String trackUri, String title) {
        Favorite f = new Favorite();
        f.setProfileId(PROFILE_ID);
        f.setSpotifyTrackUri(trackUri);
        f.setTrackTitle(title);
        f.setAddedAt(Instant.now());
        favoriteRepo.save(f);
    }
}
