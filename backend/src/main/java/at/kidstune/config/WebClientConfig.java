package at.kidstune.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Provides a WebClient.Builder bean with Spotify-specific filters pre-applied.
 * Spring Boot 4 no longer auto-configures WebClient.Builder, so we declare it here.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // SpotifyRateLimitFilter handles 429 responses with Retry-After-aware backoff.
        // All current WebClient usages are Spotify API calls, so applying it globally is safe.
        return WebClient.builder().filter(new SpotifyRateLimitFilter());
    }
}
