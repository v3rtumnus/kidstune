package at.kidstune.common;

import at.kidstune.auth.KidstuneAuthentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** Returns the familyId of the currently authenticated device. */
    public static Mono<String> getFamilyId() {
        return getClaims().map(at.kidstune.auth.JwtClaims::familyId);
    }

    /** Returns the full JWT claims for the currently authenticated device. */
    public static Mono<at.kidstune.auth.JwtClaims> getClaims() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (KidstuneAuthentication) ctx.getAuthentication())
                .map(KidstuneAuthentication::getClaims);
    }
}
