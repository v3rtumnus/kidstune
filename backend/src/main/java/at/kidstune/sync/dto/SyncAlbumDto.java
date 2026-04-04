package at.kidstune.sync.dto;

import at.kidstune.content.ContentType;
import at.kidstune.resolver.ResolvedAlbum;

import java.util.List;

public record SyncAlbumDto(
        String spotifyAlbumUri,
        String title,
        String imageUrl,
        String releaseDate,
        ContentType contentType,
        List<SyncTrackDto> tracks
) {
    public static SyncAlbumDto from(ResolvedAlbum a, List<SyncTrackDto> tracks) {
        return new SyncAlbumDto(
                a.getSpotifyAlbumUri(), a.getTitle(), a.getImageUrl(),
                a.getReleaseDate(), a.getContentType(), tracks);
    }
}
