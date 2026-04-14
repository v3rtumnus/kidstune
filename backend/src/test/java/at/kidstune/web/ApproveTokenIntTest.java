package at.kidstune.web;

import at.kidstune.AbstractIntTest;
import at.kidstune.content.ContentType;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.notification.EmailNotificationService;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestService;
import at.kidstune.requests.ContentRequestStatus;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApproveTokenIntTest extends AbstractIntTest {

    /** Replace JavaMailSender with a mock so no SMTP server is needed. */
    @TestConfiguration
    static class MockMailConfig {
        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }

    @LocalServerPort int port;
    WebTestClient client;

    @Autowired ContentRequestService    requestService;
    @Autowired ContentRequestRepository requestRepository;
    @Autowired ProfileRepository        profileRepository;
    @Autowired FamilyRepository         familyRepository;
    @Autowired PairedDeviceRepository   pairedDeviceRepository;
    @Autowired EmailNotificationService emailService;
    @Autowired JavaMailSender           mailSender;

    private String profileId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        requestRepository.deleteAll();
        pairedDeviceRepository.deleteAll();
        profileRepository.deleteAll();
        familyRepository.deleteAll();
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        Family family = new Family();
        family.setEmail("parent@example.de");
        family.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        family.setNotificationEmails("parent@example.de");
        familyRepository.save(family);

        ChildProfile profile = new ChildProfile();
        profile.setFamilyId(family.getId());
        profile.setName("Luna");
        profile.setAvatarIcon(AvatarIcon.BUNNY);
        profile.setAvatarColor(AvatarColor.PINK);
        profile.setAgeGroup(AgeGroup.PRESCHOOL);
        profileRepository.save(profile);
        profileId = profile.getId();
    }

    // ── GET /web/approve/{token} ──────────────────────────────────────────────

    @Test
    void approve_valid_pending_token_shows_success_page() {
        ContentRequest request = pendingRequest(profileId, "Bibi und Tina Folge 1");
        requestRepository.save(request);
        String token = request.getApproveToken();

        client.get().uri("/web/approve/" + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("Genehmigt");
                    assertThat(body).contains("Luna");
                    assertThat(body).contains("Bibi und Tina Folge 1");
                });

        ContentRequest updated = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ContentRequestStatus.APPROVED);
        assertThat(updated.getApproveToken()).isNull();
    }

    @Test
    void approve_already_approved_shows_handled_page() {
        ContentRequest request = pendingRequest(profileId, "Title");
        request.setStatus(ContentRequestStatus.APPROVED);
        request.setApproveToken("already-done");
        requestRepository.save(request);

        client.get().uri("/web/approve/already-done")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Bereits bearbeitet"));
    }

    @Test
    void approve_expired_token_shows_expired_page() {
        ContentRequest request = pendingRequest(profileId, "Title");
        request.setStatus(ContentRequestStatus.EXPIRED);
        request.setApproveToken("expired-tok");
        requestRepository.save(request);

        client.get().uri("/web/approve/expired-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("abgelaufen"));
    }

    @Test
    void approve_unknown_token_shows_handled_page() {
        client.get().uri("/web/approve/no-such-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Bereits bearbeitet"));
    }

    // ── Email sent on createRequest ───────────────────────────────────────────

    @Test
    void createRequest_fires_notification_email() throws Exception {
        requestService.createRequest(
                profileId, "spotify:album:abc", "Bibi und Tina",
                ContentType.AUDIOBOOK, null, "Bibi und Tina").block();

        // @Async: give the task executor a moment to run
        Thread.sleep(500);

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void createRequest_email_contains_approve_token_url() throws Exception {
        requestService.createRequest(
                profileId, "spotify:album:abc", "Bibi und Tina",
                ContentType.AUDIOBOOK, null, "Bibi und Tina").block();

        Thread.sleep(500);

        ContentRequest saved = requestRepository.findAll().stream()
                .filter(r -> "Bibi und Tina".equals(r.getTitle())).findFirst().orElseThrow();
        assertThat(saved.getApproveToken()).isNotNull();
    }

    // ── Daily digest ──────────────────────────────────────────────────────────

    @Test
    void sendDailyDigest_sends_email_for_old_pending_and_sets_digest_sent_at() throws Exception {
        ContentRequest old = pendingRequest(profileId, "Old Wish");
        old.setRequestedAt(Instant.now().minus(5, ChronoUnit.HOURS));
        requestRepository.save(old);

        ContentRequest recent = pendingRequest(profileId, "Recent Wish");
        recent.setRequestedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        recent.setApproveToken("recent-tok");
        requestRepository.save(recent);

        emailService.sendDailyDigest();

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        assertThat(requestRepository.findById(old.getId()).orElseThrow().getDigestSentAt()).isNotNull();
        assertThat(requestRepository.findById(recent.getId()).orElseThrow().getDigestSentAt()).isNull();
    }

    @Test
    void sendDailyDigest_sends_nothing_when_no_old_pending_requests() {
        ContentRequest recent = pendingRequest(profileId, "Recent Wish");
        recent.setRequestedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        recent.setApproveToken("recent-tok-2");
        requestRepository.save(recent);

        emailService.sendDailyDigest();

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ContentRequest pendingRequest(String profileId, String title) {
        ContentRequest r = new ContentRequest();
        r.setProfileId(profileId);
        r.setSpotifyUri("spotify:album:test");
        r.setTitle(title);
        r.setArtistName("Test Artist");
        r.setContentType(ContentType.MUSIC);
        return r;
    }
}
