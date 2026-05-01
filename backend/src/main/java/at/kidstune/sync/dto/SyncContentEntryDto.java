package at.kidstune.sync.dto;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;

import java.time.Instant;
import java.util.List;

public record SyncContentEntryDto(
        String id,
        String spotifyUri,
        ContentScope scope,
        ContentType contentType,
        String title,
        String imageUrl,
        String artistName,
        List<SyncAlbumDto> albums,
        Instant lastListenedAt
) {
    public static SyncContentEntryDto from(AllowedContent c, List<SyncAlbumDto> albums, Instant lastListenedAt) {
        return new SyncContentEntryDto(
                c.getId(), c.getSpotifyUri(), c.getScope(), c.getContentType(),
                c.getTitle(), c.getImageUrl(), c.getArtistName(), albums, lastListenedAt);
    }
}