package at.kidstune.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family")
public class Family {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "spotify_user_id", nullable = false, unique = true, length = 255)
    private String spotifyUserId;

    /** AES-256-GCM encrypted Spotify refresh token. */
    @Column(name = "spotify_refresh_token")
    private String spotifyRefreshToken;

    /** Comma-separated list of email addresses for parent notifications. */
    @Column(name = "notification_emails", columnDefinition = "TEXT")
    private String notificationEmails;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Getters & setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSpotifyUserId() { return spotifyUserId; }
    public void setSpotifyUserId(String spotifyUserId) { this.spotifyUserId = spotifyUserId; }

    public String getSpotifyRefreshToken() { return spotifyRefreshToken; }
    public void setSpotifyRefreshToken(String spotifyRefreshToken) { this.spotifyRefreshToken = spotifyRefreshToken; }

    public String getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(String notificationEmails) { this.notificationEmails = notificationEmails; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
