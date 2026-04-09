package at.kidstune.auth;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.dto.ConfirmPairingResponse;
import at.kidstune.auth.dto.PairingCodeResponse;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.device.dto.DeviceResponse;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevicePairingIntTest extends AbstractIntTest {

    static final String FAMILY_ID = UUID.randomUUID().toString();

    @LocalServerPort int serverPort;
    WebTestClient client;

    @Autowired FamilyRepository familyRepository;
    @Autowired PairedDeviceRepository pairedDeviceRepository;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired PairingCodeRepository pairingCodeRepository;

    String parentToken;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family family = new Family();
            family.setId(FAMILY_ID);
            family.setEmail("pairing-" + FAMILY_ID + "@kidstune.test");
            family.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            familyRepository.save(family);
        }

        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "parent-device", DeviceType.PARENT);
    }

    // ── generate code ─────────────────────────────────────────────────────────

    @Test
    void generateCode_returns201With6DigitCode() {
        PairingCodeResponse response = client.post().uri("/api/v1/auth/pair")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PairingCodeResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.code()).matches("\\d{6}");
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void generateCode_withoutAuth_returns401() {
        client.post().uri("/api/v1/auth/pair")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── generate and confirm ──────────────────────────────────────────────────

    @Test
    void confirmPairing_validCode_returnsDeviceToken() {
        PairingCodeResponse codeResponse = client.post().uri("/api/v1/auth/pair")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PairingCodeResponse.class)
                .returnResult().getResponseBody();

        assertThat(codeResponse).isNotNull();

        ConfirmPairingResponse confirmResponse = client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\": \"" + codeResponse.code() + "\", \"deviceName\": \"My Kids Tablet\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ConfirmPairingResponse.class)
                .returnResult().getResponseBody();

        assertThat(confirmResponse).isNotNull();
        assertThat(confirmResponse.deviceToken()).isNotBlank();
        assertThat(confirmResponse.familyId()).isEqualTo(FAMILY_ID);

        JwtClaims claims = jwtTokenService.validateToken(confirmResponse.deviceToken());
        assertThat(claims.familyId()).isEqualTo(FAMILY_ID);
        assertThat(claims.deviceType()).isEqualTo(DeviceType.KIDS);
    }

    @Test
    void confirmPairing_wrongCode_returns410() {
        client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\": \"999999\", \"deviceName\": \"Device\"}")
                .exchange()
                .expectStatus().isEqualTo(410);
    }

    @Test
    void confirmPairing_codeIsSingleUse_secondAttemptFails() {
        PairingCodeResponse codeResponse = client.post().uri("/api/v1/auth/pair")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PairingCodeResponse.class)
                .returnResult().getResponseBody();

        assertThat(codeResponse).isNotNull();
        String body = "{\"code\": \"" + codeResponse.code() + "\", \"deviceName\": \"My Device\"}";

        // First confirm succeeds
        client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk();

        // Second confirm with same code fails
        client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(410);
    }

    @Test
    void confirmPairing_expiredCode_returns410() {
        PairingCode expired = new PairingCode();
        expired.setFamilyId(FAMILY_ID);
        expired.setCode("000001");
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        pairingCodeRepository.save(expired);

        client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\": \"000001\", \"deviceName\": \"Device\"}")
                .exchange()
                .expectStatus().isEqualTo(410);
    }

    // ── device listing ────────────────────────────────────────────────────────

    @Test
    void listDevices_showsNewlyPairedDevice() {
        PairingCodeResponse codeResponse = client.post().uri("/api/v1/auth/pair")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PairingCodeResponse.class)
                .returnResult().getResponseBody();

        assertThat(codeResponse).isNotNull();

        client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\": \"" + codeResponse.code() + "\", \"deviceName\": \"Listed Device\"}")
                .exchange()
                .expectStatus().isOk();

        List<DeviceResponse> devices = client.get().uri("/api/v1/devices")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeviceResponse.class)
                .returnResult().getResponseBody();

        assertThat(devices).isNotNull();
        assertThat(devices).anyMatch(d ->
                "Listed Device".equals(d.deviceName()) && FAMILY_ID.equals(d.familyId()));
    }

    // ── unpair revokes token ──────────────────────────────────────────────────

    @Test
    void unpairDevice_tokenNoLongerWorks() {
        PairingCodeResponse codeResponse = client.post().uri("/api/v1/auth/pair")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PairingCodeResponse.class)
                .returnResult().getResponseBody();

        assertThat(codeResponse).isNotNull();

        ConfirmPairingResponse confirmResponse = client.post().uri("/api/v1/auth/pair/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\": \"" + codeResponse.code() + "\", \"deviceName\": \"Revokable Device\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ConfirmPairingResponse.class)
                .returnResult().getResponseBody();

        assertThat(confirmResponse).isNotNull();
        String deviceToken = confirmResponse.deviceToken();

        List<DeviceResponse> devices = client.get().uri("/api/v1/devices")
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeviceResponse.class)
                .returnResult().getResponseBody();

        assertThat(devices).isNotNull();
        String deviceId = devices.stream()
                .filter(d -> "Revokable Device".equals(d.deviceName()))
                .findFirst()
                .map(DeviceResponse::id)
                .orElseThrow();

        // Unpair
        client.delete().uri("/api/v1/devices/" + deviceId)
                .header("Authorization", "Bearer " + parentToken)
                .exchange()
                .expectStatus().isNoContent();

        // Device token should now return 401
        client.get().uri("/api/v1/profiles")
                .header("Authorization", "Bearer " + deviceToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
