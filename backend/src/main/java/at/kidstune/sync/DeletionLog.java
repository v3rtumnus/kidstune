package at.kidstune.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_log")
public class DeletionLog {

    public enum DeletionType { CONTENT, FAVORITE }

    @Id
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DeletionType type;

    @Column(name = "spotify_uri", nullable = false, length = 255)
    private String spotifyUri;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        if (id == null)         id = UUID.randomUUID().toString();
        if (deletedAt == null)  deletedAt = Instant.now();
    }

    public String getId()                { return id; }
    public void   setId(String id)       { this.id = id; }

    public String getProfileId()                  { return profileId; }
    public void   setProfileId(String profileId)  { this.profileId = profileId; }

    public DeletionType getType()                  { return type; }
    public void         setType(DeletionType type) { this.type = type; }

    public String getSpotifyUri()                  { return spotifyUri; }
    public void   setSpotifyUri(String spotifyUri) { this.spotifyUri = spotifyUri; }

    public Instant getDeletedAt()                  { return deletedAt; }
    public void    setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}