package at.kidstune.push;

import at.kidstune.content.SpotifyApiClient;
import at.kidstune.content.ContentType;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PushNotificationIntTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("kidstune")
            .withUsername("kidstune")
            .withPassword("kidstune");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spotify.client-id",     () -> "test-client-id");
        registry.add("spotify.client-secret", () -> "test-client-secret");
        registry.add("kidstune.jwt-secret",   () -> "test-jwt-secret-32-characters-!!");
        registry.add("kidstune.base-url",     () -> "http://localhost");
    }

    @MockitoBean SpotifyApiClient spotifyApiClient;
    @MockitoBean JavaMailSender   mailSender;

    @Autowired PushSubscriptionRepository subRepository;
    @Autowired PushNotificationService    pushService;
    @Autowired ProfileRepository          profileRepository;
    @Autowired FamilyRepository           familyRepository;
    @Autowired ContentRequestRepository   requestRepository;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("push-test@kidstune.test");
            f.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            familyRepository.save(f);
        }
        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Max");
            p.setAvatarIcon(AvatarIcon.BEAR);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.PRESCHOOL);
            profileRepository.save(p);
        }

        subRepository.deleteAll(subRepository.findByFamilyId(FAMILY_ID));

        when(spotifyApiClient.getAlbumUriForTrack(any())).thenReturn(Mono.just("spotify:album:noop"));
        when(spotifyApiClient.getArtistUrisForTrack(any())).thenReturn(Mono.just(List.of()));
        when(spotifyApiClient.getTrackUrisInPlaylist(any())).thenReturn(Mono.just(List.of()));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void subscribe_create_request_push_payload_delivered() throws Exception {
        // Arrange – register a subscription pointing to the mock server
        mockServer.enqueue(new MockResponse().setResponseCode(201));
        PushSubscription sub = buildFakeSubscription(FAMILY_ID,
                mockServer.url("/push").toString());
        subRepository.save(sub);

        ContentRequest request = new ContentRequest();
        request.setProfileId(PROFILE_ID);
        request.setSpotifyUri("spotify:album:push-test");
        request.setTitle("Bibi und Tina");
        request.setArtistName("Europa");
        request.setContentType(ContentType.AUDIOBOOK);

        // Act – call the service directly (synchronous in test)
        pushService.sendToSubscription(sub, buildPayloadJson("Max", "Bibi und Tina"));

        // Assert – mock server received a POST with correct JSON payload
        RecordedRequest recorded = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");

        // Body may be encrypted, but we can at least verify the request was made
        assertThat(recorded.getBodySize()).isGreaterThan(0);
    }

    @Test
    void expired_endpoint_410_removes_subscription() throws Exception {
        // Arrange – mock server returns 410 Gone
        mockServer.enqueue(new MockResponse().setResponseCode(410));
        PushSubscription sub = buildFakeSubscription(FAMILY_ID,
                mockServer.url("/push").toString());
        subRepository.save(sub);
        String subId = sub.getId();

        // Act
        pushService.sendToSubscription(sub, "{\"title\":\"test\"}");

        // Assert – subscription deleted
        assertThat(subRepository.findById(subId)).isEmpty();
    }

    @Test
    void endpoint_404_also_removes_subscription() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(404));
        PushSubscription sub = buildFakeSubscription(FAMILY_ID,
                mockServer.url("/push").toString());
        subRepository.save(sub);
        String subId = sub.getId();

        pushService.sendToSubscription(sub, "{\"title\":\"test\"}");

        assertThat(subRepository.findById(subId)).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a {@link PushSubscription} with a real P-256 key pair so the java-webpush
     * library can encrypt the payload without throwing.
     */
    private PushSubscription buildFakeSubscription(String familyId, String endpoint) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = gen.generateKeyPair();

        String p256dh = VapidConfig.encodePublicKey((ECPublicKey) kp.getPublic());
        // auth is a 16-byte random value, base64url encoded
        byte[] authBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(authBytes);
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(authBytes);

        PushSubscription sub = new PushSubscription();
        sub.setFamilyId(familyId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        return sub;
    }

    private String buildPayloadJson(String childName, String title) {
        return "{\"title\":\"Neuer Musikwunsch\",\"body\":\"" + childName
                + " m\u00F6chte \u201E" + title + "\u201C\",\"url\":\"http://localhost/web/requests\"}";
    }
}
