package at.kidstune.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    static final String SECRET = "test-jwt-secret-32-characters-!!";

    JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET);
    }

    // ── createDeviceToken / validateToken round-trip ──────────────────────────

    @Test
    void createDeviceToken_roundTrip_returnsCorrectClaims() {
        String token = jwtTokenService.createDeviceToken("fam-1", "dev-1", DeviceType.PARENT);

        JwtClaims claims = jwtTokenService.validateToken(token);

        assertThat(claims.familyId()).isEqualTo("fam-1");
        assertThat(claims.deviceId()).isEqualTo("dev-1");
        assertThat(claims.deviceType()).isEqualTo(DeviceType.PARENT);
    }

    @Test
    void createDeviceToken_kidsType_roundTrip() {
        String token = jwtTokenService.createDeviceToken("fam-2", "dev-2", DeviceType.KIDS);

        JwtClaims claims = jwtTokenService.validateToken(token);

        assertThat(claims.deviceType()).isEqualTo(DeviceType.KIDS);
        assertThat(claims.familyId()).isEqualTo("fam-2");
    }

    // ── expired token ─────────────────────────────────────────────────────────

    @Test
    void validateToken_expiredToken_throwsJwtException() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(60);
        String expiredToken = Jwts.builder()
                .claim("familyId", "fam-1")
                .claim("deviceId", "dev-1")
                .claim("deviceType", DeviceType.PARENT.name())
                .issuedAt(Date.from(past.minusSeconds(120)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtTokenService.validateToken(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    // ── tampered token ────────────────────────────────────────────────────────

    @Test
    void validateToken_tamperedSignature_throwsJwtException() {
        String token = jwtTokenService.createDeviceToken("fam-1", "dev-1", DeviceType.PARENT);
        // Corrupt the last character of the signature segment
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> jwtTokenService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_tamperedPayload_throwsJwtException() {
        String token = jwtTokenService.createDeviceToken("fam-1", "dev-1", DeviceType.PARENT);
        // Replace the payload segment (index 1) with garbage
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".dGFtcGVyZWQ." + parts[2];

        assertThatThrownBy(() -> jwtTokenService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
