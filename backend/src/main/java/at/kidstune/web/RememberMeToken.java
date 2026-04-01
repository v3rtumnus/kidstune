package at.kidstune.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "remember_me_token")
@Getter
@Setter
public class RememberMeToken {

    /** Random base64url series – serves as the stable cookie identifier. */
    @Id
    @Column(name = "series", length = 64, nullable = false)
    private String series;

    /** SHA-256 hex digest of the one-time random token value. */
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}