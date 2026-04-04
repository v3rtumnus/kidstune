package at.kidstune.resolver;

import at.kidstune.content.ContentType;
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
@Table(name = "resolved_album")
public class ResolvedAlbum {

    @Id
    private String id;

    @Column(name = "allowed_content_id", nullable = false, length = 36)
    private String allowedContentId;

    @Column(name = "spotify_album_uri", nullable = false, length = 255)
    private String spotifyAlbumUri;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "release_date", length = 10)
    private String releaseDate;

    @Column(name = "total_tracks")
    private Integer totalTracks;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId()                           { return id; }
    public void   setId(String id)                  { this.id = id; }

    public String getAllowedContentId()                            { return allowedContentId; }
    public void   setAllowedContentId(String allowedContentId)    { this.allowedContentId = allowedContentId; }

    public String getSpotifyAlbumUri()                            { return spotifyAlbumUri; }
    public void   setSpotifyAlbumUri(String spotifyAlbumUri)      { this.spotifyAlbumUri = spotifyAlbumUri; }

    public String getTitle()                        { return title; }
    public void   setTitle(String title)            { this.title = title; }

    public String getImageUrl()                     { return imageUrl; }
    public void   setImageUrl(String imageUrl)      { this.imageUrl = imageUrl; }

    public String getReleaseDate()                       { return releaseDate; }
    public void   setReleaseDate(String releaseDate)     { this.releaseDate = releaseDate; }

    public Integer getTotalTracks()                      { return totalTracks; }
    public void    setTotalTracks(Integer totalTracks)   { this.totalTracks = totalTracks; }

    public ContentType getContentType()                        { return contentType; }
    public void        setContentType(ContentType contentType) { this.contentType = contentType; }

    public Instant getResolvedAt()                     { return resolvedAt; }
    public void    setResolvedAt(Instant resolvedAt)   { this.resolvedAt = resolvedAt; }
}
