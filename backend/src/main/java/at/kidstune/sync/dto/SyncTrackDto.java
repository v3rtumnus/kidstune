package at.kidstune.sync.dto;

import at.kidstune.resolver.ResolvedTrack;

public record SyncTrackDto(
        String spotifyTrackUri,
        String title,
        String artistName,
        Long durationMs,
        Integer trackNumber,
        Integer discNumber,
        String imageUrl
) {
    public static SyncTrackDto from(ResolvedTrack t) {
        return new SyncTrackDto(
                t.getSpotifyTrackUri(), t.getTitle(), t.getArtistName(),
                t.getDurationMs(), t.getTrackNumber(), t.getDiscNumber(), t.getImageUrl());
    }
}