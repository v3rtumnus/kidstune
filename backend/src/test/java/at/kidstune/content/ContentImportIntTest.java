package at.kidstune.content;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.dto.ImportContentRequest;
import at.kidstune.content.dto.ImportContentResponse;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.resolver.ContentResolver;
import at.kidstune.spotify.SpotifyImportService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for POST /api/v1/content/import and importLikedSongsAsFavorites.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContentImportIntTest extends AbstractIntTest {

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
    static void tearDownServer() throws IOException {
        mockSpotify.shutdown();
    }

    @DynamicPropertySource
    static void overrideSpotifyUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + mockSpotify.getPort();
        registry.add("spotify.api-base-url",      () -> base);
        registry.add("spotify.accounts-base-url", () -> base);
        registry.add("spotify.redirect-uri",      () -> "http://localhost/callback");
    }

    // Mock ContentResolver so resolveAsync doesn't make real Spotify calls
    @MockitoBean ContentResolver    contentResolver;
    @MockitoBean SpotifyApiClient   spotifyApiClient;
    @MockitoBean SpotifyTokenService spotifyTokenService;

    @LocalServerPort int serverPort;
    WebTestClient client;
    String parentToken;

    static final String FAMILY_ID   = UUID.randomUUID().toString();
    static final String PROFILE_ID1 = UUID.randomUUID().toString();
    static final String PROFILE_ID2 = UUID.randomUUID().toString();

    @Autowired JwtTokenService          jwtTokenService;
    @Autowired FamilyRepository         familyRepository;
    @Autowired ProfileRepository        profileRepository;
    @Autowired ContentRepository        contentRepository;
    @Autowired FavoriteRepository       favoriteRepository;
    @Autowired SpotifyImportService     spotifyImportService;

    @BeforeEach
    void setUp() {
        client      = WebTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "test-device", DeviceType.PARENT);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("import-int-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setSpotifyUserId("import-int-spotify");
            familyRepository.save(f);
        }
        ensureProfile(PROFILE_ID1, "Lena",   AgeGroup.PRESCHOOL);
        ensureProfile(PROFILE_ID2, "Tobias", AgeGroup.SCHOOL);

        contentRepository.deleteAll(contentRepository.findByProfileId(PROFILE_ID1));
        contentRepository.deleteAll(contentRepository.findByProfileId(PROFILE_ID2));
        favoriteRepository.deleteAll(favoriteRepository.findByProfileId(PROFILE_ID1));

        when(spotifyApiClient.getAlbumUriForTrack(anyString()))
                .thenReturn(Mono.just("spotify:album:no-match"));
        when(spotifyApiClient.getArtistUrisForTrack(anyString()))
                .thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(anyString()))
                .thenReturn(Mono.just(List.of()));
    }

    // ── POST /api/v1/content/import ────────────────────────────────────────────

    @Test
    void import_3_items_2_profiles_creates_6_AllowedContent_rows_and_triggers_resolver_6_times() {
        ImportContentRequest request = new ImportContentRequest(List.of(
                new ImportContentRequest.ImportItem(
                        "spotify:artist:bibi",  ContentScope.ARTIST, "Bibi & Tina",
                        null, null, null, null, List.of(PROFILE_ID1, PROFILE_ID2)),
                new ImportContentRequest.ImportItem(
                        "spotify:album:benjii", ContentScope.ALBUM, "Benjamin Blümchen",
                        null, null, null, null, List.of(PROFILE_ID1, PROFILE_ID2)),
                new ImportContentRequest.ImportItem(
                        "spotify:playlist:p1",  ContentScope.PLAYLIST, "Kinderlieder Mix",
                        null, null, null, null, List.of(PROFILE_ID1, PROFILE_ID2))
        ));

        ImportContentResponse response = client.post().uri("/api/v1/content/import")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ImportContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.created()).isEqualTo(6);
        assertThat(response.profiles()).hasSize(2);

        // 3 rows per profile
        assertThat(contentRepository.findByProfileId(PROFILE_ID1)).hasSize(3);
        assertThat(contentRepository.findByProfileId(PROFILE_ID2)).hasSize(3);

        // ContentResolver triggered once per created row
        verify(contentResolver, times(6)).resolveAsync(any(AllowedContent.class));
    }

    @Test
    void import_skips_duplicate_uris_per_profile() {
        // Pre-create one entry for PROFILE_ID1
        AllowedContent existing = new AllowedContent();
        existing.setProfileId(PROFILE_ID1);
        existing.setSpotifyUri("spotify:artist:bibi");
        existing.setScope(ContentScope.ARTIST);
        existing.setTitle("Bibi & Tina");
        existing.setContentType(ContentType.MUSIC);
        contentRepository.save(existing);

        ImportContentRequest request = new ImportContentRequest(List.of(
                new ImportContentRequest.ImportItem(
                        "spotify:artist:bibi", ContentScope.ARTIST, "Bibi & Tina",
                        null, null, null, null, List.of(PROFILE_ID1, PROFILE_ID2))
        ));

        ImportContentResponse response = client.post().uri("/api/v1/content/import")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ImportContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.created()).isEqualTo(1); // only PROFILE_ID2 is new
        assertThat(response.profiles()).hasSize(1);
        assertThat(response.profiles().get(0).id()).isEqualTo(PROFILE_ID2);
    }

    @Test
    void import_requires_authentication() {
        client.post().uri("/api/v1/content/import")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ImportContentRequest(List.of()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── importLikedSongsAsFavorites ────────────────────────────────────────────

    @Test
    void importLikedSongs_5_liked_tracks_all_imported_as_favorites_and_allowed_content() throws Exception {
        when(spotifyTokenService.isProfileSpotifyLinked(PROFILE_ID1)).thenReturn(true);
        when(spotifyTokenService.getValidProfileAccessToken(PROFILE_ID1))
                .thenReturn(Mono.just("profile-token-liked"));

        mockSpotify.enqueue(likedSongsResponse(List.of(
                "spotify:track:liked-track-1",
                "spotify:track:liked-track-2",
                "spotify:track:liked-track-3",
                "spotify:track:liked-track-4",
                "spotify:track:liked-track-5"
        )));

        Integer imported = spotifyImportService.importLikedSongsAsFavorites(PROFILE_ID1).block();

        assertThat(imported).isEqualTo(5);

        List<Favorite> favorites = favoriteRepository.findByProfileId(PROFILE_ID1);
        assertThat(favorites).hasSize(5);
        assertThat(favorites).extracting(Favorite::getSpotifyTrackUri)
                .containsExactlyInAnyOrder(
                        "spotify:track:liked-track-1",
                        "spotify:track:liked-track-2",
                        "spotify:track:liked-track-3",
                        "spotify:track:liked-track-4",
                        "spotify:track:liked-track-5"
                );

        // All liked tracks also added to AllowedContent
        List<AllowedContent> allowed = contentRepository.findByProfileId(PROFILE_ID1);
        assertThat(allowed).hasSize(5);
        assertThat(allowed).extracting(AllowedContent::getSpotifyUri)
                .containsExactlyInAnyOrder(
                        "spotify:track:liked-track-1",
                        "spotify:track:liked-track-2",
                        "spotify:track:liked-track-3",
                        "spotify:track:liked-track-4",
                        "spotify:track:liked-track-5"
                );
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

    private void ensureProfile(String profileId, String name, AgeGroup ageGroup) {
        if (!profileRepository.existsById(profileId)) {
            ChildProfile p = new ChildProfile();
            p.setId(profileId);
            p.setFamilyId(FAMILY_ID);
            p.setName(name);
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(ageGroup);
            profileRepository.save(p);
        }
    }
}
