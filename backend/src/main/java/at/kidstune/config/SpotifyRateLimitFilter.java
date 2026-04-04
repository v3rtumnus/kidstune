package at.kidstune.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * WebClient filter that handles Spotify's 429 Too Many Requests responses.
 *
 * When Spotify returns 429, reads the {@code Retry-After} header (seconds) and
 * waits before retrying the request once. If the retry also returns 429, the
 * error propagates to the caller.
 *
 * Applied globally via {@link WebClientConfig} – all Spotify WebClient instances
 * pick this up automatically.
 */
public class SpotifyRateLimitFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(SpotifyRateLimitFilter.class);

    /** Used when Spotify omits the Retry-After header (documented as "normally" present). */
    private static final long FALLBACK_WAIT_SECONDS = 2L;

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
                .flatMap(response -> {
                    if (response.statusCode().value() != 429) {
                        return Mono.just(response);
                    }
                    long waitSeconds = parseRetryAfter(
                            response.headers().asHttpHeaders().getFirst("Retry-After"));

                    log.warn("Spotify rate limit (429) on {} – retrying after {}s",
                            request.url().getPath(), waitSeconds);

                    return response.releaseBody()
                            .then(Mono.delay(Duration.ofSeconds(waitSeconds)))
                            .then(next.exchange(request));
                });
    }

    private static long parseRetryAfter(String header) {
        if (header == null) return FALLBACK_WAIT_SECONDS;
        try {
            return Math.max(1L, Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return FALLBACK_WAIT_SECONDS;
        }
    }
}
