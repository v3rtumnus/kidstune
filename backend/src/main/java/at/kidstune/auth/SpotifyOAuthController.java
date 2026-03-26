package at.kidstune.auth;

import at.kidstune.auth.dto.AuthStatusResponse;
import at.kidstune.auth.dto.CallbackResponse;
import at.kidstune.family.FamilyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/auth")
public class SpotifyOAuthController {

    private static final Logger log = LoggerFactory.getLogger(SpotifyOAuthController.class);

    private static final String[] SCOPES = {
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-library-read",
            "user-read-recently-played",
            "playlist-read-private",
            "streaming",
            "user-read-private"   // required for GET /v1/me identity call
    };

    /** Temporary store of state → code_verifier, TTL 10 minutes. */
    private final Cache<String, String> pkceStateCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final SpotifyConfig       spotifyConfig;
    private final SpotifyTokenService tokenService;
    private final FamilyRepository    familyRepository;

    public SpotifyOAuthController(
            SpotifyConfig spotifyConfig,
            SpotifyTokenService tokenService,
            FamilyRepository familyRepository) {
        this.spotifyConfig    = spotifyConfig;
        this.tokenService     = tokenService;
        this.familyRepository = familyRepository;
    }

    // ── GET /api/v1/auth/spotify/login ───────────────────────────────────────

    /**
     * Generates a PKCE code challenge, stores the verifier keyed by {@code state},
     * and redirects the client to Spotify's authorization page.
     */
    @GetMapping("/spotify/login")
    public Mono<ResponseEntity<Void>> login() {
        String codeVerifier  = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state         = UUID.randomUUID().toString();

        pkceStateCache.put(state, codeVerifier);

        String authUrl = spotifyConfig.getAccountsBaseUrl() + "/authorize?" +
                "client_id="             + encode(spotifyConfig.getClientId()) +
                "&response_type=code" +
                "&redirect_uri="         + encode(spotifyConfig.getRedirectUri()) +
                "&scope="                + encode(String.join(" ", SCOPES)) +
                "&state="                + encode(state) +
                "&code_challenge_method=S256" +
                "&code_challenge="       + encode(codeChallenge);

        return Mono.just(
                ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(authUrl))
                        .build()
        );
    }

    // ── GET /api/v1/auth/spotify/callback ────────────────────────────────────

    /**
     * Spotify redirects here after the user grants access.
     * Exchanges the code for tokens, creates/updates the Family record, and returns
     * the familyId and a live access token.
     *
     * The Parent App uses a Custom Tab pointing at /login; this endpoint is the
     * redirect_uri that Spotify calls back, so it must be a GET with query params.
     */
    @GetMapping("/spotify/callback")
    public Mono<ResponseEntity<CallbackResponse>> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {

        String codeVerifier = pkceStateCache.getIfPresent(state);
        if (codeVerifier == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid or expired OAuth state. Please restart the login flow."));
        }
        pkceStateCache.invalidate(state);

        return tokenService.exchangeCodeAndPersist(code, codeVerifier)
                .flatMap(familyId ->
                        tokenService.getValidAccessToken(familyId)
                                .map(accessToken -> {
                                    Instant expiresAt = tokenService.getCachedExpiry(familyId);
                                    int expiresIn = expiresAt != null
                                            ? (int) (expiresAt.getEpochSecond() - Instant.now().getEpochSecond())
                                            : 3600;
                                    return ResponseEntity.ok(new CallbackResponse(familyId, accessToken, expiresIn));
                                })
                )
                .onErrorMap(e -> !(e instanceof ResponseStatusException), e ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Spotify token exchange failed: " + e.getMessage()));
    }

    // ── GET /api/v1/auth/status ──────────────────────────────────────────────

    /**
     * Returns the Spotify connection status for a family.
     * Accepts an optional {@code X-Family-Id} header; if absent returns unauthenticated.
     * JWT authentication is wired in prompt 1.4.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<AuthStatusResponse>> status(
            @RequestHeader(name = "X-Family-Id", required = false) String familyId) {

        if (familyId == null || familyId.isBlank()) {
            return Mono.just(ResponseEntity.ok(AuthStatusResponse.unauthenticated()));
        }

        return Mono.fromCallable(() ->
                familyRepository.findById(familyId)
        )
        .subscribeOn(Schedulers.boundedElastic())
        .map(optFamily -> {
            if (optFamily.isEmpty()) {
                return ResponseEntity.ok(AuthStatusResponse.unauthenticated());
            }
            boolean connected = optFamily.get().getSpotifyRefreshToken() != null;
            Instant expiresAt = tokenService.getCachedExpiry(familyId);
            return ResponseEntity.ok(new AuthStatusResponse(true, connected, familyId, expiresAt));
        });
    }

    // ── PKCE helpers ─────────────────────────────────────────────────────────

    /** Generates a 32-byte random code verifier (base64url, no padding). */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the verifier, base64url-encoded without padding (S256 method). */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
