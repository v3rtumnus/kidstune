package at.kidstune.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal entity for the content_request table.
 * Full request workflow (approval, notifications) is implemented in prompt 2.6 / phase 7.
 */
@Entity
@Table(name = "content_request")
public class ContentRequest {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContentRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @PrePersist
    void prePersist() {
        if (id == null)          id = UUID.randomUUID().toString();
        if (requestedAt == null) requestedAt = Instant.now();
        if (status == null)      status = ContentRequestStatus.PENDING;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public ContentRequestStatus getStatus() { return status; }
    public void setStatus(ContentRequestStatus status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
}