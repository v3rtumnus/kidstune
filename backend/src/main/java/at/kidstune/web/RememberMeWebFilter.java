package at.kidstune.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Reads the {@code remember_me} cookie, validates the persistent token stored in the DB,
 * and – if valid – puts the {@code familyId} into the WebSession so that
 * {@link WebSessionSecurityContextRepository} can then load the security context normally.
 *
 * Uses the series + token approach (inspired by Spring Security's
 * {@code PersistentTokenBasedRememberMeServices}):
 * <ul>
 *   <li>Cookie value: {@code <series>:<rawToken>} (both base64url-encoded)</li>
 *   <li>Only {@code SHA-256(rawToken)} is stored – never the raw value.</li>
 *   <li>On each successful use: token is rotated (new rawToken, same series).</li>
 *   <li>Series collision (correct series, wrong token) → token theft assumed →
 *       all remember_me tokens for the family are deleted.</li>
 * </ul>
 */
@Component
public class RememberMeWebFilter implements WebFilter {

    static final String COOKIE_NAME   = "remember_me";
    static final Duration COOKIE_TTL  = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(RememberMeWebFilter.class);

    private final RememberMeTokenRepository tokenRepository;
    private final RememberMeTokenService    tokenService;

    public RememberMeWebFilter(RememberMeTokenRepository tokenRepository,
                               RememberMeTokenService tokenService) {
        this.tokenRepository = tokenRepository;
        this.tokenService    = tokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (cookie == null) {
            return chain.filter(exchange);
        }

        String cookieValue = cookie.getValue();
        int sep = cookieValue.lastIndexOf(':');
        if (sep < 1 || sep >= cookieValue.length() - 1) {
            clearCookie(exchange);
            return chain.filter(exchange);
        }

        String series   = cookieValue.substring(0, sep);
        String rawToken = cookieValue.substring(sep + 1);

        return exchange.getSession().flatMap(session -> {
            // If the session already contains a familyId, remember-me is not needed.
            if (session.getAttribute(WebSessionSecurityContextRepository.SESSION_FAMILY_ID) != null) {
                return chain.filter(exchange);
            }

            return Mono.fromCallable(() -> tokenRepository.findBySeries(series))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(opt -> {
                        if (opt.isEmpty()) {
                            // Unknown series – someone sent a stale or forged cookie.
                            clearCookie(exchange);
                            return chain.filter(exchange);
                        }

                        RememberMeToken stored = opt.get();

                        if (stored.getExpiresAt().isBefore(Instant.now())) {
                            return Mono.fromCallable(() -> { tokenService.deleteToken(stored); return null; })
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnTerminate(() -> clearCookie(exchange))
                                    .then(chain.filter(exchange));
                        }

                        String expectedHash = sha256Hex(rawToken);
                        if (!expectedHash.equals(stored.getTokenHash())) {
                            // Series matches but token doesn't → cookie theft detected.
                            log.warn("Remember-me token theft suspected for family {}; "
                                    + "invalidating all persistent tokens.", stored.getFamilyId());
                            return Mono.fromCallable(() -> { tokenService.deleteAllForFamily(stored.getFamilyId()); return null; })
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnTerminate(() -> clearCookie(exchange))
                                    .then(chain.filter(exchange));
                        }

                        // ── Valid token: rotate and populate session ────────────────────
                        String newRawToken = generateToken();
                        String newHash     = sha256Hex(newRawToken);
                        Instant now        = Instant.now();
                        stored.setTokenHash(newHash);
                        stored.setCreatedAt(now);
                        stored.setExpiresAt(now.plus(COOKIE_TTL));

                        String familyId = stored.getFamilyId();

                        return Mono.fromCallable(() -> {
                            tokenRepository.save(stored);
                            return familyId;
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(fid -> {
                            setCookie(exchange, series, newRawToken);
                            session.getAttributes().put(
                                    WebSessionSecurityContextRepository.SESSION_FAMILY_ID, fid);
                            return chain.filter(exchange);
                        });
                    });
        });
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    void setCookie(ServerWebExchange exchange, String series, String rawToken) {
        exchange.getResponse().addCookie(
                ResponseCookie.from(COOKIE_NAME, series + ":" + rawToken)
                        .httpOnly(true)
                        .path("/")
                        .maxAge(COOKIE_TTL)
                        .sameSite("Strict")
                        .build()
        );
    }

    private void clearCookie(ServerWebExchange exchange) {
        exchange.getResponse().addCookie(
                ResponseCookie.from(COOKIE_NAME, "")
                        .httpOnly(true)
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .sameSite("Strict")
                        .build()
        );
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}