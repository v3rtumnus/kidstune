package at.kidstune.auth;

import at.kidstune.family.FamilyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for SpotifyTokenService.
 * No Spring context – verifies crypto and cache logic directly.
 */
class SpotifyTokenServiceTest {

    private SpotifyTokenService service;

    @BeforeEach
    void setUp() {
        SpotifyConfig config = new SpotifyConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRedirectUri("http://localhost/callback");
        config.setAccountsBaseUrl("https://accounts.spotify.com");
        config.setApiBaseUrl("https://api.spotify.com");

        service = new SpotifyTokenService(
                config,
                mock(FamilyRepository.class),
                WebClient.builder(),
                "test-secret-minimum-32-chars-ok!!"
        );
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    @Test
    void encrypt_and_decrypt_roundtrip() {
        String original = "spotify:refresh-token:abc123xyz";

        String encrypted = service.encrypt(original);
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_produces_different_ciphertext_each_time() {
        String input = "same-token-value";

        String first  = service.encrypt(input);
        String second = service.encrypt(input);

        // Random IV means ciphertexts differ even for the same plaintext
        assertThat(first).isNotEqualTo(second);
        // But both decrypt to the original
        assertThat(service.decrypt(first)).isEqualTo(input);
        assertThat(service.decrypt(second)).isEqualTo(input);
    }

    @Test
    void encrypt_output_is_base64() {
        String encrypted = service.encrypt("some-token");
        // Must be valid base64 (no exception thrown)
        byte[] decoded = java.util.Base64.getDecoder().decode(encrypted);
        // At minimum: 12-byte IV + 16-byte GCM tag = 28 bytes
        assertThat(decoded.length).isGreaterThanOrEqualTo(28);
    }

    @Test
    void decrypt_with_wrong_key_throws() {
        SpotifyConfig config2 = new SpotifyConfig();
        config2.setClientId("c");
        config2.setClientSecret("s");
        config2.setRedirectUri("r");
        config2.setAccountsBaseUrl("https://accounts.spotify.com");
        config2.setApiBaseUrl("https://api.spotify.com");

        SpotifyTokenService otherService = new SpotifyTokenService(
                config2,
                mock(FamilyRepository.class),
                WebClient.builder(),
                "completely-different-key-32chars!"
        );

        String encrypted = service.encrypt("secret-value");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> otherService.decrypt(encrypted)
        );
    }

    // ── AccessTokenEntry expiry ────────────────────────────────────────────────

    @Test
    void accessTokenEntry_not_expiring_soon_when_far_in_future() {
        SpotifyTokenService.AccessTokenEntry entry = new SpotifyTokenService.AccessTokenEntry(
                "access-token",
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
        assertThat(entry.isExpiredOrExpiringSoon()).isFalse();
    }

    @Test
    void accessTokenEntry_expiring_soon_when_within_10_seconds() {
        SpotifyTokenService.AccessTokenEntry entry = new SpotifyTokenService.AccessTokenEntry(
                "access-token",
                Instant.now().plusSeconds(5)  // expires in 5 seconds
        );
        assertThat(entry.isExpiredOrExpiringSoon()).isTrue();
    }

    @Test
    void accessTokenEntry_expiring_soon_when_already_expired() {
        SpotifyTokenService.AccessTokenEntry entry = new SpotifyTokenService.AccessTokenEntry(
                "access-token",
                Instant.now().minusSeconds(60)  // expired 60 seconds ago
        );
        assertThat(entry.isExpiredOrExpiringSoon()).isTrue();
    }

    // ── Scheduled refresh detection ────────────────────────────────────────────

    @Test
    void refreshExpiringTokens_does_not_throw_when_cache_is_empty() {
        // Purely verifies the scheduled method handles an empty cache without errors
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.refreshExpiringTokens()
        );
    }

    @Test
    void refreshExpiringTokens_skips_tokens_with_ample_expiry() {
        // Put a healthy token in the cache
        service.accessTokenCache.put("family-1", new SpotifyTokenService.AccessTokenEntry(
                "token", Instant.now().plus(2, ChronoUnit.HOURS)
        ));

        // Should not trigger any refresh (no network calls, so no error)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.refreshExpiringTokens()
        );
        // Token is still in cache after the call
        assertThat(service.accessTokenCache.getIfPresent("family-1")).isNotNull();
    }
}
