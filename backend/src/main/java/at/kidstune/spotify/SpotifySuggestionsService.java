package at.kidstune.spotify;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.spotify.dto.SpotifyItemDto;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Returns personalised Discover suggestions for a child profile.
 *
 * The suggestion set is derived from:
 *  - Artist names on the profile's already-approved content (AllowedContent)
 *  - Artist names on the profile's favourite tracks (Favorite)
 *
 * For each unique artist name (up to {@value #MAX_ARTISTS}) we search Spotify
 * and include their albums in the result. Items whose Spotify URI is already
 * in AllowedContent for the profile are pre-filtered so the kids app receives
 * only genuinely new content to request.
 *
 * Results are cached per profileId with a 1-hour TTL (see CacheConfig).
 */
@Service
public class SpotifySuggestionsService {

    static final int MAX_ARTISTS     = 5;
    static final int MAX_SUGGESTIONS = 10;
    static final int ALBUMS_PER_ARTIST = 3;

    private final SpotifyWebApiClient  apiClient;
    private final ContentRepository    contentRepository;
    private final FavoriteRepository   favoriteRepository;
    private final Cache<String, List<SpotifyItemDto>> suggestionsCache;

    public SpotifySuggestionsService(
            SpotifyWebApiClient apiClient,
            ContentRepository contentRepository,
            FavoriteRepository favoriteRepository,
            CacheManager cacheManager) {

        this.apiClient         = apiClient;
        this.contentRepository = contentRepository;
        this.favoriteRepository = favoriteRepository;
        this.suggestionsCache  = nativeCache(cacheManager, "spotify-suggestions");
    }

    /**
     * Returns personalised suggestions for the given profile.
     *
     * @param familyId  family whose Spotify access token is used for API calls
     * @param profileId the child profile to personalise suggestions for
     */
    public Mono<List<SpotifyItemDto>> getSuggestions(String familyId, String profileId) {
        List<SpotifyItemDto> cached = suggestionsCache.getIfPresent(profileId);
        if (cached != null) return Mono.just(cached);

        // URIs already approved → client filters them too, but we pre-filter to avoid
        // sending useless results over the wire.
        Set<String> approvedUris = contentRepository.findByProfileId(profileId).stream()
                .map(AllowedContent::getSpotifyUri)
                .collect(Collectors.toSet());

        List<String> artistNames = collectArtistNames(profileId);
        if (artistNames.isEmpty()) return Mono.just(List.of());

        return Flux.fromIterable(artistNames)
                .concatMap(name -> searchAlbumsByArtistName(familyId, name)
                        .onErrorResume(e -> Mono.just(List.of())))
                .collectList()
                .map(allLists -> {
                    // Flatten → dedupe by URI → pre-filter approved → cap
                    Map<String, SpotifyItemDto> byUri = new java.util.LinkedHashMap<>();
                    allLists.forEach(list -> list.forEach(dto -> {
                        if (!approvedUris.contains(dto.spotifyUri())) {
                            byUri.putIfAbsent(dto.spotifyUri(), dto);
                        }
                    }));
                    return byUri.values().stream().limit(MAX_SUGGESTIONS).toList();
                })
                .doOnNext(list -> {
                    if (!list.isEmpty()) suggestionsCache.put(profileId, list);
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Gathers unique artist names from the profile's approved content and favourites.
     * Order: approved content first (they showed interest by adding), then favourites.
     */
    private List<String> collectArtistNames(String profileId) {
        Set<String> names = new LinkedHashSet<>();

        contentRepository.findByProfileId(profileId).stream()
                .map(AllowedContent::getArtistName)
                .filter(n -> n != null && !n.isBlank())
                .forEach(names::add);

        favoriteRepository.findByProfileId(profileId).stream()
                .map(Favorite::getArtistName)
                .filter(n -> n != null && !n.isBlank())
                .forEach(names::add);

        return names.stream().distinct().limit(MAX_ARTISTS).toList();
    }

    /**
     * Searches Spotify for albums by the given artist name and returns non-explicit results.
     */
    private Mono<List<SpotifyItemDto>> searchAlbumsByArtistName(String familyId, String artistName) {
        return apiClient.search(familyId, artistName, ALBUMS_PER_ARTIST)
                .map(raw -> raw.albums().stream()
                        .filter(item -> !item.explicit())
                        .map(item -> new SpotifyItemDto(
                                item.id(),
                                item.title(),
                                item.imageUrl(),
                                item.spotifyUri(),
                                item.artistName()))
                        .limit(ALBUMS_PER_ARTIST)
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private static <V> Cache<String, V> nativeCache(CacheManager cacheManager, String name) {
        Object raw = ((CaffeineCache) cacheManager.getCache(name)).getNativeCache();
        return (Cache<String, V>) raw;
    }
}
