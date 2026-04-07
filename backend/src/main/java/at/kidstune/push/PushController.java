package at.kidstune.push;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/web/push")
public class PushController {

    private final PushSubscriptionRepository subRepository;
    private final String                     vapidPublicKeyString;

    public PushController(PushSubscriptionRepository subRepository,
                          @Qualifier("vapidPublicKeyString") String vapidPublicKeyString) {
        this.subRepository       = subRepository;
        this.vapidPublicKeyString = vapidPublicKeyString;
    }

    /** Returns the VAPID public key for use in pushManager.subscribe(). */
    @GetMapping("/vapid-public-key")
    public Mono<ResponseEntity<Map<String, String>>> vapidPublicKey() {
        return Mono.just(ResponseEntity.ok(Map.of("publicKey", vapidPublicKeyString)));
    }

    /** Saves a new push subscription for the authenticated family. */
    @PostMapping("/subscribe")
    public Mono<ResponseEntity<Void>> subscribe(
            @RequestBody PushSubscribeRequest body,
            @AuthenticationPrincipal String familyId,
            @RequestHeader(value = "User-Agent", defaultValue = "") String userAgent) {

        return Mono.fromRunnable(() -> {
                    // Upsert: delete old subscription with same endpoint, then save new one
                    subRepository.deleteByEndpoint(body.endpoint());

                    PushSubscription sub = new PushSubscription();
                    sub.setFamilyId(familyId);
                    sub.setEndpoint(body.endpoint());
                    sub.setP256dh(body.p256dh());
                    sub.setAuth(body.auth());
                    sub.setUserAgent(userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent);
                    subRepository.save(sub);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /** Removes a push subscription by its endpoint URL. */
    @DeleteMapping("/unsubscribe")
    public Mono<ResponseEntity<Void>> unsubscribe(
            @RequestBody PushUnsubscribeRequest body,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromRunnable(() -> subRepository.deleteByEndpoint(body.endpoint()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
