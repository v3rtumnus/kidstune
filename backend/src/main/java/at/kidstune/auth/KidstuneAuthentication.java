package at.kidstune.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class KidstuneAuthentication extends AbstractAuthenticationToken {

    private final JwtClaims claims;

    public KidstuneAuthentication(JwtClaims claims) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + claims.deviceType().name())));
        this.claims = claims;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public Object getPrincipal() { return claims.familyId(); }

    public JwtClaims getClaims() { return claims; }
}
