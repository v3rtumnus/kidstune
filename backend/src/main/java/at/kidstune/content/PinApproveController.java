package at.kidstune.content;

import at.kidstune.common.OwnershipService;
import at.kidstune.common.SecurityUtils;
import at.kidstune.content.dto.PinApproveRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST endpoint for the in-person quick-approval flow.
 * Called by the kids app when a parent enters their PIN directly on the device.
 *
 * <p>Security: requires a valid device JWT (same as all other kids-app endpoints).
 * The JWT carries the {@code familyId}, so the device cannot impersonate a different family.</p>
 */
@RestController
public class PinApproveController {

    private final PinApproveService pinApproveService;
    private final OwnershipService  ownershipService;

    public PinApproveController(PinApproveService pinApproveService,
                                OwnershipService ownershipService) {
        this.pinApproveService = pinApproveService;
        this.ownershipService  = ownershipService;
    }

    /**
     * {@code POST /api/v1/profiles/{profileId}/content/pin-approve}
     *
     * <ul>
     *   <li>204 No Content – PIN correct, content added to whitelist</li>
     *   <li>403 Forbidden  – wrong PIN or PIN not configured</li>
     *   <li>429 Too Many Requests – 3+ consecutive failures, try again in 10 min</li>
     * </ul>
     */
    @PostMapping("/api/v1/profiles/{profileId}/content/pin-approve")
    public Mono<ResponseEntity<Void>> pinApprove(
            @PathVariable String profileId,
            @RequestBody @Valid PinApproveRequest request) {

        return SecurityUtils.getFamilyId()
                .flatMap(familyId ->
                        ownershipService.requireProfileOwnership(profileId, familyId)
                                .thenReturn(familyId))
                .flatMap(familyId ->
                        pinApproveService.pinApprove(profileId, familyId, request))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
