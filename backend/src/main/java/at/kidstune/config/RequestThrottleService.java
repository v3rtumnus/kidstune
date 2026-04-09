package at.kidstune.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-device and per-profile request throttle using Caffeine tumbling-window counters.
 *
 * <ul>
 *   <li><b>Search queries</b> – max {@value #SEARCH_LIMIT} per device per minute.</li>
 *   <li><b>Content requests</b> – max {@value #REQUEST_LIMIT} per profile per hour.</li>
 * </ul>
 *
 * The window is a "tumbling" (fixed) window: the counter resets when the Caffeine entry
 * expires ({@code expireAfterWrite}).  This is intentionally simple; a sliding window
 * would require more complex bookkeeping.
 *
 * {@link RateLimitExceededException} is thrown (not returned as a Mono) so that Reactor's
 * operator wrapping converts it to an error signal automatically.
 */
@Service
public class RequestThrottleService {

    private static final Logger log = LoggerFactory.getLogger(RequestThrottleService.class);

    static final int SEARCH_LIMIT  = 10;  // per device per minute
    static final int REQUEST_LIMIT = 5;   // per profile per hour

    /** Key: deviceId (or familyId fallback). Value: request count in the current minute. */
    private final Cache<String, AtomicInteger> searchLimiter;

    /** Key: profileId. Value: request count in the current hour. */
    private final Cache<String, AtomicInteger> requestLimiter;

    public RequestThrottleService() {
        this.searchLimiter = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
        this.requestLimiter = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();
    }

    /**
     * Checks the search quota for the given device.
     *
     * @throws RateLimitExceededException when the per-minute limit is exceeded
     */
    public void checkSearchLimit(String deviceId) {
        AtomicInteger counter = searchLimiter.get(deviceId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count > SEARCH_LIMIT) {
            log.warn("Search rate limit exceeded for device {} (count={})", deviceId, count);
            throw new RateLimitExceededException("search", 60);
        }
    }

    /**
     * Checks the content-request quota for the given profile.
     *
     * @throws RateLimitExceededException when the per-hour limit is exceeded
     */
    public void checkRequestLimit(String profileId) {
        AtomicInteger counter = requestLimiter.get(profileId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count > REQUEST_LIMIT) {
            log.warn("Content-request rate limit exceeded for profile {} (count={})", profileId, count);
            throw new RateLimitExceededException("content-requests", 3600);
        }
    }

    /** Resets all counters – intended for tests only. */
    public void resetAll() {
        searchLimiter.invalidateAll();
        requestLimiter.invalidateAll();
    }
}
