package at.kidstune.auth;

import at.kidstune.device.PairedDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Optional;

/**
 * Runs on every API request that carries a KIDS device token.
 * <ol>
 *   <li>Verifies the device still exists in the DB (enforces unpair revocation).</li>
 *   <li>Asynchronously updates {@code last_seen_at}.</li>
 * </ol>
 * <p>
 * Spring Boot auto-registers all {@code @Component WebFilter} beans globally, so this
 * filter runs twice per request: once in the global chain (before JWT auth) and once
 * inside the security chain (after JWT auth). We resolve this correctly by using
 * {@code Optional} as a non-null carrier with {@code defaultIfEmpty}, so that
 * {@code flatMap} always receives a value and there is no {@code switchIfEmpty} on a
 * {@code Mono<Void>} (which would cause a second chain invocation).
 * </p>
 */
@Component
public class LastSeenFilter implements WebFilter {

    private static final Optional<KidstuneAuthentication> NO_KIDS_AUTH = Optional.empty();

    private final PairedDeviceRepository pairedDeviceRepository;
    private final TransactionTemplate transactionTemplate;

    public LastSeenFilter(PairedDeviceRepository pairedDeviceRepository,
                          TransactionTemplate transactionTemplate) {
        this.pairedDeviceRepository = pairedDeviceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                // Extract KIDS auth as Optional, or emit NO_KIDS_AUTH if wrong type / device type
                .<Optional<KidstuneAuthentication>>handle((ctx, sink) -> {
                    if (ctx.getAuthentication() instanceof KidstuneAuthentication ka
                            && ka.getClaims().deviceType() == DeviceType.KIDS) {
                        sink.next(Optional.of(ka));
                    } else {
                        sink.next(NO_KIDS_AUTH);
                    }
                })
                // When there is no security context at all (global filter run before JWT auth)
                .defaultIfEmpty(NO_KIDS_AUTH)
                .flatMap(optAuth -> {
                    if (optAuth.isEmpty()) {
                        // No KIDS auth in context — continue without device check
                        return chain.filter(exchange);
                    }
                    String deviceId = optAuth.get().getClaims().deviceId();
                    return Mono.fromCallable(() -> pairedDeviceRepository.existsById(deviceId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(exists -> {
                                if (!exists) {
                                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return exchange.getResponse().setComplete();
                                }
                                // Fire-and-forget last_seen_at update
                                Mono.fromRunnable(() ->
                                        transactionTemplate.execute(status -> {
                                            pairedDeviceRepository.updateLastSeenAt(deviceId, Instant.now());
                                            return null;
                                        })
                                ).subscribeOn(Schedulers.boundedElastic()).subscribe();
                                return chain.filter(exchange);
                            });
                });
    }
}
