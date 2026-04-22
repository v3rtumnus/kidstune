package at.kidstune.insights.dto;

public record TrackSummary(
        String trackId,
        String trackName,
        String artistName,
        int    playCount,
        long   totalSeconds
) {}
