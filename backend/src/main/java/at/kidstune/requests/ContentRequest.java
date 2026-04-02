package at.kidstune.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_request")
@Getter
@Setter
public class ContentRequest {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Column(name = "spotify_uri", nullable = false, length = 255)
    private String spotifyUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private at.kidstune.content.ContentType contentType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "artist_name", length = 500)
    private String artistName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContentRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "parent_note", columnDefinition = "TEXT")
    private String parentNote;

    @Column(name = "digest_sent_at")
    private Instant digestSentAt;

    @PrePersist
    void prePersist() {
        if (id == null)          id = UUID.randomUUID().toString();
        if (requestedAt == null) requestedAt = Instant.now();
        if (status == null)      status = ContentRequestStatus.PENDING;
    }
}