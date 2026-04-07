package at.kidstune.push;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import java.util.Base64;

/**
 * Provides VAPID key material as base64url-encoded string beans.
 *
 * <p>Set {@code kidstune.vapid.public-key} and {@code kidstune.vapid.private-key} to
 * base64url-encoded P-256 key material.  If either property is absent a new key pair is
 * generated on startup and the values are logged so the operator can persist them.
 *
 * <p>Exposes two named String beans – {@code "vapidPublicKeyString"} and
 * {@code "vapidPrivateKeyString"} – so consumers can inject the correct one via
 * {@code @Qualifier}.
 */
@Configuration
public class VapidConfig {

    private static final Logger log = LoggerFactory.getLogger(VapidConfig.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${kidstune.vapid.public-key:}")
    private String publicKeyBase64;

    @Value("${kidstune.vapid.private-key:}")
    private String privateKeyBase64;

    @Bean("vapidPublicKeyString")
    public String vapidPublicKeyString() throws Exception {
        ensureKeys();
        return publicKeyBase64;
    }

    @Bean("vapidPrivateKeyString")
    public String vapidPrivateKeyString() throws Exception {
        ensureKeys();
        return privateKeyBase64;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private synchronized void ensureKeys() throws Exception {
        if (!publicKeyBase64.isBlank() && !privateKeyBase64.isBlank()) return;

        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();

        publicKeyBase64  = encodePublicKey((ECPublicKey)  kp.getPublic());
        privateKeyBase64 = encodePrivateKey((ECPrivateKey) kp.getPrivate());

        log.warn("VAPID key pair not configured – generated ephemeral pair (will change on restart!).");
        log.warn("Add to application.yml / environment to persist:");
        log.warn("  kidstune.vapid.public-key={}",  publicKeyBase64);
        log.warn("  kidstune.vapid.private-key={}", privateKeyBase64);
    }

    /**
     * Encodes an EC public key as an uncompressed P-256 point: 0x04 || X (32 bytes) || Y (32 bytes),
     * then base64url-encodes the result (no padding).
     */
    static String encodePublicKey(ECPublicKey key) {
        ECPoint w = key.getW();
        byte[] x = toBytes32(w.getAffineX());
        byte[] y = toBytes32(w.getAffineY());
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed,  1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    }

    /**
     * Encodes an EC private key as the raw 32-byte scalar, then base64url-encodes.
     */
    static String encodePrivateKey(ECPrivateKey key) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toBytes32(key.getS()));
    }

    /** Converts a {@link BigInteger} to exactly 32 bytes, trimming or padding as needed. */
    static byte[] toBytes32(BigInteger n) {
        byte[] raw = n.toByteArray();
        if (raw.length == 33 && raw[0] == 0) {
            return Arrays.copyOfRange(raw, 1, 33);
        }
        if (raw.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
            return padded;
        }
        return raw;
    }
}
