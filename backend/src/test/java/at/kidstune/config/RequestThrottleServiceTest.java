package at.kidstune.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestThrottleServiceTest {

    private RequestThrottleService throttle;

    @BeforeEach
    void setUp() {
        throttle = new RequestThrottleService();
    }

    // ── Search limit ──────────────────────────────────────────────────────────

    @Test
    void searchAllowsRequestsUpToTheLimit() {
        String deviceId = "device-abc";
        for (int i = 0; i < RequestThrottleService.SEARCH_LIMIT; i++) {
            assertThatCode(() -> throttle.checkSearchLimit(deviceId))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void searchThrowsRateLimitExceededWhenLimitExceeded() {
        String deviceId = "device-xyz";
        for (int i = 0; i < RequestThrottleService.SEARCH_LIMIT; i++) {
            throttle.checkSearchLimit(deviceId);
        }
        assertThatThrownBy(() -> throttle.checkSearchLimit(deviceId))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void searchLimitsArePerDeviceAndIndependent() {
        String deviceA = "device-A";
        String deviceB = "device-B";

        for (int i = 0; i < RequestThrottleService.SEARCH_LIMIT; i++) {
            throttle.checkSearchLimit(deviceA);
        }

        // Device B should still be allowed even though device A hit the limit
        assertThatCode(() -> throttle.checkSearchLimit(deviceB))
                .doesNotThrowAnyException();
    }

    // ── Request limit ─────────────────────────────────────────────────────────

    @Test
    void requestAllowsCallsUpToTheLimit() {
        String profileId = "profile-1";
        for (int i = 0; i < RequestThrottleService.REQUEST_LIMIT; i++) {
            assertThatCode(() -> throttle.checkRequestLimit(profileId))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void requestThrowsRateLimitExceededWhenLimitExceeded() {
        String profileId = "profile-2";
        for (int i = 0; i < RequestThrottleService.REQUEST_LIMIT; i++) {
            throttle.checkRequestLimit(profileId);
        }
        assertThatThrownBy(() -> throttle.checkRequestLimit(profileId))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void requestLimitsArePerProfileAndIndependent() {
        String profileA = "profile-A";
        String profileB = "profile-B";

        for (int i = 0; i < RequestThrottleService.REQUEST_LIMIT; i++) {
            throttle.checkRequestLimit(profileA);
        }

        assertThatCode(() -> throttle.checkRequestLimit(profileB))
                .doesNotThrowAnyException();
    }

    @Test
    void resetAllClearsAllCounters() {
        String deviceId = "device-reset";
        for (int i = 0; i < RequestThrottleService.SEARCH_LIMIT; i++) {
            throttle.checkSearchLimit(deviceId);
        }
        assertThatThrownBy(() -> throttle.checkSearchLimit(deviceId))
                .isInstanceOf(RateLimitExceededException.class);

        throttle.resetAll();

        assertThatCode(() -> throttle.checkSearchLimit(deviceId))
                .doesNotThrowAnyException();
    }
}
