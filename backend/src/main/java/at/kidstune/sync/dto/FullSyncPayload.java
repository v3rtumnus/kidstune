package at.kidstune.sync.dto;

import java.time.Instant;
import java.util.List;

public record FullSyncPayload(
        SyncProfileDto profile,
        List<SyncFavoriteDto> favorites,
        List<SyncContentEntryDto> content,
        boolean pinAvailable,
        Instant syncTimestamp
) {}
