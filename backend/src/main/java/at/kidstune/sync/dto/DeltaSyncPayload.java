package at.kidstune.sync.dto;

import java.time.Instant;
import java.util.List;

public record DeltaSyncPayload(
        List<SyncContentEntryDto> added,
        List<SyncContentEntryDto> updated,
        List<String> removed,
        List<SyncFavoriteDto> favoritesAdded,
        List<String> favoritesRemoved,
        Instant syncTimestamp
) {}