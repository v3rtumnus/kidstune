package at.kidstune.sync;

import at.kidstune.sync.dto.DeltaSyncPayload;
import at.kidstune.sync.dto.FullSyncPayload;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/{profileId}")
    public Mono<ResponseEntity<FullSyncPayload>> fullSync(@PathVariable String profileId) {
        return syncService.fullSync(profileId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{profileId}/delta")
    public Mono<ResponseEntity<DeltaSyncPayload>> deltaSync(
            @PathVariable String profileId,
            @RequestParam("since") String since) {
        Instant sinceInstant = Instant.parse(since);
        return syncService.deltaSync(profileId, sinceInstant)
                .map(ResponseEntity::ok);
    }
}
