package at.kidstune.web;

import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtClaims;
import at.kidstune.auth.KidstuneAuthentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reads {@code familyId} from the WebSession and sets up a Spring Security context
 * for all requests under {@code /web/**}.
 *
 * The JWT filter handles {@code /api/**} authentication separately; this filter
 * is a no-op for non-web paths.
 */
@Component
public class WebSessionAuthFilter implements WebFilter {

    static final String SESSION_FAMILY_ID = "familyId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/web/")) {
            return chain.filter(exchange);
        }

        return exchange.getSession().flatMap(session -> {
            String familyId = session.getAttribute(SESSION_FAMILY_ID);
            if (familyId == null) {
                return chain.filter(exchange);
            }
            KidstuneAuthentication auth = new KidstuneAuthentication(
                    new JwtClaims(familyId, "web", DeviceType.PARENT));
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        });
    }
}