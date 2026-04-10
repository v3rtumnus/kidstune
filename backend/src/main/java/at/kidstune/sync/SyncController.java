package at.kidstune.sync;

import at.kidstune.common.SecurityUtils;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.sync.dto.DeltaSyncPayload;
import at.kidstune.sync.dto.FullSyncPayload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService             syncService;
    private final PairedDeviceRepository  deviceRepository;

    public SyncController(SyncService syncService, PairedDeviceRepository deviceRepository) {
        this.syncService      = syncService;
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/{profileId}")
    public Mono<ResponseEntity<FullSyncPayload>> fullSync(@PathVariable String profileId) {
        return requireDeviceOwnsProfile(profileId)
                .then(syncService.fullSync(profileId))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{profileId}/delta")
    public Mono<ResponseEntity<DeltaSyncPayload>> deltaSync(
            @PathVariable String profileId,
            @RequestParam("since") String since) {
        Instant sinceInstant = Instant.parse(since);
        return requireDeviceOwnsProfile(profileId)
                .then(syncService.deltaSync(profileId, sinceInstant))
                .map(ResponseEntity::ok);
    }

    /**
     * Validates that the authenticated device (identified by deviceId in the JWT) is bound
     * to the requested profileId. Prevents a device from syncing another profile's content.
     */
    private Mono<Void> requireDeviceOwnsProfile(String profileId) {
        return SecurityUtils.getClaims()
                .flatMap(claims -> Mono.fromCallable(() -> {
                    boolean owned = deviceRepository
                            .findByIdAndProfileId(claims.deviceId(), profileId)
                            .isPresent();
                    if (!owned) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
                    }
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }
}
