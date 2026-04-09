package at.kidstune.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyCircuitBreakerTest {

    private SpotifyCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new SpotifyCircuitBreaker();
    }

    @Test
    void startsClosed_andAllowsRequests() {
        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest()).isTrue();
        assertThat(breaker.getFailureCount()).isZero();
    }

    @Test
    void opensAfterThresholdConsecutiveFailures() {
        for (int i = 0; i < SpotifyCircuitBreaker.FAILURE_THRESHOLD; i++) {
            assertThat(breaker.allowRequest()).isTrue();
            breaker.onFailure();
        }
        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.OPEN);
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    void doesNotOpenBeforeThresholdIsReached() {
        for (int i = 0; i < SpotifyCircuitBreaker.FAILURE_THRESHOLD - 1; i++) {
            breaker.onFailure();
        }
        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void successResetsToClosed_andClearsFailureCount() {
        for (int i = 0; i < SpotifyCircuitBreaker.FAILURE_THRESHOLD; i++) {
            breaker.onFailure();
        }
        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.OPEN);

        breaker.onSuccess();

        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
        assertThat(breaker.getFailureCount()).isZero();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void resetClearsAllState() {
        for (int i = 0; i < SpotifyCircuitBreaker.FAILURE_THRESHOLD; i++) {
            breaker.onFailure();
        }

        breaker.reset();

        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
        assertThat(breaker.getFailureCount()).isZero();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void failureCountIncrementsWithEachFailure() {
        breaker.onFailure();
        assertThat(breaker.getFailureCount()).isEqualTo(1);
        breaker.onFailure();
        assertThat(breaker.getFailureCount()).isEqualTo(2);
    }

    @Test
    void successAfterPartialFailuresResetsCounter() {
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.getFailureCount()).isEqualTo(2);

        breaker.onSuccess();

        assertThat(breaker.getFailureCount()).isZero();
        assertThat(breaker.getState()).isEqualTo(SpotifyCircuitBreaker.State.CLOSED);
    }
}
