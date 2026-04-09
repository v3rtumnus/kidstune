package at.kidstune.sse;

import at.kidstune.AbstractIntTest;
import at.kidstune.content.ContentType;
import at.kidstune.content.SpotifyApiClient;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestService;
import at.kidstune.requests.ContentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// webEnvironment = NONE: this test exercises the service layer directly,
// no WebTestClient or HTTP server is needed.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SseIntTest extends AbstractIntTest {

    @MockitoBean SpotifyApiClient spotifyApiClient;
    @MockitoBean JavaMailSender   mailSender;

    @Autowired SseRegistry              sseRegistry;
    @Autowired ContentRequestService    requestService;
    @Autowired ContentRequestRepository requestRepository;
    @Autowired ProfileRepository        profileRepository;
    @Autowired FamilyRepository         familyRepository;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        requestRepository.deleteAll(
                requestRepository.findByProfileIdIn(List.of(PROFILE_ID)));

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("sse-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            familyRepository.save(f);
        }
        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("SSE Kid");
            p.setAvatarIcon(AvatarIcon.BUNNY);
            p.setAvatarColor(AvatarColor.PINK);
            p.setAgeGroup(AgeGroup.PRESCHOOL);
            profileRepository.save(p);
        }

        when(spotifyApiClient.getAlbumUriForTrack(any())).thenReturn(Mono.just("spotify:album:noop"));
        when(spotifyApiClient.getArtistUrisForTrack(any())).thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(any())).thenReturn(Mono.just(List.of()));
    }

    @Test
    void sseRegistry_emits_increased_count_after_request_created() {
        Flux<Long> flux = sseRegistry.register(FAMILY_ID);

        StepVerifier.create(flux.take(1))
                .then(() -> requestService.createRequest(
                        PROFILE_ID, "spotify:album:sse1", "Testlied",
                        ContentType.MUSIC, null, "Testband").block())
                .expectNextMatches(count -> count >= 1)
                .verifyComplete();
    }

    @Test
    void sseRegistry_emits_decreased_count_after_approve() {
        ContentRequest request = savedPendingRequest("spotify:album:sse-approve");
        assertThat(requestRepository.countByProfileIdAndStatus(PROFILE_ID, ContentRequestStatus.PENDING))
                .isEqualTo(1);

        Flux<Long> flux = sseRegistry.register(FAMILY_ID);

        StepVerifier.create(flux.take(1))
                .then(() -> requestService.approveRequest(request.getId(), FAMILY_ID).block())
                .expectNextMatches(count -> count == 0)
                .verifyComplete();
    }

    @Test
    void sseRegistry_emits_decreased_count_after_reject() {
        ContentRequest request = savedPendingRequest("spotify:album:sse-reject");

        Flux<Long> flux = sseRegistry.register(FAMILY_ID);

        StepVerifier.create(flux.take(1))
                .then(() -> requestService.rejectRequest(request.getId(), FAMILY_ID, null).block())
                .expectNextMatches(count -> count == 0)
                .verifyComplete();
    }

    @Test
    void emit_is_noop_when_no_subscriber_connected() {
        // Should not throw even when no SSE client is connected
        sseRegistry.emit("nonexistent-family-" + UUID.randomUUID(), 42L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContentRequest savedPendingRequest(String uri) {
        ContentRequest r = new ContentRequest();
        r.setProfileId(PROFILE_ID);
        r.setSpotifyUri(uri);
        r.setTitle("SSE Test Song");
        r.setArtistName("Test Artist");
        r.setContentType(ContentType.MUSIC);
        return requestRepository.save(r);
    }
}
