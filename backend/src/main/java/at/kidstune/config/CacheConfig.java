package at.kidstune.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configures named Caffeine caches with per-cache TTLs.
 *
 * Cache TTLs:
 *  - spotify-search:           1 hour  (search results change infrequently)
 *  - spotify-artist:          24 hours (artist metadata is stable)
 *  - spotify-artist-albums:   24 hours
 *  - spotify-album-tracks:    24 hours
 *  - spotify-playlist-tracks:  6 hours (playlists updated more often)
 *  - spotify-recently-played:  1 hour  (per-user, changes often)
 *  - spotify-top-artists:      1 hour
 *  - spotify-user-playlists:   1 hour
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            build("spotify-search",          1,  TimeUnit.HOURS),
            build("spotify-artist",          24, TimeUnit.HOURS),
            build("spotify-artist-albums",   24, TimeUnit.HOURS),
            build("spotify-album-tracks",    24, TimeUnit.HOURS),
            build("spotify-playlist-tracks", 6,  TimeUnit.HOURS),
            build("spotify-recently-played", 1,  TimeUnit.HOURS),
            build("spotify-top-artists",     1,  TimeUnit.HOURS),
            build("spotify-user-playlists",  1,  TimeUnit.HOURS)
        ));
        return manager;
    }

    private static CaffeineCache build(String name, long duration, TimeUnit unit) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(500)
                .build());
    }
}
