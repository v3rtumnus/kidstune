package at.kidstune.requests;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.notification.EmailNotificationService;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.push.PushNotificationService;
import at.kidstune.requests.dto.ContentRequestResponse;
import at.kidstune.requests.dto.PendingCountResponse;
import at.kidstune.resolver.ContentResolver;
import at.kidstune.sse.SseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContentRequestService {

    private static final Logger log = LoggerFactory.getLogger(ContentRequestService.class);

    private final ContentRequestRepository requestRepository;
    private final ContentRepository        contentRepository;
    private final ProfileRepository        profileRepository;
    private final EmailNotificationService emailNotificationService;
    private final ContentResolver          contentResolver;
    private final SseRegistry             sseRegistry;
    private final PushNotificationService  pushNotificationService;
    private final TransactionTemplate      txTemplate;

    public ContentRequestService(ContentRequestRepository requestRepository,
                                  ContentRepository contentRepository,
                                  ProfileRepository profileRepository,
                                  EmailNotificationService emailNotificationService,
                                  ContentResolver contentResolver,
                                  SseRegistry sseRegistry,
                                  PushNotificationService pushNotificationService,
                                  TransactionTemplate txTemplate) {
        this.requestRepository          = requestRepository;
        this.contentRepository          = contentRepository;
        this.profileRepository          = profileRepository;
        this.emailNotificationService   = emailNotificationService;
        this.contentResolver            = contentResolver;
        this.sseRegistry                = sseRegistry;
        this.pushNotificationService    = pushNotificationService;
        this.txTemplate                 = txTemplate;
    }

    /**
     * Creates a new PENDING request. Enforces max-3 pending per profile.
     * Uses a pessimistic write lock on existing pending rows to prevent the
     * check-then-act race condition under concurrent submissions.
     * Fires email + push async after the transaction commits.
     */
    public Mono<ContentRequest> createRequest(String profileId, String spotifyUri,
                                              String title, ContentType contentType,
                                              String imageUrl, String artistName) {
        return dbTx(() -> {
            // Lock existing pending rows so concurrent inserts are serialized.
            List<ContentRequest> pending = requestRepository.findPendingForUpdate(profileId);
            if (pending.size() >= 3) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Maximal 3 offene Anfragen pro Profil erlaubt.");
            }
            ContentRequest request = new ContentRequest();
            request.setProfileId(profileId);
            request.setSpotifyUri(spotifyUri);
            request.setTitle(title);
            request.setContentType(contentType);
            request.setImageUrl(imageUrl);
            request.setArtistName(artistName);
            return requestRepository.save(request);
        }).doOnNext(req -> {
            emailNotificationService.sendRequestNotification(req);
            profileRepository.findById(profileId).ifPresent(profile -> {
                String familyId = profile.getFamilyId();
                emitSseCount(familyId);
                pushNotificationService.sendRequestNotification(req, familyId);
            });
        });
    }

    /**
     * Approves a request by its approve_token (public one-click flow – no auth required).
     * Uses a pessimistic write lock so that two simultaneous clicks on the same email link
     * cannot both proceed past the PENDING check.
     */
    public Mono<ContentRequest> approveByToken(String approveToken) {
        return dbTx(() -> {
            ContentRequest request = requestRepository.findByApproveTokenForUpdate(approveToken)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Ungültiger oder bereits verwendeter Token."));
            if (request.getStatus() != ContentRequestStatus.PENDING) {
                return request; // already handled – caller checks status
            }
            addContentIfMissing(request.getProfileId(), request);
            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setApproveToken(null); // single-use: clear after use
            return requestRepository.save(request);
        }).doOnNext(req -> emitSseCountForProfile(req.getProfileId()));
    }

    /** Approve a single request: creates AllowedContent for the requesting profile. */
    public Mono<Void> approveRequest(String requestId, String familyId) {
        return dbTx(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            addContentIfMissing(request.getProfileId(), request);
            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            requestRepository.save(request);
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    /** Approve for all profiles in the family: creates AllowedContent for every profile. */
    public Mono<Void> approveForAllProfiles(String requestId, String familyId) {
        return dbTx(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            List<String> profileIds = profileRepository.findByFamilyId(familyId)
                    .stream().map(ChildProfile::getId).toList();
            for (String profileId : profileIds) {
                addContentIfMissing(profileId, request);
            }
            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            requestRepository.save(request);
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    private static final int MAX_NOTE_LENGTH = 500;

    /** Reject a request with an optional parent note. */
    public Mono<Void> rejectRequest(String requestId, String familyId, String note) {
        return dbTx(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            request.setStatus(ContentRequestStatus.REJECTED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            if (note != null && !note.isBlank()) {
                String trimmed = note.strip();
                request.setParentNote(trimmed.length() > MAX_NOTE_LENGTH
                        ? trimmed.substring(0, MAX_NOTE_LENGTH) : trimmed);
            }
            requestRepository.save(request);
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    /** Approve with optional profile override, content-type override, and parent note. */
    public Mono<Void> approveRequestWithOptions(String requestId, String familyId,
                                                List<String> approvedByProfileIds,
                                                ContentType contentTypeOverride,
                                                String note) {
        return dbTx(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);

            List<String> targetProfileIds;
            if (approvedByProfileIds != null && !approvedByProfileIds.isEmpty()) {
                List<String> familyProfileIds = profileRepository.findByFamilyId(familyId)
                        .stream().map(ChildProfile::getId).toList();
                targetProfileIds = approvedByProfileIds.stream()
                        .filter(familyProfileIds::contains).toList();
            } else {
                targetProfileIds = List.of(request.getProfileId());
            }

            ContentType effectiveType = contentTypeOverride != null
                    ? contentTypeOverride
                    : request.getContentType();

            for (String profileId : targetProfileIds) {
                addContentIfMissing(profileId, request, effectiveType);
            }

            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            if (note != null && !note.isBlank()) {
                String trimmed = note.strip();
                request.setParentNote(trimmed.length() > MAX_NOTE_LENGTH
                        ? trimmed.substring(0, MAX_NOTE_LENGTH) : trimmed);
            }
            requestRepository.save(request);
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    /** Bulk-reject a list of request IDs. Skips requests that don't belong to the family. */
    public Mono<Void> bulkReject(List<String> requestIds, String familyId, String note) {
        return dbTx(() -> {
            for (String requestId : requestIds) {
                try {
                    ContentRequest request = getRequestForFamily(requestId, familyId);
                    if (request.getStatus() == ContentRequestStatus.PENDING) {
                        request.setStatus(ContentRequestStatus.REJECTED);
                        request.setResolvedAt(Instant.now());
                        request.setResolvedBy(familyId);
                        if (note != null && !note.isBlank()) {
                            request.setParentNote(note.strip());
                        }
                        requestRepository.save(request);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip — request doesn't belong to this family
                }
            }
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    /** Lists requests for a family, optionally filtered by status and/or profileId. */
    public Mono<List<ContentRequestResponse>> listRequests(
            String familyId, ContentRequestStatus status, String profileIdFilter) {
        return db(() -> {
            List<String> profileIds;
            if (profileIdFilter != null) {
                boolean belongs = profileRepository.findById(profileIdFilter)
                        .map(p -> p.getFamilyId().equals(familyId))
                        .orElse(false);
                profileIds = belongs ? List.of(profileIdFilter) : List.of();
            } else {
                profileIds = profileRepository.findByFamilyId(familyId)
                        .stream().map(ChildProfile::getId).toList();
            }
            if (profileIds.isEmpty()) return List.of();

            List<ContentRequest> requests;
            if (status != null) {
                requests = requestRepository
                        .findByProfileIdInAndStatusOrderByRequestedAtDesc(profileIds, status);
            } else {
                requests = requestRepository.findByProfileIdIn(profileIds)
                        .stream()
                        .sorted(Comparator.comparing(ContentRequest::getRequestedAt).reversed())
                        .toList();
            }
            return requests.stream().map(ContentRequestResponse::from).toList();
        });
    }

    /** Returns pending request counts per profile and the family total. */
    public Mono<PendingCountResponse> getPendingCount(String familyId) {
        return db(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            List<PendingCountResponse.ProfileCount> profileCounts = profiles.stream()
                    .map(p -> new PendingCountResponse.ProfileCount(
                            p.getId(),
                            p.getName(),
                            requestRepository.countByProfileIdAndStatus(
                                    p.getId(), ContentRequestStatus.PENDING)))
                    .toList();
            long total = profileCounts.stream()
                    .mapToLong(PendingCountResponse.ProfileCount::count).sum();
            return new PendingCountResponse(profileCounts, total);
        });
    }

    /** Expires PENDING requests older than 7 days. Runs daily at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void expireStaleRequests() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        // Capture affected families before the bulk update so we can emit SSE afterward.
        Set<String> affectedFamilies = new HashSet<>();
        requestRepository.findProfileIdsWithPendingOlderThan(cutoff).forEach(profileId ->
                profileRepository.findById(profileId)
                        .ifPresent(p -> affectedFamilies.add(p.getFamilyId())));

        int expired = requestRepository.expireOldRequests(cutoff, Instant.now());
        if (expired > 0) {
            log.info("Expired {} stale content request(s) older than 7 days.", expired);
            affectedFamilies.forEach(this::emitSseCount);
        }
    }

    /** Bulk-approve a list of request IDs. Skips requests that don't belong to the family. */
    public Mono<Void> bulkApprove(List<String> requestIds, String familyId) {
        return dbTx(() -> {
            for (String requestId : requestIds) {
                try {
                    ContentRequest request = getRequestForFamily(requestId, familyId);
                    if (request.getStatus() == ContentRequestStatus.PENDING) {
                        addContentIfMissing(request.getProfileId(), request);
                        request.setStatus(ContentRequestStatus.APPROVED);
                        request.setResolvedAt(Instant.now());
                        request.setResolvedBy(familyId);
                        requestRepository.save(request);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip — request doesn't belong to this family
                }
            }
            return null;
        }).then(Mono.fromRunnable(() -> emitSseCount(familyId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContentRequest getRequestForFamily(String requestId, String familyId) {
        ContentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Anfrage nicht gefunden: " + requestId));
        profileRepository.findById(request.getProfileId())
                .filter(p -> p.getFamilyId().equals(familyId))
                .orElseThrow(() -> new IllegalArgumentException("Anfrage nicht gefunden: " + requestId));
        return request;
    }

    private void addContentIfMissing(String profileId, ContentRequest request) {
        addContentIfMissing(profileId, request, request.getContentType());
    }

    private void addContentIfMissing(String profileId, ContentRequest request, ContentType contentType) {
        if (contentRepository.existsByProfileIdAndSpotifyUri(profileId, request.getSpotifyUri())) {
            return;
        }
        AllowedContent content = new AllowedContent();
        content.setProfileId(profileId);
        content.setSpotifyUri(request.getSpotifyUri());
        content.setContentType(contentType);
        content.setScope(scopeFromUri(request.getSpotifyUri()));
        content.setTitle(request.getTitle());
        content.setImageUrl(request.getImageUrl());
        content.setArtistName(request.getArtistName());
        AllowedContent saved = contentRepository.save(content);
        contentResolver.resolveAsync(saved);
    }

    /** Infers ContentScope from the Spotify URI prefix. Defaults to ALBUM. */
    static ContentScope scopeFromUri(String uri) {
        if (uri == null)                            return ContentScope.ALBUM;
        if (uri.startsWith("spotify:track:"))       return ContentScope.TRACK;
        if (uri.startsWith("spotify:album:"))       return ContentScope.ALBUM;
        if (uri.startsWith("spotify:playlist:"))    return ContentScope.PLAYLIST;
        if (uri.startsWith("spotify:artist:"))      return ContentScope.ARTIST;
        return ContentScope.ALBUM;
    }

    // ── SSE emit helpers ──────────────────────────────────────────────────────

    /** Emits the current pending count for the given family to all connected SSE clients. */
    private void emitSseCount(String familyId) {
        List<String> profileIds = profileRepository.findByFamilyId(familyId)
                .stream().map(ChildProfile::getId).toList();
        long count = profileIds.isEmpty() ? 0
                : requestRepository.countByProfileIdInAndStatus(profileIds, ContentRequestStatus.PENDING);
        sseRegistry.emit(familyId, count);
    }

    /** Resolves the family for a profile, then emits the SSE count for that family. */
    private void emitSseCountForProfile(String profileId) {
        profileRepository.findById(profileId)
                .ifPresent(p -> emitSseCount(p.getFamilyId()));
    }

    /** Read-only helper: runs callable on a bounded-elastic thread without a transaction. */
    private <T> Mono<T> db(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Transactional write helper: runs callable inside a transaction on a bounded-elastic
     * thread so the transaction context is active when JPA operations execute.
     * Using {@code @Transactional} on a Mono-returning method is inert because the
     * transaction is opened on the calling thread (WebFlux event loop) while the actual
     * DB work runs on a different thread via subscribeOn.
     */
    private <T> Mono<T> dbTx(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(() ->
            txTemplate.execute(status -> {
                try {
                    return callable.call();
                } catch (RuntimeException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            })
        ).subscribeOn(Schedulers.boundedElastic());
    }
}