package at.kidstune.favorites;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.SpotifyApiClient;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.dto.AddFavoriteRequest;
import at.kidstune.favorites.dto.FavoriteResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FavoritesIntTest extends AbstractIntTest {

    @MockitoBean SpotifyApiClient spotifyApiClient;

    // SpotifyFavoriteSyncService makes real HTTP calls – mock the token service
    // so it reports "no Spotify account" for the test profile.
    // (The actual Spotify HTTP call is already guarded by isProfileSpotifyLinked.)
    @MockitoBean SpotifyTokenService spotifyTokenService;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    @LocalServerPort int serverPort;
    WebTestClient client;
    String kidsToken;
    String parentToken;

    @Autowired JwtTokenService          jwtTokenService;
    @Autowired FamilyRepository         familyRepo;
    @Autowired ProfileRepository        profileRepo;
    @Autowired FavoriteRepository       favoriteRepo;
    @Autowired PairedDeviceRepository   pairedDeviceRepo;

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
            f.setEmail("fav-test-" + FAMILY_ID + "@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            familyRepo.save(f);
        }
        if (!profileRepo.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Emma");
            p.setAvatarIcon(AvatarIcon.FOX);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            profileRepo.save(p);
        }

        favoriteRepo.deleteAll(favoriteRepo.findByProfileId(PROFILE_ID));

        // Mock SpotifyTokenService: no linked Spotify account → mirrorAdd is a no-op
        when(spotifyTokenService.isProfileSpotifyLinked(anyString())).thenReturn(false);
        when(spotifyTokenService.getValidAccessToken(anyString())).thenReturn(Mono.just("mock-token"));
        when(spotifyTokenService.getValidProfileAccessToken(anyString())).thenReturn(Mono.just("mock-token"));

        when(spotifyApiClient.getAlbumUriForTrack(anyString())).thenReturn(Mono.just("spotify:album:x"));
        when(spotifyApiClient.getArtistUrisForTrack(anyString())).thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(anyString())).thenReturn(Mono.just(List.of()));

        // Register the test kids device so LastSeenFilter allows it through
        if (!pairedDeviceRepo.existsById("kids-device")) {
            PairedDevice d = new PairedDevice();
            d.setId("kids-device");
            d.setFamilyId(FAMILY_ID);
            d.setDeviceName("Test Kids Device");
            d.setDeviceType(DeviceType.KIDS);
            d.setDeviceTokenHash("test-hash-kids-device-fav");
            pairedDeviceRepo.save(d);
        }
    }

    @Test
    void list_returns_empty_for_profile_with_no_favorites() {
        client.get()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FavoriteResponse.class)
                .hasSize(0);
    }

    @Test
    void add_favorite_returns_201_and_persists() {
        var req = new AddFavoriteRequest(
                "spotify:track:abc123",
                "Bibi & Tina – Folge 1",
                null,
                "Bibi & Tina");

        FavoriteResponse resp = client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FavoriteResponse.class)
                .returnResult().getResponseBody();

        assertThat(resp).isNotNull();
        assertThat(resp.spotifyTrackUri()).isEqualTo("spotify:track:abc123");
        assertThat(resp.trackTitle()).isEqualTo("Bibi & Tina – Folge 1");
        assertThat(resp.profileId()).isEqualTo(PROFILE_ID);

        // Verify it's in the list
        client.get()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectBodyList(FavoriteResponse.class)
                .hasSize(1);
    }

    @Test
    void add_favorite_is_idempotent() {
        var req = new AddFavoriteRequest("spotify:track:dupe", "Dup Track", null, null);

        client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        // Second add must not fail or create a duplicate
        client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        client.get()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectBodyList(FavoriteResponse.class)
                .hasSize(1);
    }

    @Test
    void delete_favorite_returns_204_and_removes() {
        // Add first
        var req = new AddFavoriteRequest("spotify:track:del123", "Delete Me", null, null);
        client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        // Delete – the colon in the URI requires encoding in the path
        client.delete()
                .uri("/api/v1/profiles/{pid}/favorites/{uri}", PROFILE_ID,
                        "spotify:track:del123")
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectStatus().isNoContent();

        // List should be empty
        client.get()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .exchange()
                .expectBodyList(FavoriteResponse.class)
                .hasSize(0);
    }

    @Test
    void add_favorite_without_token_returns_401() {
        var req = new AddFavoriteRequest("spotify:track:x", "X", null, null);
        client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void add_favorite_with_non_track_uri_returns_400() {
        var req = new AddFavoriteRequest("spotify:album:xyz", "Album", null, null);
        client.post()
                .uri("/api/v1/profiles/{pid}/favorites", PROFILE_ID)
                .header("Authorization", "Bearer " + kidsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
