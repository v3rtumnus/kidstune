package at.kidstune.push;

import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class VapidConfigTest {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    @DisplayName("generates key pair when properties are empty")
    void generatesKeyPairWhenPropertiesAreEmpty() throws Exception {
        VapidConfig config = new VapidConfig();
        setKeys(config, "", "");

        String pub  = config.vapidPublicKeyString();
        String priv = config.vapidPrivateKeyString();

        assertThat(pub).isNotBlank();
        assertThat(priv).isNotBlank();
    }

    @Test
    @DisplayName("generated public key is valid – loadable by Utils.loadPublicKey")
    void generatedPublicKeyIsLoadable() throws Exception {
        VapidConfig config = new VapidConfig();
        setKeys(config, "", "");
        String pub = config.vapidPublicKeyString();

        // Utils.loadPublicKey accepts base64url P-256 uncompressed point
        ECPublicKey ecKey = (ECPublicKey) Utils.loadPublicKey(pub);
        assertThat(ecKey).isNotNull();
        // Algorithm may be "EC" or "ECDH" depending on the JCA provider
        assertThat(ecKey.getAlgorithm()).containsIgnoringCase("EC");
    }

    @Test
    @DisplayName("generated private key is valid – loadable by Utils.loadPrivateKey")
    void generatedPrivateKeyIsLoadable() throws Exception {
        VapidConfig config = new VapidConfig();
        setKeys(config, "", "");
        config.vapidPublicKeyString(); // trigger generation
        String priv = config.vapidPrivateKeyString();

        ECPrivateKey ecKey = (ECPrivateKey) Utils.loadPrivateKey(priv);
        assertThat(ecKey).isNotNull();
        // Algorithm may be "EC" or "ECDH" depending on the JCA provider
        assertThat(ecKey.getAlgorithm()).containsIgnoringCase("EC");
    }

    @Test
    @DisplayName("generated public key base64url decodes to 65-byte uncompressed point")
    void generatedPublicKeyIs65Bytes() throws Exception {
        VapidConfig config = new VapidConfig();
        setKeys(config, "", "");
        String pub = config.vapidPublicKeyString();

        byte[] decoded = Base64.getUrlDecoder().decode(pub);
        assertThat(decoded).hasSize(65);
        assertThat(decoded[0]).isEqualTo((byte) 0x04); // uncompressed point prefix
    }

    @Test
    @DisplayName("loads key pair from configuration and round-trips correctly")
    void loadsKeyPairFromConfiguration() throws Exception {
        // Generate initial pair
        VapidConfig gen = new VapidConfig();
        setKeys(gen, "", "");
        String origPub  = gen.vapidPublicKeyString();
        String origPriv = gen.vapidPrivateKeyString();

        // Re-load from the encoded strings
        VapidConfig loader = new VapidConfig();
        setKeys(loader, origPub, origPriv);

        assertThat(loader.vapidPublicKeyString()).isEqualTo(origPub);
        assertThat(loader.vapidPrivateKeyString()).isEqualTo(origPriv);

        // Keys must still be loadable after round-trip
        assertThat(Utils.loadPublicKey(loader.vapidPublicKeyString())).isNotNull();
        assertThat(Utils.loadPrivateKey(loader.vapidPrivateKeyString())).isNotNull();
    }

    @Test
    @DisplayName("encodePublicKey produces the same result as manual uncompressed point encoding")
    void encodePublicKeyProducesUncompressedPoint() throws Exception {
        VapidConfig gen = new VapidConfig();
        setKeys(gen, "", "");
        String pubString = gen.vapidPublicKeyString();

        // Decode and verify structure
        byte[] decoded = Base64.getUrlDecoder().decode(pubString);
        assertThat(decoded[0]).isEqualTo((byte) 0x04);

        // Load back and re-encode – must be stable
        ECPublicKey ecKey = (ECPublicKey) Utils.loadPublicKey(pubString);
        String reEncoded = VapidConfig.encodePublicKey(ecKey);
        assertThat(reEncoded).isEqualTo(pubString);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setKeys(VapidConfig config, String pub, String priv) {
        ReflectionTestUtils.setField(config, "publicKeyBase64",  pub);
        ReflectionTestUtils.setField(config, "privateKeyBase64", priv);
    }
}
