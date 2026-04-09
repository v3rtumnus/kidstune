package at.kidstune.auth;

import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JwtAuthIntTest {

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

    @Autowired JwtTokenService jwtTokenService;
    @Autowired FamilyRepository familyRepository;
    @Autowired PairedDeviceRepository pairedDeviceRepository;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family family = new Family();
            family.setId(FAMILY_ID);
            family.setEmail("test-" + FAMILY_ID + "@kidstune.test");
            family.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            family.setSpotifyUserId("test-spotify-user-" + FAMILY_ID);
            familyRepository.save(family);
        }
        // Register "dev-kids" so LastSeenFilter allows it through
        if (!pairedDeviceRepository.existsById("dev-kids")) {
            PairedDevice d = new PairedDevice();
            d.setId("dev-kids");
            d.setFamilyId(FAMILY_ID);
            d.setDeviceName("Test Kids Device");
            d.setDeviceType(DeviceType.KIDS);
            d.setDeviceTokenHash("test-hash-dev-kids");
            pairedDeviceRepository.save(d);
        }
    }

    // ── 401 – no token ────────────────────────────────────────────────────────

    @Test
    void profiles_without_token_returns_401() {
        client.get().uri("/api/v1/profiles")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── 200 – valid parent token ──────────────────────────────────────────────

    @Test
    void profiles_with_valid_parent_token_returns_200() {
        String token = jwtTokenService.createDeviceToken(FAMILY_ID, "dev-parent", DeviceType.PARENT);

        client.get().uri("/api/v1/profiles")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // ── 403 – kids token on parent-only endpoint ──────────────────────────────

    @Test
    void profiles_with_kids_token_returns_403() {
        String token = jwtTokenService.createDeviceToken(FAMILY_ID, "dev-kids", DeviceType.KIDS);

        client.get().uri("/api/v1/profiles")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── 200 – public endpoint without token ──────────────────────────────────

    @Test
    void actuator_spotify_without_token_returns_200() {
        client.get().uri("/actuator/spotify")
                .exchange()
                .expectStatus().isOk();
    }
}
