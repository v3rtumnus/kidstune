package at.kidstune.web;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Controller
@RequestMapping("/web")
public class WebLoginController {

    private static final Logger log = LoggerFactory.getLogger(WebLoginController.class);

    private static final String[] SCOPES = {
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-library-read",
            "user-read-recently-played",
            "playlist-read-private",
            "streaming",
            "user-read-private"
    };

    private static final String PKCE_PREFIX = "pkce_";

    private final SpotifyTokenService tokenService;
    private final SpotifyConfig       spotifyConfig;

    public WebLoginController(SpotifyTokenService tokenService, SpotifyConfig spotifyConfig) {
        this.tokenService  = tokenService;
        this.spotifyConfig = spotifyConfig;
    }

    // ── GET /web/login ───────────────────────────────────────────────────────

    @GetMapping("/login")
    public Mono<String> loginPage() {
        return Mono.just("web/login");
    }

    // ── GET /web/auth/spotify-login ──────────────────────────────────────────
    // Generates a PKCE challenge, stores the verifier in the session, and
    // redirects the browser to Spotify's authorization page.

    @GetMapping("/auth/spotify-login")
    public Mono<Void> spotifyLogin(ServerWebExchange exchange) {
        String codeVerifier  = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state         = UUID.randomUUID().toString();

        return exchange.getSession().flatMap(session -> {
            session.getAttributes().put(PKCE_PREFIX + state, codeVerifier);

            String authUrl = spotifyConfig.getAccountsBaseUrl() + "/authorize?" +
                    "client_id="             + encode(spotifyConfig.getClientId()) +
                    "&response_type=code" +
                    "&redirect_uri="         + encode(spotifyConfig.getWebRedirectUri()) +
                    "&scope="                + encode(String.join(" ", SCOPES)) +
                    "&state="                + encode(state) +
                    "&code_challenge_method=S256" +
                    "&code_challenge="       + encode(codeChallenge);

            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(authUrl));
            return exchange.getResponse().setComplete();
        });
    }

    // ── GET /web/auth/callback ───────────────────────────────────────────────
    // Spotify redirects here after the user grants access.
    // Exchanges the authorization code for tokens, stores familyId in the
    // WebSession, and redirects to the dashboard.

    @GetMapping("/auth/callback")
    public Mono<Void> callback(
            @RequestParam("code")  String code,
            @RequestParam("state") String state,
            ServerWebExchange exchange) {

        return exchange.getSession().flatMap(session -> {
            String verifier = session.getAttribute(PKCE_PREFIX + state);
            if (verifier == null) {
                log.warn("Web OAuth callback: missing or expired PKCE state '{}'", state);
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create("/web/login?error=expired"));
                return exchange.getResponse().setComplete();
            }
            session.getAttributes().remove(PKCE_PREFIX + state);

            return tokenService.exchangeCodeAndPersist(code, verifier, spotifyConfig.getWebRedirectUri())
                    .flatMap(familyId -> exchange.getSession().flatMap(s -> {
                        s.getAttributes().put(WebSessionSecurityContextRepository.SESSION_FAMILY_ID, familyId);
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(URI.create("/web/dashboard"));
                        return exchange.getResponse().setComplete();
                    }))
                    .onErrorResume(e -> {
                        log.error("Web OAuth callback failed: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(URI.create("/web/login?error=auth_failed"));
                        return exchange.getResponse().setComplete();
                    });
        });
    }

    // ── POST /web/logout ─────────────────────────────────────────────────────

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            session.invalidate();
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/web/login"));
            return exchange.getResponse().setComplete();
        });
    }

    // ── PKCE helpers ─────────────────────────────────────────────────────────

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}