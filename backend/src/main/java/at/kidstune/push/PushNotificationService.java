package at.kidstune.push;

import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends Web Push notifications to all registered browser subscriptions for a family.
 * Failures are best-effort: a gone endpoint is logged and removed; other errors are
 * logged but never bubble up to the caller.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository subRepository;
    private final ProfileRepository          profileRepository;
    private final PushService                pushService;

    @Value("${kidstune.base-url}")
    private String baseUrl;

    public PushNotificationService(PushSubscriptionRepository subRepository,
                                   ProfileRepository profileRepository,
                                   @Qualifier("vapidPublicKeyString")  String vapidPublicKeyString,
                                   @Qualifier("vapidPrivateKeyString") String vapidPrivateKeyString)
            throws Exception {
        this.subRepository    = subRepository;
        this.profileRepository = profileRepository;
        this.pushService      = new PushService(vapidPublicKeyString, vapidPrivateKeyString,
                                                "mailto:push@kidstune.at");
    }

    /**
     * Sends a Web Push notification to every subscription belonging to {@code familyId}
     * informing the parent about a new content request.  Runs asynchronously.
     */
    @Async
    public void sendRequestNotification(ContentRequest request, String familyId) {
        List<PushSubscription> subs = subRepository.findByFamilyId(familyId);
        if (subs.isEmpty()) return;

        String childName = profileRepository.findById(request.getProfileId())
                .map(ChildProfile::getName)
                .orElse("Ein Kind");

        String payload = buildPayload(childName, request.getTitle());

        for (PushSubscription sub : subs) {
            sendToSubscription(sub, payload);
        }
    }

    // ── Package-private for tests ─────────────────────────────────────────────

    void sendToSubscription(PushSubscription sub, String payload) {
        try {
            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8));

            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status == 410 || status == 404) {
                log.info("Push subscription gone ({}), removing endpoint for family {}",
                        status, sub.getFamilyId());
                subRepository.delete(sub);
            } else if (status >= 400) {
                log.warn("Push delivery failed with HTTP {} for subscription {}",
                        status, sub.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send push notification to subscription {}: {}",
                    sub.getId(), e.getMessage(), e);
        }
    }

    private String buildPayload(String childName, String title) {
        // Build JSON manually to avoid injecting ObjectMapper into this service.
        String safeChild = escape(childName);
        String safeTitle = escape(title);
        String safeUrl   = escape(baseUrl + "/web/requests");
        return "{\"title\":\"Neuer Musikwunsch\",\"body\":\"" + safeChild
                + " m\u00F6chte \u201E" + safeTitle + "\u201C\",\"url\":\"" + safeUrl + "\"}";
    }

    /** Minimal JSON string escaping (backslash and double-quote). */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
