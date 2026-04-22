package at.kidstune.insights.dto;

import at.kidstune.insights.PlayEvent;

import java.time.Instant;

public record PlayEventDto(
        Instant playedAt,
        String  trackId,
        String  trackName,
        String  artistName,
        int     durationMs,
        String  itemType,
        String  contextType,
        String  contextUri
) {
    public static PlayEventDto from(PlayEvent e) {
        return new PlayEventDto(e.getPlayedAt(), e.getTrackId(), e.getTrackName(),
                e.getArtistName(), e.getDurationMs(), e.getItemType(),
                e.getContextType(), e.getContextUri());
    }
}
