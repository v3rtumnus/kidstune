package at.kidstune.content.dto;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;

import java.time.Instant;

public record ContentResponse(
        String id,
        String profileId,
        String spotifyUri,
        ContentType contentType,
        ContentScope scope,
        String title,
        String imageUrl,
        String artistName,
        Instant createdAt
) {
    public static ContentResponse from(AllowedContent c) {
        return new ContentResponse(
                c.getId(),
                c.getProfileId(),
                c.getSpotifyUri(),
                c.getContentType(),
                c.getScope(),
                c.getTitle(),
                c.getImageUrl(),
                c.getArtistName(),
                c.getCreatedAt()
        );
    }
}
