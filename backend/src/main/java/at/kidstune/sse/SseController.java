package at.kidstune.sse;

import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Streams pending-request badge count updates to authenticated dashboard clients via SSE.
 *
 * <p>The client opens one long-lived connection on page load.  On connect an initial
 * count is pushed immediately; subsequent events arrive whenever
 * {@link SseRegistry#emit} is called from the request workflow.
 */
@RestController
public class SseController {

    private final SseRegistry                sseRegistry;
    private final ContentRequestRepository   requestRepository;
    private final ProfileRepository          profileRepository;

    public SseController(SseRegistry sseRegistry,
                         ContentRequestRepository requestRepository,
                         ProfileRepository profileRepository) {
        this.sseRegistry       = sseRegistry;
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping(value = "/web/sse/requests", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> requestSse(
            @AuthenticationPrincipal String familyId) {

        Mono<ServerSentEvent<String>> initial = Mono
                .fromCallable(() -> pendingCount(familyId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toSse);

        Flux<ServerSentEvent<String>> updates = sseRegistry
                .register(familyId)
                .map(this::toSse);

        return Flux.concat(initial, updates);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long pendingCount(String familyId) {
        List<String> profileIds = profileRepository.findByFamilyId(familyId)
                .stream().map(ChildProfile::getId).toList();
        if (profileIds.isEmpty()) return 0;
        return requestRepository.countByProfileIdInAndStatus(profileIds, ContentRequestStatus.PENDING);
    }

    private ServerSentEvent<String> toSse(long count) {
        return ServerSentEvent.<String>builder()
                .event("pendingCount")
                .data("{\"pendingCount\":" + count + "}")
                .build();
    }
}
