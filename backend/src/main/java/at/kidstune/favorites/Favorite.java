package at.kidstune.favorites;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorite")
public class Favorite {

    @Id
    private String id;

    @Column(name = "profile_id", nullable = false, length = 36)
    private String profileId;

    @Column(name = "spotify_track_uri", nullable = false, length = 255)
    private String spotifyTrackUri;

    @Column(name = "track_title", nullable = false, length = 500)
    private String trackTitle;

    @Column(name = "track_image_url", columnDefinition = "TEXT")
    private String trackImageUrl;

    @Column(name = "artist_name", length = 500)
    private String artistName;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @PrePersist
    void prePersist() {
        if (id == null)      id = UUID.randomUUID().toString();
        if (addedAt == null) addedAt = Instant.now();
    }

    public String getId()                  { return id; }
    public void   setId(String id)         { this.id = id; }

    public String getProfileId()                  { return profileId; }
    public void   setProfileId(String profileId)  { this.profileId = profileId; }

    public String getSpotifyTrackUri()                        { return spotifyTrackUri; }
    public void   setSpotifyTrackUri(String spotifyTrackUri)  { this.spotifyTrackUri = spotifyTrackUri; }

    public String getTrackTitle()                  { return trackTitle; }
    public void   setTrackTitle(String trackTitle) { this.trackTitle = trackTitle; }

    public String getTrackImageUrl()                      { return trackImageUrl; }
    public void   setTrackImageUrl(String trackImageUrl)  { this.trackImageUrl = trackImageUrl; }

    public String getArtistName()                  { return artistName; }
    public void   setArtistName(String artistName) { this.artistName = artistName; }

    public Instant getAddedAt()                { return addedAt; }
    public void    setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}