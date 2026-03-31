package at.kidstune.web;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtClaims;
import at.kidstune.auth.KidstuneAuthentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Loads the web-session security context from the {@code familyId} WebSession attribute
 * set by {@link WebLoginController} after a successful Spotify OAuth flow.
 *
 * Registered as the {@code securityContextRepository} for the web filter chain so that
 * {@code SecurityContextServerWebExchangeWebFilter} populates the Reactor context before
 * {@code AuthorizationWebFilter} runs.  Saving is a no-op because we manage the session
 * attribute ourselves.
 */
@Component
public class WebSessionSecurityContextRepository implements ServerSecurityContextRepository {

    /** WebSession attribute key that holds the authenticated family's ID. */
    static final String SESSION_FAMILY_ID = "familyId";

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> {
                    String familyId = session.getAttribute(SESSION_FAMILY_ID);
                    if (familyId == null) {
                        return Mono.empty();
                    }
                    KidstuneAuthentication auth = new KidstuneAuthentication(
                            new JwtClaims(familyId, "web", DeviceType.PARENT));
                    return Mono.just((SecurityContext) new SecurityContextImpl(auth));
                });
    }
}
