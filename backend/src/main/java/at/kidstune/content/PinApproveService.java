package at.kidstune.content;

import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.PinApproveRequest;
import at.kidstune.family.FamilyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * Handles quick in-person PIN approval: validates the 4-digit PIN entered by a parent
 * on the child's device and, on success, adds the requested content to the child's whitelist.
 *
 * <p>Rate limiting: a family is locked out for 10 minutes after 3 consecutive wrong PINs.</p>
 */
@Service
public class PinApproveService {

    private static final int MAX_ATTEMPTS = 3;

    private final FamilyService  familyService;
    private final ContentService contentService;

    /** keyed by familyId → consecutive wrong-PIN count; auto-expires after 10 minutes of inactivity */
    private final Cache<String, Integer> failedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public PinApproveService(FamilyService familyService, ContentService contentService) {
        this.familyService  = familyService;
        this.contentService = contentService;
    }

    /**
     * Validates the PIN and adds the content to {@code profileId}'s whitelist on success.
     *
     * @throws ResponseStatusException 429 after 3 consecutive wrong PINs (10-min window)
     * @throws ResponseStatusException 403 on wrong PIN (or PIN not configured)
     */
    public Mono<Void> pinApprove(String profileId, String familyId, PinApproveRequest request) {
        return Mono.fromCallable(() -> {
                    int attempts = failedAttempts.get(familyId, k -> 0);
                    if (attempts >= MAX_ATTEMPTS) {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                "Zu viele Fehlversuche");
                    }
                    if (!familyService.verifyPin(familyId, request.pin())) {
                        failedAttempts.put(familyId, attempts + 1);
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger PIN");
                    }
                    failedAttempts.invalidate(familyId);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(contentService.addContent(profileId, new AddContentRequest(
                        request.spotifyUri(),
                        request.scope(),
                        request.title(),
                        request.imageUrl(),
                        request.artistName(),
                        null,
                        null
                )))
                // Already in the whitelist → treat as success, not an error
                .onErrorResume(ContentException.class,
                        ex -> ex.getStatus() == HttpStatus.CONFLICT ? Mono.empty() : Mono.error(ex))
                .then();
    }
}
