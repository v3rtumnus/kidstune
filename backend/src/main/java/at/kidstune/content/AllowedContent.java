package at.kidstune.content;

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
@Table(name = "allowed_content")
public class AllowedContent {

    @Id
    private String id;

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Column(name = "spotify_uri", nullable = false)
    private String spotifyUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private ContentScope scope;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "artist_name")
    private String artistName;

    @Column(name = "cached_metadata", columnDefinition = "JSON")
    private String cachedMetadata;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null)        id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()              { return id; }
    public void setId(String id)       { this.id = id; }

    public String getProfileId()                 { return profileId; }
    public void setProfileId(String profileId)   { this.profileId = profileId; }

    public String getSpotifyUri()                { return spotifyUri; }
    public void setSpotifyUri(String spotifyUri) { this.spotifyUri = spotifyUri; }

    public ContentType getContentType()                  { return contentType; }
    public void setContentType(ContentType contentType)  { this.contentType = contentType; }

    public ContentScope getScope()               { return scope; }
    public void setScope(ContentScope scope)     { this.scope = scope; }

    public String getTitle()             { return title; }
    public void setTitle(String title)   { this.title = title; }

    public String getImageUrl()                { return imageUrl; }
    public void setImageUrl(String imageUrl)   { this.imageUrl = imageUrl; }

    public String getArtistName()                  { return artistName; }
    public void setArtistName(String artistName)   { this.artistName = artistName; }

    public String getCachedMetadata()                      { return cachedMetadata; }
    public void setCachedMetadata(String cachedMetadata)   { this.cachedMetadata = cachedMetadata; }

    public String getAddedBy()               { return addedBy; }
    public void setAddedBy(String addedBy)   { this.addedBy = addedBy; }

    public Instant getResolvedAt()                 { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt)  { this.resolvedAt = resolvedAt; }

    public Instant getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Instant createdAt)    { this.createdAt = createdAt; }
}
