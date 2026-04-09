package at.kidstune.spotify;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.dto.SpotifyItemDto;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for GET /api/v1/spotify/suggestions.
 *
 * Verifies:
 *  - KIDS device token can access the endpoint
 *  - Suggestions are derived from the profile's approved content
 *  - Already-approved URIs are pre-filtered from the response
 *  - Unauthenticated requests are rejected
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpotifySuggestionsIntTest extends AbstractIntTest {

    static MockWebServer mockSpotify;

    static {
        mockSpotify = new MockWebServer();
        try { mockSpotify.start(); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @AfterAll
    static void tearDown() throws IOException { mockSpotify.shutdown(); }

    @DynamicPropertySource
    static void overrideSpotifyUrls(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + mockSpotify.getPort();
        registry.add("spotify.api-base-url",      () -> base);
        registry.add("spotify.accounts-base-url", () -> base);
        registry.add("spotify.redirect-uri",      () -> "http://localhost/callback");
    }

    @MockitoBean SpotifyTokenService spotifyTokenService;

    @Autowired JwtTokenService         jwtTokenService;
    @Autowired FamilyRepository        familyRepository;
    @Autowired ProfileRepository       profileRepository;
    @Autowired ContentRepository       contentRepository;
    @Autowired PairedDeviceRepository  pairedDeviceRepository;
    @Autowired SpotifyWebApiClient     spotifyWebApiClient;

    @LocalServerPort int serverPort;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    WebTestClient client;
    String        kidsToken;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .responseTimeout(java.time.Duration.ofSeconds(10))
                .build();

        kidsToken = jwtTokenService.createDeviceToken(FAMILY_ID, "kids-device-test", DeviceType.KIDS);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("suggestions-" + FAMILY_ID + "@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Test Kind");
            p.setAgeGroup(AgeGroup.SCHOOL);
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            profileRepository.save(p);
        }

        // Register the kids device so LastSeenFilter lets it through
        if (!pairedDeviceRepository.existsById("kids-device-test")) {
            PairedDevice d = new PairedDevice();
            d.setId("kids-device-test");
            d.setFamilyId(FAMILY_ID);
            d.setDeviceName("Test Kids Device");
            d.setDeviceType(DeviceType.KIDS);
            d.setDeviceTokenHash("test-hash-kids-device-suggestions");
            pairedDeviceRepository.save(d);
        }

        when(spotifyTokenService.getValidAccessToken(anyString()))
                .thenReturn(Mono.just("fake-access-token"));

        spotifyWebApiClient.searchCache.invalidateAll();
    }

    @Test
    void suggestions_accessibleWithKidsToken() throws Exception {
        // Profile has Bibi & Tina approved → we search for that artist on Spotify
        addApprovedContent(PROFILE_ID, "spotify:artist:bibi-tina", "Bibi & Tina");
        mockSpotify.enqueue(jsonResponse(searchFixtureWithAlbum("bibi-album-sugg", "Bibi Hörspielbox", "Bibi")));

        List<SpotifyItemDto> results = client.get()
            .uri("/api/v1/spotify/suggestions?profileId=" + PROFILE_ID)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + kidsToken)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SpotifyItemDto.class)
            .returnResult()
            .getResponseBody();

        assertThat(results).isNotNull();
        assertThat(results).isNotEmpty();
    }

    @Test
    void suggestions_preFiltersAlreadyApprovedUris() throws Exception {
        String approvedUri = "spotify:album:already-approved";
        addApprovedContent(PROFILE_ID, approvedUri, "Bibi & Tina");
        // Mock returns the already-approved album AND a new one
        mockSpotify.enqueue(jsonResponse(searchFixtureWithTwoAlbums(
                "already-approved", "Bibi Klassiker",
                "new-album-id",     "Bibi Neuheiten", "Bibi")));

        List<SpotifyItemDto> results = client.get()
            .uri("/api/v1/spotify/suggestions?profileId=" + PROFILE_ID)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + kidsToken)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SpotifyItemDto.class)
            .returnResult()
            .getResponseBody();

        assertThat(results).isNotNull();
        // The already-approved album must not be in suggestions
        assertThat(results).extracting(SpotifyItemDto::spotifyUri)
                .doesNotContain(approvedUri);
        // The new album must appear
        assertThat(results).extracting(SpotifyItemDto::spotifyUri)
                .contains("spotify:album:new-album-id");
    }

    @Test
    void suggestions_requiresAuthentication() {
        client.get()
            .uri("/api/v1/spotify/suggestions?profileId=" + PROFILE_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void suggestions_emptyWhenProfileHasNoContent() {
        // Profile with no approved content and no favourites → empty list
        String emptyProfileId = UUID.randomUUID().toString();

        List<SpotifyItemDto> results = client.get()
            .uri("/api/v1/spotify/suggestions?profileId=" + emptyProfileId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + kidsToken)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(SpotifyItemDto.class)
            .returnResult()
            .getResponseBody();

        assertThat(results).isNotNull().isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addApprovedContent(String profileId, String spotifyUri, String artistName) {
        if (!contentRepository.existsByProfileIdAndSpotifyUri(profileId, spotifyUri)) {
            AllowedContent c = new AllowedContent();
            c.setProfileId(profileId);
            c.setSpotifyUri(spotifyUri);
            c.setTitle(artistName);
            c.setArtistName(artistName);
            c.setContentType(ContentType.AUDIOBOOK);
            c.setScope(ContentScope.ARTIST);
            contentRepository.save(c);
        }
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
            .setBody(body)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    private static String searchFixtureWithAlbum(String albumId, String albumName, String artistName) {
        return """
            {"artists":{"items":[]},"albums":{"items":[
              {"id":"%s","name":"%s","uri":"spotify:album:%s",
               "images":[{"url":"https://example.com/img.jpg","height":300,"width":300}],
               "artists":[{"id":"a1","name":"%s"}],"explicit":false}
            ]},"playlists":{"items":[]}}
            """.formatted(albumId, albumName, albumId, artistName);
    }

    private static String searchFixtureWithTwoAlbums(
            String id1, String name1,
            String id2, String name2,
            String artistName) {
        return """
            {"artists":{"items":[]},"albums":{"items":[
              {"id":"%s","name":"%s","uri":"spotify:album:%s",
               "images":[{"url":"https://example.com/img1.jpg","height":300,"width":300}],
               "artists":[{"id":"a1","name":"%s"}],"explicit":false},
              {"id":"%s","name":"%s","uri":"spotify:album:%s",
               "images":[{"url":"https://example.com/img2.jpg","height":300,"width":300}],
               "artists":[{"id":"a1","name":"%s"}],"explicit":false}
            ]},"playlists":{"items":[]}}
            """.formatted(id1, name1, id1, artistName, id2, name2, id2, artistName);
    }
}
