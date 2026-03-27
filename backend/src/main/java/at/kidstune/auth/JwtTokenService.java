package at.kidstune.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    static final long TOKEN_LIFETIME_SECONDS = 30L * 24 * 3600; // 30 days

    private final SecretKey signingKey;

    public JwtTokenService(@Value("${kidstune.jwt-secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues a long-lived (30-day) device token.
     */
    public String createDeviceToken(String familyId, String deviceId, DeviceType deviceType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("familyId", familyId)
                .claim("deviceId", deviceId)
                .claim("deviceType", deviceType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TOKEN_LIFETIME_SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token and returns its claims.
     *
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public JwtClaims validateToken(String jwt) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        String familyId    = claims.get("familyId",    String.class);
        String deviceId    = claims.get("deviceId",    String.class);
        DeviceType devType = DeviceType.valueOf(claims.get("deviceType", String.class));

        return new JwtClaims(familyId, deviceId, devType);
    }
}
