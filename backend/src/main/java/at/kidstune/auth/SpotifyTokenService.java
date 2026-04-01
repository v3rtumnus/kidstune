package at.kidstune.auth;

import at.kidstune.auth.dto.TokenExchangeResponse;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Manages Spotify access and refresh tokens for each family.
 *
 * Responsibilities:
 *  - AES-256-GCM encrypt / decrypt of refresh tokens stored in DB
 *  - In-memory Caffeine cache of live access tokens keyed by familyId
 *  - Code-for-token exchange after OAuth PKCE callback
 *  - Proactive token refresh 5 minutes before expiry via @Scheduled
 *  - getValidAccessToken(familyId) always returns a non-expired token
 */
@Service
public class SpotifyTokenService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyTokenService.class);

    private static final int GCM_IV_LENGTH  = 12;   // bytes
    private static final int GCM_TAG_LENGTH = 128;  // bits

    /** BCrypt hash of "changeme" (cost 10) – used as placeholder for device-created families. */
    private static final String PLACEHOLDER_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SpotifyConfig spotifyConfig;
    private final FamilyRepository familyRepository;
    private final WebClient spotifyAccountsClient;
    private final WebClient spotifyApiClient;
    private final SecretKey encryptionKey;

    /** familyId → live access token + expiry. */
    final Cache<String, AccessTokenEntry> accessTokenCache = Caffeine.newBuilder()
            .maximumSize(500)
            .build();

    public SpotifyTokenService(
            SpotifyConfig spotifyConfig,
            FamilyRepository familyRepository,
            WebClient.Builder webClientBuilder,
            @Value("${kidstune.jwt-secret}") String jwtSecret) {

        this.spotifyConfig = spotifyConfig;
        this.familyRepository = familyRepository;
        this.spotifyAccountsClient = webClientBuilder
                .baseUrl(spotifyConfig.getAccountsBaseUrl())
                .build();
        this.spotifyApiClient = webClientBuilder
                .baseUrl(spotifyConfig.getApiBaseUrl())
                .build();
        this.encryptionKey = deriveKey(jwtSecret);
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    /**
     * Encrypts {@code plaintext} with AES-256-GCM.
     * Output format: Base64(IV[12] || ciphertext+tag).
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertextWithTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertextWithTag, 0, combined, iv.length, ciphertextWithTag.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value produced by {@link #encrypt(String)}.
     */
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Token exchange ────────────────────────────────────────────────────────

    /**
     * Exchanges an authorization code for access + refresh tokens, then:
     * <ol>
     *   <li>Fetches the Spotify user ID via GET /v1/me</li>
     *   <li>Creates or updates the {@link Family} row in the DB</li>
     *   <li>Caches the access token</li>
     * </ol>
     *
     * @return the stored Family ID
     */
    public Mono<String> exchangeCodeAndPersist(String code, String codeVerifier) {
        return exchangeCodeAndPersist(code, codeVerifier, spotifyConfig.getRedirectUri());
    }

    public Mono<String> exchangeCodeAndPersist(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "authorization_code");
        form.add("code",          code);
        form.add("redirect_uri",  redirectUri);
        form.add("client_id",     spotifyConfig.getClientId());
        form.add("code_verifier", codeVerifier);

        return spotifyAccountsClient.post()
                .uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenExchangeResponse.class)
                .flatMap(tokenResponse -> fetchUserIdAndPersist(tokenResponse));
    }

    private Mono<String> fetchUserIdAndPersist(TokenExchangeResponse tokenResponse) {
        return spotifyApiClient.get()
                .uri("/v1/me")
                .header("Authorization", "Bearer " + tokenResponse.accessToken())
                .retrieve()
                .bodyToMono(at.kidstune.auth.dto.SpotifyUserProfile.class)
                .flatMap(profile -> persistFamily(profile.id(), tokenResponse));
    }

    private Mono<String> persistFamily(String spotifyUserId, TokenExchangeResponse tokenResponse) {
        return Mono.fromCallable(() -> {
            Family family = familyRepository.findBySpotifyUserId(spotifyUserId)
                    .orElseGet(() -> {
                        // Device-initiated Spotify login: set placeholder credentials.
                        // The parent can later complete their account via the web dashboard.
                        Family f = new Family();
                        f.setEmail("spotify." + spotifyUserId + "@kidstune.placeholder");
                        f.setPasswordHash(PLACEHOLDER_PASSWORD_HASH);
                        return f;
                    });

            family.setSpotifyUserId(spotifyUserId);
            family.setSpotifyRefreshToken(encrypt(tokenResponse.refreshToken()));
            family = familyRepository.save(family);

            // Cache the live access token
            Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
            accessTokenCache.put(family.getId(), new AccessTokenEntry(tokenResponse.accessToken(), expiresAt));

            return family.getId();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Settings: connect Spotify to an existing family ───────────────────────

    /**
     * Called from the web-dashboard settings page after the parent completes Spotify OAuth.
     * Exchanges the code, fetches the Spotify user ID, and stores both on the given family.
     *
     * @param familyId    the already-authenticated family's ID
     * @param code        the authorization code from Spotify's callback
     * @param codeVerifier the PKCE verifier stored in the session during the OAuth flow
     * @param redirectUri  the redirect_uri that was registered with Spotify for this flow
     */
    public Mono<Void> connectSpotifyToFamily(String familyId, String code,
                                             String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "authorization_code");
        form.add("code",          code);
        form.add("redirect_uri",  redirectUri);
        form.add("client_id",     spotifyConfig.getClientId());
        form.add("code_verifier", codeVerifier);

        return spotifyAccountsClient.post()
                .uri("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenExchangeResponse.class)
                .flatMap(tokenResponse ->
                        spotifyApiClient.get()
                                .uri("/v1/me")
                                .header("Authorization", "Bearer " + tokenResponse.accessToken())
                                .retrieve()
                                .bodyToMono(at.kidstune.auth.dto.SpotifyUserProfile.class)
                                .flatMap(profile -> Mono.fromCallable(() -> {
                                    Family family = familyRepository.findById(familyId)
                                            .orElseThrow(() -> new IllegalArgumentException(
                                                    "Family not found: " + familyId));
                                    family.setSpotifyUserId(profile.id());
                                    family.setSpotifyRefreshToken(encrypt(tokenResponse.refreshToken()));
                                    familyRepository.save(family);

                                    Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
                                    accessTokenCache.put(familyId,
                                            new AccessTokenEntry(tokenResponse.accessToken(), expiresAt));
                                    return (Void) null;
                                }).subscribeOn(Schedulers.boundedElastic()))
                );
    }

    // ── Token access & refresh ────────────────────────────────────────────────

    /**
     * Returns a current access token for {@code familyId}, refreshing
     * from the stored refresh token if the cached token is missing or expired.
     */
    public Mono<String> getValidAccessToken(String familyId) {
        AccessTokenEntry cached = accessTokenCache.getIfPresent(familyId);
        if (cached != null && !cached.isExpiredOrExpiringSoon()) {
            return Mono.just(cached.accessToken());
        }
        return refreshAccessToken(familyId);
    }

    /**
     * Returns the cached expiry for {@code familyId}, or empty if not cached.
     */
    public Instant getCachedExpiry(String familyId) {
        AccessTokenEntry entry = accessTokenCache.getIfPresent(familyId);
        return entry == null ? null : entry.expiresAt();
    }

    Mono<String> refreshAccessToken(String familyId) {
        return Mono.fromCallable(() ->
                familyRepository.findById(familyId)
                        .orElseThrow(() -> new IllegalArgumentException("Family not found: " + familyId))
                        .getSpotifyRefreshToken()
        )
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(encryptedRefreshToken -> {
            String refreshToken = decrypt(encryptedRefreshToken);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type",    "refresh_token");
            form.add("refresh_token", refreshToken);
            form.add("client_id",     spotifyConfig.getClientId());

            return spotifyAccountsClient.post()
                    .uri("/api/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenExchangeResponse.class);
        })
        .flatMap(tokenResponse -> {
            Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
            accessTokenCache.put(familyId, new AccessTokenEntry(tokenResponse.accessToken(), expiresAt));

            // Spotify may rotate the refresh token on refresh — persist if so
            if (tokenResponse.refreshToken() != null) {
                return Mono.fromCallable(() -> {
                    familyRepository.findById(familyId).ifPresent(family -> {
                        family.setSpotifyRefreshToken(encrypt(tokenResponse.refreshToken()));
                        familyRepository.save(family);
                    });
                    return tokenResponse.accessToken();
                }).subscribeOn(Schedulers.boundedElastic());
            }
            return Mono.just(tokenResponse.accessToken());
        });
    }

    /**
     * Runs every minute. Proactively refreshes tokens expiring within 5 minutes.
     */
    @Scheduled(fixedRate = 60_000)
    public void refreshExpiringTokens() {
        Instant threshold = Instant.now().plus(5, ChronoUnit.MINUTES);
        accessTokenCache.asMap().forEach((familyId, entry) -> {
            if (entry.expiresAt().isBefore(threshold)) {
                log.debug("Proactively refreshing token for family {}", familyId);
                refreshAccessToken(familyId)
                        .doOnError(e -> log.warn("Proactive refresh failed for family {}: {}", familyId, e.getMessage()))
                        .subscribe();
            }
        });
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    record AccessTokenEntry(String accessToken, Instant expiresAt) {
        /** True if expired or within 10 seconds of expiry. */
        boolean isExpiredOrExpiringSoon() {
            return expiresAt.isBefore(Instant.now().plusSeconds(10));
        }
    }
}
