package at.kidstune.config;

/**
 * Thrown by {@link RequestThrottleService} when a per-device or per-profile request
 * quota is exceeded.  Maps to HTTP 429 with a {@code Retry-After} header in
 * {@link at.kidstune.common.GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(String resource, int retryAfterSeconds) {
        super("Rate limit exceeded for " + resource + " – retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
