package at.kidstune.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    private String id;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String endpoint;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_agent")
    private String userAgent;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId()                { return id; }
    public void setId(String id)         { this.id = id; }

    public String getFamilyId()          { return familyId; }
    public void setFamilyId(String v)    { this.familyId = v; }

    public String getEndpoint()          { return endpoint; }
    public void setEndpoint(String v)    { this.endpoint = v; }

    public String getP256dh()            { return p256dh; }
    public void setP256dh(String v)      { this.p256dh = v; }

    public String getAuth()              { return auth; }
    public void setAuth(String v)        { this.auth = v; }

    public Instant getCreatedAt()        { return createdAt; }
    public void setCreatedAt(Instant v)  { this.createdAt = v; }

    public String getUserAgent()         { return userAgent; }
    public void setUserAgent(String v)   { this.userAgent = v; }
}
