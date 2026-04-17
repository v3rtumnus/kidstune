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

    private final PushSubscriptionService subService;
    private final String                  vapidPublicKeyString;

    public PushController(PushSubscriptionService subService,
                          @Qualifier("vapidPublicKeyString") String vapidPublicKeyString) {
        this.subService          = subService;
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

        return Mono.fromCallable(() -> {
                    subService.upsert(familyId, body.endpoint(), body.p256dh(), body.auth(), userAgent);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /** Removes a push subscription by its endpoint URL. */
    @DeleteMapping("/unsubscribe")
    public Mono<ResponseEntity<Void>> unsubscribe(
            @RequestBody PushUnsubscribeRequest body,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> { subService.deleteByEndpoint(body.endpoint()); return null; })
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
