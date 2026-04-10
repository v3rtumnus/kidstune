package at.kidstune.requests;

import at.kidstune.common.OwnershipService;
import at.kidstune.common.SecurityUtils;
import at.kidstune.config.RequestThrottleService;
import at.kidstune.requests.dto.ApproveRequestBody;
import at.kidstune.requests.dto.BulkApproveRequest;
import at.kidstune.requests.dto.BulkRejectRequest;
import at.kidstune.requests.dto.ContentRequestResponse;
import at.kidstune.requests.dto.CreateContentRequestDto;
import at.kidstune.requests.dto.PendingCountResponse;
import at.kidstune.requests.dto.RejectRequestBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class ContentRequestController {

    private final ContentRequestService requestService;
    private final RequestThrottleService throttle;
    private final OwnershipService       ownershipService;

    public ContentRequestController(ContentRequestService requestService,
                                    RequestThrottleService throttle,
                                    OwnershipService ownershipService) {
        this.requestService  = requestService;
        this.throttle        = throttle;
        this.ownershipService = ownershipService;
    }

    // ── POST /api/v1/profiles/{profileId}/content-requests (KIDS + PARENT) ────

    @PostMapping("/api/v1/profiles/{profileId}/content-requests")
    public Mono<ResponseEntity<ContentRequestResponse>> createRequest(
            @PathVariable String profileId,
            @RequestBody @Valid CreateContentRequestDto body) {

        throttle.checkRequestLimit(profileId); // throws RateLimitExceededException if over limit

        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
                .then(requestService.createRequest(
                        profileId,
                        body.spotifyUri(),
                        body.title(),
                        body.contentType(),
                        body.imageUrl(),
                        body.artistName()))
                .map(r -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ContentRequestResponse.from(r)));
    }

    // ── GET /api/v1/content-requests (PARENT) ─────────────────────────────────

    @GetMapping("/api/v1/content-requests")
    public Mono<ResponseEntity<List<ContentRequestResponse>>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String profileId,
            @AuthenticationPrincipal String familyId) {

        ContentRequestStatus statusFilter = null;
        if (status != null) {
            try { statusFilter = ContentRequestStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        return requestService.listRequests(familyId, statusFilter, profileId)
                .map(ResponseEntity::ok);
    }

    // ── GET /api/v1/content-requests/pending-count (PARENT) ───────────────────

    @GetMapping("/api/v1/content-requests/pending-count")
    public Mono<ResponseEntity<PendingCountResponse>> pendingCount(
            @AuthenticationPrincipal String familyId) {

        return requestService.getPendingCount(familyId)
                .map(ResponseEntity::ok);
    }

    // ── POST /api/v1/content-requests/{id}/approve (PARENT) ───────────────────

    @PostMapping("/api/v1/content-requests/{id}/approve")
    public Mono<ResponseEntity<Void>> approve(
            @PathVariable String id,
            @RequestBody(required = false) ApproveRequestBody body,
            @AuthenticationPrincipal String familyId) {

        List<String> profileIds      = body != null ? body.approvedByProfileIds() : null;
        String note                  = body != null ? body.note() : null;
        var contentTypeOverride      = body != null ? body.contentTypeOverride()  : null;

        return requestService.approveRequestWithOptions(id, familyId, profileIds, contentTypeOverride, note)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    // ── POST /api/v1/content-requests/{id}/reject (PARENT) ────────────────────

    @PostMapping("/api/v1/content-requests/{id}/reject")
    public Mono<ResponseEntity<Void>> reject(
            @PathVariable String id,
            @RequestBody(required = false) RejectRequestBody body,
            @AuthenticationPrincipal String familyId) {

        String note = body != null ? body.note() : null;
        return requestService.rejectRequest(id, familyId, note)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    // ── POST /api/v1/content-requests/bulk-approve (PARENT) ───────────────────

    @PostMapping("/api/v1/content-requests/bulk-approve")
    public Mono<ResponseEntity<Void>> bulkApprove(
            @RequestBody @Valid BulkApproveRequest body,
            @AuthenticationPrincipal String familyId) {

        return requestService.bulkApprove(body.requestIds(), familyId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    // ── POST /api/v1/content-requests/bulk-reject (PARENT) ────────────────────

    @PostMapping("/api/v1/content-requests/bulk-reject")
    public Mono<ResponseEntity<Void>> bulkReject(
            @RequestBody @Valid BulkRejectRequest body,
            @AuthenticationPrincipal String familyId) {

        return requestService.bulkReject(body.requestIds(), familyId, body.note())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
