package at.kidstune.resolver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "resolved_track")
public class ResolvedTrack {

    @Id
    private String id;

    @Column(name = "resolved_album_id", nullable = false, length = 36)
    private String resolvedAlbumId;

    @Column(name = "spotify_track_uri", nullable = false, length = 255)
    private String spotifyTrackUri;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "artist_name", length = 500)
    private String artistName;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "track_number")
    private Integer trackNumber;

    @Column(name = "disc_number")
    private Integer discNumber;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }

    public String getResolvedAlbumId()                          { return resolvedAlbumId; }
    public void   setResolvedAlbumId(String resolvedAlbumId)    { this.resolvedAlbumId = resolvedAlbumId; }

    public String getSpotifyTrackUri()                          { return spotifyTrackUri; }
    public void   setSpotifyTrackUri(String spotifyTrackUri)    { this.spotifyTrackUri = spotifyTrackUri; }

    public String getTitle()                    { return title; }
    public void   setTitle(String title)        { this.title = title; }

    public String getArtistName()                    { return artistName; }
    public void   setArtistName(String artistName)   { this.artistName = artistName; }

    public Long getDurationMs()                    { return durationMs; }
    public void setDurationMs(Long durationMs)     { this.durationMs = durationMs; }

    public Integer getTrackNumber()                    { return trackNumber; }
    public void    setTrackNumber(Integer trackNumber) { this.trackNumber = trackNumber; }

    public Integer getDiscNumber()                    { return discNumber; }
    public void    setDiscNumber(Integer discNumber)  { this.discNumber = discNumber; }

    public String getImageUrl()                  { return imageUrl; }
    public void   setImageUrl(String imageUrl)   { this.imageUrl = imageUrl; }
}
