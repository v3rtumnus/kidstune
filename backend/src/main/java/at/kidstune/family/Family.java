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

    /** KidsTune dashboard login e-mail (unique). */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash of the dashboard password. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Spotify user ID – nullable; set once the parent connects their Spotify account. */
    @Column(name = "spotify_user_id", unique = true, length = 255)
    private String spotifyUserId;

    /** AES-256-GCM encrypted Spotify refresh token – nullable. */
    @Column(name = "spotify_refresh_token")
    private String spotifyRefreshToken;

    /** Comma-separated list of email addresses for parent notifications. */
    @Column(name = "notification_emails", columnDefinition = "TEXT")
    private String notificationEmails;

    /** BCrypt hash of the 4-digit quick-approval PIN – nullable; absent means feature is off. */
    @Column(name = "approval_pin_hash", length = 255)
    private String approvalPinHash;

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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSpotifyUserId() { return spotifyUserId; }
    public void setSpotifyUserId(String spotifyUserId) { this.spotifyUserId = spotifyUserId; }

    public String getSpotifyRefreshToken() { return spotifyRefreshToken; }
    public void setSpotifyRefreshToken(String spotifyRefreshToken) { this.spotifyRefreshToken = spotifyRefreshToken; }

    public String getNotificationEmails() { return notificationEmails; }
    public void setNotificationEmails(String notificationEmails) { this.notificationEmails = notificationEmails; }

    public String getApprovalPinHash() { return approvalPinHash; }
    public void setApprovalPinHash(String approvalPinHash) { this.approvalPinHash = approvalPinHash; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}