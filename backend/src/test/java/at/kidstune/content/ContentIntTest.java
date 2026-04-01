package at.kidstune.content;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.BulkAddContentRequest;
import at.kidstune.content.dto.ContentCheckResponse;
import at.kidstune.content.dto.ContentResponse;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContentIntTest {

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

    @MockitoBean SpotifyApiClient spotifyApiClient;

    static final String FAMILY_ID   = UUID.randomUUID().toString();
    static final String PROFILE_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID2 = UUID.randomUUID().toString();

    @LocalServerPort int serverPort;
    WebTestClient client;
    String parentToken;

    @Autowired JwtTokenService   jwtTokenService;
    @Autowired FamilyRepository  familyRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired ContentRepository contentRepository;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();

        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "test-device", DeviceType.PARENT);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("test-" + FAMILY_ID + "@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setSpotifyUserId("spotify-user-" + FAMILY_ID);
            familyRepository.save(f);
        }
        ensureProfile(PROFILE_ID,  "Lena");
        ensureProfile(PROFILE_ID2, "Tobias");

        contentRepository.deleteAll(contentRepository.findByProfileId(PROFILE_ID));
        contentRepository.deleteAll(contentRepository.findByProfileId(PROFILE_ID2));

        // Default stubs – no Spotify matches → checkContent defaults to denied
        when(spotifyApiClient.getAlbumUriForTrack(anyString()))
                .thenReturn(Mono.just("spotify:album:no-match"));
        when(spotifyApiClient.getArtistUrisForTrack(anyString()))
                .thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(anyString()))
                .thenReturn(Mono.just(List.of()));
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void post_creates_content_and_returns_201() {
        ContentResponse response = postContent(PROFILE_ID, trackRequest("spotify:track:abc", "My Song"))
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotBlank();
        assertThat(response.spotifyUri()).isEqualTo("spotify:track:abc");
        assertThat(response.scope()).isEqualTo(ContentScope.TRACK);
        assertThat(response.contentType()).isEqualTo(ContentType.MUSIC);
        assertThat(response.profileId()).isEqualTo(PROFILE_ID);
        assertThat(contentRepository.findById(response.id())).isPresent();
    }

    @Test
    void post_with_content_type_override_persists_override() {
        AddContentRequest body = new AddContentRequest(
                "spotify:album:book1", ContentScope.ALBUM, "My Audiobook",
                null, "Author Name", ContentType.AUDIOBOOK, null);

        ContentResponse response = postContent(PROFILE_ID, body)
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.contentType()).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Classification ────────────────────────────────────────────────────────

    @Test
    void post_without_override_classifies_hoerspiel_genre_as_audiobook() {
        SpotifyItemInfo itemInfo = new SpotifyItemInfo(
                "album", List.of("hörspiel"), "Bibi Blocksberg Folge 1", 5, 120_000L);
        AddContentRequest body = new AddContentRequest(
                "spotify:album:hoerspiel1", ContentScope.ALBUM, "Bibi Blocksberg",
                null, null, null, itemInfo);

        ContentResponse response = postContent(PROFILE_ID, body)
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.contentType()).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test
    void post_with_override_ignores_heuristic() {
        // hörspiel genre would classify as AUDIOBOOK, but override forces MUSIC
        SpotifyItemInfo itemInfo = new SpotifyItemInfo(
                "album", List.of("hörspiel"), null, 5, 120_000L);
        AddContentRequest body = new AddContentRequest(
                "spotify:album:override1", ContentScope.ALBUM, "Override Test",
                null, null, ContentType.MUSIC, itemInfo);

        ContentResponse response = postContent(PROFILE_ID, body)
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.contentType()).isEqualTo(ContentType.MUSIC);
    }

    @Test
    void post_without_override_and_no_item_info_defaults_to_music() {
        ContentResponse response = postContent(PROFILE_ID, trackRequest("spotify:track:noinfo", "Unknown"))
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.contentType()).isEqualTo(ContentType.MUSIC);
    }

    // ── Duplicate prevention ──────────────────────────────────────────────────

    @Test
    void post_duplicate_uri_for_same_profile_returns_409() {
        AddContentRequest body = trackRequest("spotify:track:dup", "Duplicate Song");

        postContent(PROFILE_ID, body).expectStatus().isCreated();
        postContent(PROFILE_ID, body)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_CONTENT");
    }

    @Test
    void post_same_uri_for_different_profile_succeeds() {
        AddContentRequest body = trackRequest("spotify:track:shared", "Shared Song");

        postContent(PROFILE_ID, body).expectStatus().isCreated();
        postContent(PROFILE_ID2, body).expectStatus().isCreated();
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void get_returns_all_content_for_profile() {
        postContent(PROFILE_ID, trackRequest("spotify:track:t1", "Track 1")).expectStatus().isCreated();
        postContent(PROFILE_ID, trackRequest("spotify:track:t2", "Track 2")).expectStatus().isCreated();

        client.get().uri("/api/v1/profiles/{id}/content", PROFILE_ID)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ContentResponse.class)
                .hasSize(2);
    }

    @Test
    void get_with_type_filter_returns_matching_content() {
        postContent(PROFILE_ID, new AddContentRequest(
                "spotify:album:audio", ContentScope.ALBUM, "Audiobook",
                null, null, ContentType.AUDIOBOOK, null)).expectStatus().isCreated();
        postContent(PROFILE_ID, trackRequest("spotify:track:music", "Music")).expectStatus().isCreated();

        client.get().uri("/api/v1/profiles/{id}/content?type=AUDIOBOOK", PROFILE_ID)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ContentResponse.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).spotifyUri()).isEqualTo("spotify:album:audio"));
    }

    @Test
    void get_with_scope_filter_returns_matching_content() {
        postContent(PROFILE_ID, trackRequest("spotify:track:t1", "Track")).expectStatus().isCreated();
        postContent(PROFILE_ID, new AddContentRequest(
                "spotify:album:a1", ContentScope.ALBUM, "Album",
                null, null, null, null)).expectStatus().isCreated();

        client.get().uri("/api/v1/profiles/{id}/content?scope=ALBUM", PROFILE_ID)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ContentResponse.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).scope()).isEqualTo(ContentScope.ALBUM));
    }

    @Test
    void get_with_search_filter_returns_matching_content() {
        postContent(PROFILE_ID, trackRequest("spotify:track:t1", "Bibi und Tina")).expectStatus().isCreated();
        postContent(PROFILE_ID, trackRequest("spotify:track:t2", "Peppa Pig")).expectStatus().isCreated();

        client.get().uri("/api/v1/profiles/{id}/content?search=bibi", PROFILE_ID)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ContentResponse.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).title()).isEqualTo("Bibi und Tina"));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_content_and_returns_204() {
        ContentResponse created = postContent(PROFILE_ID, trackRequest("spotify:track:del", "Delete Me"))
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();

        client.delete().uri("/api/v1/profiles/{profileId}/content/{id}", PROFILE_ID, created.id())
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(contentRepository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_wrong_profile_returns_404() {
        ContentResponse created = postContent(PROFILE_ID, trackRequest("spotify:track:xyz", "Song"))
                .expectStatus().isCreated()
                .expectBody(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();

        client.delete().uri("/api/v1/profiles/{profileId}/content/{id}", PROFILE_ID2, created.id())
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── Bulk add ──────────────────────────────────────────────────────────────

    @Test
    void bulk_add_creates_one_row_per_profile() {
        BulkAddContentRequest bulk = new BulkAddContentRequest(
                "spotify:artist:bibi", ContentScope.ARTIST, "Bibi und Tina",
                null, null, null, null, List.of(PROFILE_ID, PROFILE_ID2));

        List<ContentResponse> responses = client.post().uri("/api/v1/content/bulk")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bulk)
                .exchange()
                .expectStatus().isCreated()
                .expectBodyList(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ContentResponse::profileId)
                .containsExactlyInAnyOrder(PROFILE_ID, PROFILE_ID2);
        assertThat(contentRepository.findByProfileId(PROFILE_ID)).hasSize(1);
        assertThat(contentRepository.findByProfileId(PROFILE_ID2)).hasSize(1);
    }

    @Test
    void bulk_add_skips_duplicate_profiles_silently() {
        postContent(PROFILE_ID, new AddContentRequest(
                "spotify:artist:bibi", ContentScope.ARTIST, "Bibi und Tina",
                null, null, null, null)).expectStatus().isCreated();

        BulkAddContentRequest bulk = new BulkAddContentRequest(
                "spotify:artist:bibi", ContentScope.ARTIST, "Bibi und Tina",
                null, null, null, null, List.of(PROFILE_ID, PROFILE_ID2));

        List<ContentResponse> responses = client.post().uri("/api/v1/content/bulk")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bulk)
                .exchange()
                .expectStatus().isCreated()
                .expectBodyList(ContentResponse.class)
                .returnResult().getResponseBody();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).profileId()).isEqualTo(PROFILE_ID2);
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    @Test
    void check_direct_track_match_returns_allowed() {
        postContent(PROFILE_ID, trackRequest("spotify:track:allowed", "Allowed Track"))
                .expectStatus().isCreated();

        ContentCheckResponse check = client.get()
                .uri("/api/v1/profiles/{profileId}/content/check/{uri}",
                        PROFILE_ID, "spotify:track:allowed")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ContentCheckResponse.class)
                .returnResult().getResponseBody();

        assertThat(check).isNotNull();
        assertThat(check.allowed()).isTrue();
        assertThat(check.reason()).isEqualTo("TRACK_MATCH");
    }

    @Test
    void check_unlisted_track_returns_denied() {
        ContentCheckResponse check = client.get()
                .uri("/api/v1/profiles/{profileId}/content/check/{uri}",
                        PROFILE_ID, "spotify:track:unknown")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ContentCheckResponse.class)
                .returnResult().getResponseBody();

        assertThat(check).isNotNull();
        assertThat(check.allowed()).isFalse();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    void get_content_without_token_returns_401() {
        client.get().uri("/api/v1/profiles/{id}/content", PROFILE_ID)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebTestClient.ResponseSpec postContent(String profileId, AddContentRequest body) {
        return client.post().uri("/api/v1/profiles/{id}/content", profileId)
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private AddContentRequest trackRequest(String uri, String title) {
        return new AddContentRequest(uri, ContentScope.TRACK, title, null, null, null, null);
    }

    private void ensureProfile(String profileId, String name) {
        if (!profileRepository.existsById(profileId)) {
            ChildProfile p = new ChildProfile();
            p.setId(profileId);
            p.setFamilyId(FAMILY_ID);
            p.setName(name);
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            profileRepository.save(p);
        }
    }
}