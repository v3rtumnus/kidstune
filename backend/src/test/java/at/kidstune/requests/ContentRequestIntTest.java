package at.kidstune.requests;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentType;
import at.kidstune.content.SpotifyApiClient;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.dto.ApproveRequestBody;
import at.kidstune.requests.dto.BulkApproveRequest;
import at.kidstune.requests.dto.BulkRejectRequest;
import at.kidstune.requests.dto.ContentRequestResponse;
import at.kidstune.requests.dto.CreateContentRequestDto;
import at.kidstune.requests.dto.PendingCountResponse;
import at.kidstune.requests.dto.RejectRequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContentRequestIntTest {

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
    @MockitoBean JavaMailSender   mailSender; // prevents SMTP errors in @Async notification

    @LocalServerPort int port;
    WebTestClient client;
    String parentToken;
    String kidsToken;

    @Autowired JwtTokenService           jwtTokenService;
    @Autowired FamilyRepository          familyRepository;
    @Autowired ProfileRepository         profileRepository;
    @Autowired ContentRepository         contentRepository;
    @Autowired ContentRequestRepository  requestRepository;
    @Autowired ContentRequestService     requestService;
    @Autowired PairedDeviceRepository    pairedDeviceRepository;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        requestRepository.deleteAll();
        contentRepository.deleteAll(contentRepository.findByProfileId(PROFILE_ID));

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("requests-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setNotificationEmails("requests-test@kidstune.test");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Luna");
            p.setAvatarIcon(AvatarIcon.BUNNY);
            p.setAvatarColor(AvatarColor.PINK);
            p.setAgeGroup(AgeGroup.PRESCHOOL);
            profileRepository.save(p);
        }

        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "parent-device", DeviceType.PARENT);
        kidsToken   = jwtTokenService.createDeviceToken(FAMILY_ID, "kids-device",   DeviceType.KIDS);

        // LastSeenFilter verifies KIDS devices exist in paired_device — register it
        if (!pairedDeviceRepository.existsById("kids-device")) {
            PairedDevice device = new PairedDevice();
            device.setId("kids-device");
            device.setFamilyId(FAMILY_ID);
            device.setProfileId(PROFILE_ID);
            device.setDeviceType(DeviceType.KIDS);
            device.setDeviceName("Test Kids Device");
            device.setDeviceTokenHash("test-hash-kids");
            pairedDeviceRepository.save(device);
        }

        // Stub Spotify API to avoid NPE in resolver
        when(spotifyApiClient.getAlbumUriForTrack(any())).thenReturn(Mono.just("spotify:album:noop"));
        when(spotifyApiClient.getArtistUrisForTrack(any())).thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(any())).thenReturn(Mono.just(List.of()));
    }

    // ── Create request ────────────────────────────────────────────────────────

    @Test
    void createRequest_returns201_and_stores_PENDING() {
        ContentRequestResponse response = postCreateRequest(kidsToken, PROFILE_ID,
                new CreateContentRequestDto("spotify:album:abc", "Bibi und Tina",
                        ContentType.AUDIOBOOK, null, "Bibi"))
                .expectStatus().isCreated()
                .expectBody(ContentRequestResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotBlank();
        assertThat(response.status()).isEqualTo(ContentRequestStatus.PENDING);
        assertThat(response.profileId()).isEqualTo(PROFILE_ID);
        assertThat(response.spotifyUri()).isEqualTo("spotify:album:abc");

        assertThat(requestRepository.findById(response.id())).isPresent()
                .get().extracting(ContentRequest::getStatus)
                .isEqualTo(ContentRequestStatus.PENDING);
    }

    @Test
    void createRequest_sets_approve_token_for_email_notification() {
        // The approve_token is generated in @PrePersist and embedded in the notification email.
        // Verify it is present after request creation (proves the email mechanism is prepared).
        // Full end-to-end email dispatch is verified in ApproveTokenIntTest.
        ContentRequest saved = requestService.createRequest(
                PROFILE_ID, "spotify:album:notify",
                "Bibi und Tina", ContentType.AUDIOBOOK, null, "Bibi").block();

        assertThat(saved).isNotNull();
        assertThat(saved.getApproveToken()).isNotBlank();

        ContentRequest fromDb = requestRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getApproveToken()).isNotBlank();
    }

    @Test
    void createRequest_4th_pending_returns_429() {
        for (int i = 1; i <= 3; i++) {
            postCreateRequest(kidsToken, PROFILE_ID,
                    new CreateContentRequestDto("spotify:album:req" + i, "Song " + i,
                            ContentType.MUSIC, null, "Artist"))
                    .expectStatus().isCreated();
        }

        postCreateRequest(kidsToken, PROFILE_ID,
                new CreateContentRequestDto("spotify:album:req4", "Song 4",
                        ContentType.MUSIC, null, "Artist"))
                .expectStatus().isEqualTo(429);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_creates_AllowedContent_and_sets_APPROVED() {
        ContentRequest request = savedPendingRequest("spotify:album:approve-me", "Pumuckl");

        client.post()
                .uri("/api/v1/content-requests/" + request.getId() + "/approve")
                .headers(h -> h.setBearerAuth(parentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApproveRequestBody(null, null, null))
                .exchange()
                .expectStatus().isNoContent();

        ContentRequest updated = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ContentRequestStatus.APPROVED);
        assertThat(updated.getResolvedBy()).isEqualTo(FAMILY_ID);

        List<AllowedContent> allowed = contentRepository.findByProfileId(PROFILE_ID);
        assertThat(allowed).anyMatch(c -> c.getSpotifyUri().equals("spotify:album:approve-me"));
    }

    @Test
    void approve_with_contentTypeOverride_stores_overridden_type() {
        ContentRequest request = savedPendingRequest("spotify:album:reclassify", "Rock Album");

        client.post()
                .uri("/api/v1/content-requests/" + request.getId() + "/approve")
                .headers(h -> h.setBearerAuth(parentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApproveRequestBody(null, null, ContentType.AUDIOBOOK))
                .exchange()
                .expectStatus().isNoContent();

        AllowedContent content = contentRepository.findByProfileId(PROFILE_ID).stream()
                .filter(c -> c.getSpotifyUri().equals("spotify:album:reclassify"))
                .findFirst().orElseThrow();
        assertThat(content.getContentType()).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_with_note_stores_REJECTED_and_parent_note() {
        ContentRequest request = savedPendingRequest("spotify:album:rejected", "Bad Song");

        client.post()
                .uri("/api/v1/content-requests/" + request.getId() + "/reject")
                .headers(h -> h.setBearerAuth(parentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RejectRequestBody("Not suitable for your age."))
                .exchange()
                .expectStatus().isNoContent();

        ContentRequest updated = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ContentRequestStatus.REJECTED);
        assertThat(updated.getParentNote()).isEqualTo("Not suitable for your age.");
        assertThat(updated.getResolvedAt()).isNotNull();
    }

    // ── Expire stale requests ─────────────────────────────────────────────────

    @Test
    void expireStaleRequests_sets_EXPIRED_for_requests_older_than_7_days() {
        ContentRequest old = savedPendingRequest("spotify:album:old", "Old Song");
        old.setRequestedAt(Instant.now().minus(8, ChronoUnit.DAYS));
        requestRepository.save(old);

        ContentRequest recent = savedPendingRequest("spotify:album:recent", "Recent Song");

        requestService.expireStaleRequests();

        assertThat(requestRepository.findById(old.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.EXPIRED);
        assertThat(requestRepository.findById(recent.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.PENDING);
    }

    // ── Bulk approve ──────────────────────────────────────────────────────────

    @Test
    void bulkApprove_approves_all_3_requests() {
        ContentRequest r1 = savedPendingRequest("spotify:album:bulk1", "Bulk 1");
        ContentRequest r2 = savedPendingRequest("spotify:album:bulk2", "Bulk 2");
        ContentRequest r3 = savedPendingRequest("spotify:album:bulk3", "Bulk 3");

        client.post()
                .uri("/api/v1/content-requests/bulk-approve")
                .headers(h -> h.setBearerAuth(parentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BulkApproveRequest(
                        List.of(r1.getId(), r2.getId(), r3.getId()), null, null))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(requestRepository.findById(r1.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.APPROVED);
        assertThat(requestRepository.findById(r2.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.APPROVED);
        assertThat(requestRepository.findById(r3.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.APPROVED);
    }

    // ── Bulk reject ───────────────────────────────────────────────────────────

    @Test
    void bulkReject_rejects_all_with_note() {
        ContentRequest r1 = savedPendingRequest("spotify:album:br1", "Bulk Reject 1");
        ContentRequest r2 = savedPendingRequest("spotify:album:br2", "Bulk Reject 2");

        client.post()
                .uri("/api/v1/content-requests/bulk-reject")
                .headers(h -> h.setBearerAuth(parentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BulkRejectRequest(List.of(r1.getId(), r2.getId()), "Not now."))
                .exchange()
                .expectStatus().isNoContent();

        ContentRequest updated1 = requestRepository.findById(r1.getId()).orElseThrow();
        assertThat(updated1.getStatus()).isEqualTo(ContentRequestStatus.REJECTED);
        assertThat(updated1.getParentNote()).isEqualTo("Not now.");
        assertThat(requestRepository.findById(r2.getId()).orElseThrow().getStatus())
                .isEqualTo(ContentRequestStatus.REJECTED);
    }

    // ── List requests ─────────────────────────────────────────────────────────

    @Test
    void listRequests_filtered_by_status_returns_only_matching() {
        ContentRequest pending  = savedPendingRequest("spotify:album:p1", "Pending Song");
        ContentRequest approved = savedPendingRequest("spotify:album:a1", "Approved Song");
        approved.setStatus(ContentRequestStatus.APPROVED);
        approved.setResolvedAt(Instant.now());
        requestRepository.save(approved);

        List<ContentRequestResponse> result = client.get()
                .uri("/api/v1/content-requests?status=PENDING")
                .headers(h -> h.setBearerAuth(parentToken))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ContentRequestResponse.class)
                .returnResult().getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result).allMatch(r -> r.status() == ContentRequestStatus.PENDING);
        assertThat(result).anyMatch(r -> r.id().equals(pending.getId()));
        assertThat(result).noneMatch(r -> r.id().equals(approved.getId()));
    }

    // ── Pending count ─────────────────────────────────────────────────────────

    @Test
    void getPendingCount_returns_count_per_profile_and_total() {
        savedPendingRequest("spotify:album:pc1", "Song A");
        savedPendingRequest("spotify:album:pc2", "Song B");

        PendingCountResponse result = client.get()
                .uri("/api/v1/content-requests/pending-count")
                .headers(h -> h.setBearerAuth(parentToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PendingCountResponse.class)
                .returnResult().getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.total()).isGreaterThanOrEqualTo(2);
        assertThat(result.profiles())
                .anyMatch(p -> p.id().equals(PROFILE_ID) && p.count() >= 2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebTestClient.ResponseSpec postCreateRequest(
            String token, String profileId, CreateContentRequestDto body) {
        return client.post()
                .uri("/api/v1/profiles/" + profileId + "/content-requests")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private ContentRequest savedPendingRequest(String spotifyUri, String title) {
        ContentRequest r = new ContentRequest();
        r.setProfileId(PROFILE_ID);
        r.setSpotifyUri(spotifyUri);
        r.setTitle(title);
        r.setArtistName("Test Artist");
        r.setContentType(ContentType.MUSIC);
        return requestRepository.save(r);
    }
}
