package at.kidstune.content;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.content.dto.PinApproveRequest;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PinApproveIntTest extends AbstractIntTest {

    @MockitoBean SpotifyApiClient spotifyApiClient;

    @LocalServerPort int serverPort;

    @Autowired JwtTokenService         jwtTokenService;
    @Autowired FamilyRepository        familyRepository;
    @Autowired ProfileRepository       profileRepository;
    @Autowired ContentRepository       contentRepository;
    @Autowired PairedDeviceRepository  pairedDeviceRepository;
    @Autowired PasswordEncoder         passwordEncoder;

    // Each test uses its own family + profile to avoid rate-limit interference
    WebTestClient client;

    @BeforeEach
    void setUpClient() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + serverPort)
                .build();
    }

    // ── 204 – correct PIN approves content ────────────────────────────────────

    @Test
    void pin_approve_with_correct_pin_returns_204_and_adds_content() {
        TestFixture f = newFixture("1234");

        pinApprove(f, "spotify:track:abc", "My Song", "1234")
                .expectStatus().isNoContent();

        assertThat(contentRepository.existsByProfileIdAndSpotifyUri(
                f.profileId, "spotify:track:abc")).isTrue();
    }

    // ── 403 – wrong PIN ───────────────────────────────────────────────────────

    @Test
    void pin_approve_with_wrong_pin_returns_403() {
        TestFixture f = newFixture("1234");

        pinApprove(f, "spotify:track:def", "Other Song", "9999")
                .expectStatus().isForbidden();

        assertThat(contentRepository.existsByProfileIdAndSpotifyUri(
                f.profileId, "spotify:track:def")).isFalse();
    }

    // ── 403 – no PIN configured ───────────────────────────────────────────────

    @Test
    void pin_approve_when_no_pin_configured_returns_403() {
        TestFixture f = newFixture(null);   // null = no PIN set

        pinApprove(f, "spotify:track:ghi", "No PIN Song", "0000")
                .expectStatus().isForbidden();
    }

    // ── 204 – already approved content is idempotent ─────────────────────────

    @Test
    void pin_approve_already_approved_uri_returns_204() {
        TestFixture f = newFixture("5678");

        // First approval
        pinApprove(f, "spotify:track:dup", "Dup Song", "5678")
                .expectStatus().isNoContent();
        // Second approval of the same URI → should not fail
        pinApprove(f, "spotify:track:dup", "Dup Song", "5678")
                .expectStatus().isNoContent();

        assertThat(contentRepository.findByProfileId(f.profileId)
                .stream().filter(c -> c.getSpotifyUri().equals("spotify:track:dup")).count())
                .isEqualTo(1);
    }

    // ── 429 – locked out after 3 consecutive wrong attempts ───────────────────

    @Test
    void pin_approve_locked_out_after_three_wrong_attempts() {
        TestFixture f = newFixture("4321");

        pinApprove(f, "spotify:track:r1", "Rate1", "0001").expectStatus().isForbidden();
        pinApprove(f, "spotify:track:r2", "Rate2", "0002").expectStatus().isForbidden();
        pinApprove(f, "spotify:track:r3", "Rate3", "0003").expectStatus().isForbidden();
        // 4th attempt (even with correct PIN) → 429
        pinApprove(f, "spotify:track:r4", "Rate4", "4321")
                .expectStatus().isEqualTo(429);
    }

    // ── 404 – wrong family (cross-family isolation) ───────────────────────────

    @Test
    void pin_approve_for_other_family_profile_returns_404() {
        TestFixture f1 = newFixture("1111");
        TestFixture f2 = newFixture("2222");

        // f1's token trying to approve content on f2's profile
        client.post().uri("/api/v1/profiles/{profileId}/content/pin-approve", f2.profileId)
                .header("Authorization", "Bearer " + f1.deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PinApproveRequest(
                        "spotify:track:steal", ContentScope.TRACK, "Steal This",
                        null, null, "1111"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── 401 – no token ────────────────────────────────────────────────────────

    @Test
    void pin_approve_without_token_returns_401() {
        TestFixture f = newFixture("9999");

        client.post().uri("/api/v1/profiles/{profileId}/content/pin-approve", f.profileId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PinApproveRequest(
                        "spotify:track:noauth", ContentScope.TRACK, "No Auth",
                        null, null, "9999"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── 400 – invalid PIN format (not 4 digits) ───────────────────────────────

    @Test
    void pin_approve_with_non_numeric_pin_returns_400() {
        TestFixture f = newFixture("1234");

        client.post().uri("/api/v1/profiles/{profileId}/content/pin-approve", f.profileId)
                .header("Authorization", "Bearer " + f.deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"spotifyUri":"spotify:track:bad","scope":"TRACK","title":"Bad","pin":"abcd"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebTestClient.ResponseSpec pinApprove(
            TestFixture f, String spotifyUri, String title, String pin) {
        return client.post()
                .uri("/api/v1/profiles/{profileId}/content/pin-approve", f.profileId)
                .header("Authorization", "Bearer " + f.deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PinApproveRequest(
                        spotifyUri, ContentScope.TRACK, title, null, null, pin))
                .exchange();
    }

    /**
     * Creates an isolated family + profile + paired device for a single test.
     * Each fixture uses a unique deviceId so parallel tests don't collide on the PK.
     *
     * @param rawPin plaintext PIN to store (BCrypt-hashed), or {@code null} for no PIN.
     */
    private TestFixture newFixture(String rawPin) {
        String familyId  = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        String deviceId  = UUID.randomUUID().toString();

        Family family = new Family();
        family.setId(familyId);
        family.setEmail("pin-test-" + familyId + "@kidstune.test");
        family.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        if (rawPin != null) {
            family.setApprovalPinHash(passwordEncoder.encode(rawPin));
        }
        familyRepository.save(family);

        ChildProfile profile = new ChildProfile();
        profile.setId(profileId);
        profile.setFamilyId(familyId);
        profile.setName("TestKid");
        profile.setAvatarIcon(AvatarIcon.BUNNY);
        profile.setAvatarColor(AvatarColor.BLUE);
        profile.setAgeGroup(AgeGroup.SCHOOL);
        profileRepository.save(profile);

        // Register the device so LastSeenFilter lets it through
        PairedDevice device = new PairedDevice();
        device.setId(deviceId);
        device.setFamilyId(familyId);
        device.setDeviceType(DeviceType.KIDS);
        device.setDeviceTokenHash("test-hash-" + deviceId);
        pairedDeviceRepository.save(device);

        String deviceToken = jwtTokenService.createDeviceToken(familyId, deviceId, DeviceType.KIDS);
        return new TestFixture(familyId, profileId, deviceToken);
    }

    private record TestFixture(String familyId, String profileId, String deviceToken) {}
}
