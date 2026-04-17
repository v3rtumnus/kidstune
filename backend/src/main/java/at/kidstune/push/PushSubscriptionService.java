package at.kidstune.push;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Thin transactional wrapper around {@link PushSubscriptionRepository} write
 * operations.  JPA {@code EntityManager.remove()} requires a thread-bound
 * transaction; calling the repository directly from reactive code or {@code @Async}
 * methods fails without this wrapper.
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repo;

    public PushSubscriptionService(PushSubscriptionRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void upsert(String familyId, String endpoint, String p256dh, String auth, String userAgent) {
        repo.deleteByEndpoint(endpoint);
        PushSubscription sub = new PushSubscription();
        sub.setFamilyId(familyId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setUserAgent(userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent);
        repo.save(sub);
    }

    @Transactional
    public void deleteByEndpoint(String endpoint) {
        repo.deleteByEndpoint(endpoint);
    }

    @Transactional
    public void delete(PushSubscription sub) {
        repo.delete(sub);
    }

    public List<PushSubscription> findByFamilyId(String familyId) {
        return repo.findByFamilyId(familyId);
    }
}
