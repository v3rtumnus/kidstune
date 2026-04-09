package at.kidstune.config;

import at.kidstune.auth.JwtClaims;
import at.kidstune.auth.KidstuneAuthentication;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebFilter that adds structured MDC fields to every request for JSON log ingestion.
 *
 * <p>Fields added:
 * <ul>
 *   <li>{@code requestId}  – random UUID per request (also sent back as X-Request-Id header)</li>
 *   <li>{@code familyId}   – from the authenticated JWT (when present)</li>
 *   <li>{@code deviceId}   – from the authenticated JWT (when present)</li>
 * </ul>
 *
 * <p>Runs at order 1 – after Spring Security's filter chain (order -100) so the
 * security context is already populated when this filter reads it.
 */
@Component
@Order(1)
public class MdcWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> (KidstuneAuthentication) ctx.getAuthentication())
            .map(KidstuneAuthentication::getClaims)
            .defaultIfEmpty(new JwtClaims(null, null, null))
            .onErrorReturn(new JwtClaims(null, null, null))
            .flatMap(claims -> {
                Map<String, String> mdc = new HashMap<>();
                mdc.put("requestId", requestId);
                if (claims.familyId() != null) mdc.put("familyId", claims.familyId());
                if (claims.deviceId() != null) mdc.put("deviceId", claims.deviceId());
                MDC.setContextMap(mdc);
                return chain.filter(exchange)
                    .doFinally(sig -> MDC.clear());
            });
    }
}
