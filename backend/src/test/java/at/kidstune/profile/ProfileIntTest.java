package at.kidstune.profile;

import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.dto.ProfileRequest;
import at.kidstune.profile.dto.ProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProfileIntTest {

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

    static final String FAMILY_ID = UUID.randomUUID().toString();

    @LocalServerPort int serverPort;
    WebTestClient client;

    @Autowired FamilyRepository familyRepository;
    @Autowired ProfileRepository profileRepository;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family family = new Family();
            family.setId(FAMILY_ID);
            family.setSpotifyUserId("test-spotify-user-" + FAMILY_ID);
            familyRepository.save(family);
        }

        profileRepository.deleteAll(profileRepository.findByFamilyId(FAMILY_ID));
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Test
    void post_creates_profile_and_returns_201() {
        ProfileRequest body = new ProfileRequest("Lena", AvatarIcon.FOX, AvatarColor.PURPLE, AgeGroup.PRESCHOOL);

        ProfileResponse response = client.post().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProfileResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotBlank();
        assertThat(response.name()).isEqualTo("Lena");
        assertThat(response.avatarIcon()).isEqualTo(AvatarIcon.FOX);
        assertThat(response.ageGroup()).isEqualTo(AgeGroup.PRESCHOOL);
        assertThat(profileRepository.findById(response.id())).isPresent();
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void get_returns_profiles_for_family() {
        client.post().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ProfileRequest("Tobias", AvatarIcon.BEAR, AvatarColor.BLUE, AgeGroup.SCHOOL))
                .exchange().expectStatus().isCreated();

        client.get().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProfileResponse.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).name()).isEqualTo("Tobias"));
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Test
    void put_updates_profile_and_returns_200() {
        ProfileResponse created = client.post().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ProfileRequest("OriginalName", AvatarIcon.OWL, AvatarColor.GREEN, AgeGroup.TODDLER))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProfileResponse.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();

        ProfileRequest update = new ProfileRequest("UpdatedName", AvatarIcon.CAT, AvatarColor.PINK, AgeGroup.SCHOOL);
        ProfileResponse updated = client.put().uri("/api/v1/profiles/{id}", created.id())
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProfileResponse.class)
                .returnResult().getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.name()).isEqualTo("UpdatedName");
        assertThat(updated.avatarIcon()).isEqualTo(AvatarIcon.CAT);

        ChildProfile inDb = profileRepository.findById(created.id()).orElseThrow();
        assertThat(inDb.getName()).isEqualTo("UpdatedName");
        assertThat(inDb.getAvatarColor()).isEqualTo(AvatarColor.PINK);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_profile_and_returns_204() {
        ProfileResponse created = client.post().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ProfileRequest("ToDelete", AvatarIcon.BUNNY, AvatarColor.ORANGE, AgeGroup.TODDLER))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProfileResponse.class)
                .returnResult().getResponseBody();

        assertThat(created).isNotNull();

        client.delete().uri("/api/v1/profiles/{id}", created.id())
                .header("X-Family-Id", FAMILY_ID)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(profileRepository.findById(created.id())).isEmpty();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void post_with_blank_name_returns_400_validation_error() {
        client.post().uri("/api/v1/profiles")
                .header("X-Family-Id", FAMILY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "", "avatarIcon", "FOX", "avatarColor", "PURPLE", "ageGroup", "PRESCHOOL"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void post_without_family_id_header_returns_400() {
        client.post().uri("/api/v1/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ProfileRequest("Lena", AvatarIcon.FOX, AvatarColor.PURPLE, AgeGroup.PRESCHOOL))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MISSING_FAMILY_ID");
    }
}
