package at.kidstune.requests.dto;

import at.kidstune.content.ContentType;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestStatus;

import java.time.Instant;

public record ContentRequestResponse(
        String               id,
        String               profileId,
        String               spotifyUri,
        ContentType          contentType,
        String               title,
        String               imageUrl,
        String               artistName,
        ContentRequestStatus status,
        Instant              requestedAt,
        Instant              resolvedAt,
        String               resolvedBy,
        String               parentNote,
        Instant              digestSentAt
) {
    public static ContentRequestResponse from(ContentRequest r) {
        return new ContentRequestResponse(
                r.getId(),
                r.getProfileId(),
                r.getSpotifyUri(),
                r.getContentType(),
                r.getTitle(),
                r.getImageUrl(),
                r.getArtistName(),
                r.getStatus(),
                r.getRequestedAt(),
                r.getResolvedAt(),
                r.getResolvedBy(),
                r.getParentNote(),
                r.getDigestSentAt()
        );
    }
}
