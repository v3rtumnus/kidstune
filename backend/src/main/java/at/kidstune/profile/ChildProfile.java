package at.kidstune.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "child_profile")
public class ChildProfile {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_icon", nullable = false, length = 50)
    private AvatarIcon avatarIcon;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_color", nullable = false, length = 7)
    private AvatarColor avatarColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false)
    private AgeGroup ageGroup;

    @Column(name = "spotify_user_id", length = 255)
    private String spotifyUserId;

    @Column(name = "spotify_refresh_token", columnDefinition = "TEXT")
    private String spotifyRefreshToken;

    @Column(name = "insights_status", length = 32)
    private String insightsStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AvatarIcon getAvatarIcon() { return avatarIcon; }
    public void setAvatarIcon(AvatarIcon avatarIcon) { this.avatarIcon = avatarIcon; }

    public AvatarColor getAvatarColor() { return avatarColor; }
    public void setAvatarColor(AvatarColor avatarColor) { this.avatarColor = avatarColor; }

    public AgeGroup getAgeGroup() { return ageGroup; }
    public void setAgeGroup(AgeGroup ageGroup) { this.ageGroup = ageGroup; }

    public String getSpotifyUserId() { return spotifyUserId; }
    public void setSpotifyUserId(String spotifyUserId) { this.spotifyUserId = spotifyUserId; }

    public String getSpotifyRefreshToken() { return spotifyRefreshToken; }
    public void setSpotifyRefreshToken(String spotifyRefreshToken) { this.spotifyRefreshToken = spotifyRefreshToken; }

    public String getInsightsStatus() { return insightsStatus; }
    public void setInsightsStatus(String insightsStatus) { this.insightsStatus = insightsStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
