package at.kidstune.requests;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.content.ContentRepository;
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
import at.kidstune.requests.dto.CreateContentRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2 + T3 — Concurrency tests for the request-limit check and the approve-token
 * single-use guarantee, both protected by pessimistic DB locks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContentRequestConcurrencyIntTest extends AbstractIntTest {

    @MockitoBean JavaMailSender mailSender;

    @LocalServerPort int port;
    WebTestClient client;

    @Autowired JwtTokenService          jwtTokenService;
    @Autowired FamilyRepository         familyRepository;
    @Autowired ProfileRepository        profileRepository;
    @Autowired ContentRepository        contentRepository;
    @Autowired ContentRequestRepository requestRepository;
    @Autowired PairedDeviceRepository   pairedDeviceRepository;
    @Autowired ContentRequestService    requestService;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    String kidsToken;
    String parentToken;

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
            f.setEmail("concurrency-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            f.setNotificationEmails("concurrency-test@kidstune.test");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("TestKid");
            p.setAvatarIcon(AvatarIcon.BUNNY);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.PRESCHOOL);
            profileRepository.save(p);
        }

        kidsToken   = jwtTokenService.createDeviceToken(FAMILY_ID, "conc-kids-device",   DeviceType.KIDS);
        parentToken = jwtTokenService.createDeviceToken(FAMILY_ID, "conc-parent-device", DeviceType.PARENT);

        if (!pairedDeviceRepository.existsById("conc-kids-device")) {
            PairedDevice device = new PairedDevice();
            device.setId("conc-kids-device");
            device.setFamilyId(FAMILY_ID);
            device.setProfileId(PROFILE_ID);
            device.setDeviceType(DeviceType.KIDS);
            device.setDeviceName("Concurrency Test Kids Device");
            device.setDeviceTokenHash("conc-hash-kids");
            pairedDeviceRepository.save(device);
        }
    }

    /**
     * T2 — Two concurrent requests when only one slot remains (2 pending already exist).
     * Exactly one must succeed (201 Created); the other must be rejected (429).
     */
    @Test
    void concurrent_requests_when_at_limit_allows_only_one() throws Exception {
        // Seed 2 pending requests (limit is 3)
        for (int i = 0; i < 2; i++) {
            ContentRequest r = new ContentRequest();
            r.setProfileId(PROFILE_ID);
            r.setSpotifyUri("spotify:track:seed" + i);
            r.setTitle("Seed " + i);
            r.setContentType(ContentType.MUSIC);
            requestRepository.save(r);
        }

        int threads = 5;
        CountDownLatch ready  = new CountDownLatch(threads);
        CountDownLatch start  = new CountDownLatch(1);
        ExecutorService pool  = Executors.newFixedThreadPool(threads);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return client.post()
                        .uri("/api/v1/profiles/" + PROFILE_ID + "/content-requests")
                        .headers(h -> h.setBearerAuth(kidsToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new CreateContentRequestDto(
                                "spotify:track:race" + idx, "Race Song " + idx,
                                ContentType.MUSIC, null, "Artist"))
                        .exchange()
                        .returnResult(String.class)
                        .getStatus()
                        .value();
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();

        List<Integer> statuses = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return -1; } })
                .toList();

        long created  = statuses.stream().filter(s -> s == 201).count();
        long rejected = statuses.stream().filter(s -> s == 429).count();

        // Exactly 1 request may fill the 3rd slot; the remaining (threads-1) must be rejected
        assertThat(created).isEqualTo(1);
        assertThat(rejected).isEqualTo(threads - 1);

        // DB must reflect exactly 3 pending requests in total
        long pending = requestRepository.findAll().stream()
                .filter(r -> r.getStatus() == ContentRequestStatus.PENDING)
                .filter(r -> r.getProfileId().equals(PROFILE_ID))
                .count();
        assertThat(pending).isEqualTo(3);
    }

    /**
     * T3 — Two concurrent clicks on the same approve-token email link.
     * Only one transition PENDING→APPROVED should occur; the second must be a no-op
     * (either returns 200 with status already APPROVED, or 404 if token was cleared).
     */
    @Test
    void concurrent_approve_token_is_single_use() throws Exception {
        ContentRequest request = new ContentRequest();
        request.setProfileId(PROFILE_ID);
        request.setSpotifyUri("spotify:track:double-approve");
        request.setTitle("Double Click Song");
        request.setContentType(ContentType.MUSIC);
        requestRepository.save(request);

        String token = requestRepository.findById(request.getId()).orElseThrow().getApproveToken();
        assertThat(token).isNotBlank();

        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Future<ContentRequestStatus>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return requestService.approveByToken(token).block().getStatus();
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();

        List<ContentRequestStatus> results = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                .toList();

        // All non-null results must be APPROVED (the token is idempotent once consumed)
        assertThat(results).doesNotContainNull();
        assertThat(results).allMatch(s -> s == ContentRequestStatus.APPROVED);

        // The DB row must be APPROVED exactly once and the token must be cleared
        ContentRequest fromDb = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(ContentRequestStatus.APPROVED);
        assertThat(fromDb.getApproveToken()).isNull();

        // AllowedContent must be created exactly once (no duplicates)
        long count = contentRepository.findByProfileId(PROFILE_ID).stream()
                .filter(c -> c.getSpotifyUri().equals("spotify:track:double-approve"))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
