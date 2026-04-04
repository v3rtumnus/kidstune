package at.kidstune.sync.dto;

import at.kidstune.favorites.Favorite;

import java.time.Instant;

public record SyncFavoriteDto(
        String spotifyTrackUri,
        String trackTitle,
        String trackImageUrl,
        String artistName,
        Instant addedAt
) {
    public static SyncFavoriteDto from(Favorite f) {
        return new SyncFavoriteDto(
                f.getSpotifyTrackUri(), f.getTrackTitle(),
                f.getTrackImageUrl(), f.getArtistName(), f.getAddedAt());
    }
}
