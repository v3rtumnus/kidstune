package at.kidstune.favorites.dto;

import at.kidstune.favorites.Favorite;

import java.time.Instant;

public record FavoriteResponse(
        String id,
        String profileId,
        String spotifyTrackUri,
        String trackTitle,
        String trackImageUrl,
        String artistName,
        Instant addedAt
) {
    public static FavoriteResponse from(Favorite f) {
        return new FavoriteResponse(
                f.getId(),
                f.getProfileId(),
                f.getSpotifyTrackUri(),
                f.getTrackTitle(),
                f.getTrackImageUrl(),
                f.getArtistName(),
                f.getAddedAt()
        );
    }
}
