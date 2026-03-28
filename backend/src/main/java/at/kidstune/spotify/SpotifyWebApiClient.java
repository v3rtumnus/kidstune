package at.kidstune.spotify;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.spotify.dto.SpotifyItemDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Wraps the Spotify Web API via Spring WebClient.
 *
 * All responses are cached in Caffeine caches (configured by CacheConfig) with
 * the following TTLs:
 *  - search results:     1 hour
 *  - artist/album info: 24 hours
 *  - playlist tracks:    6 hours
 *  - user-specific:      1 hour
 *
 * Every method requires a {@code familyId} to obtain a valid Spotify access
 * token from SpotifyTokenService.
 */
@Service
public class SpotifyWebApiClient {

    private final SpotifyTokenService tokenService;
    private final WebClient spotifyApi;

    /** Exposed for test cache invalidation (same pattern as SpotifyTokenService.accessTokenCache). */
    final Cache<String, RawSearchData>        searchCache;
    final Cache<String, SpotifyItemDto>       artistCache;
    final Cache<String, List<SpotifyItemDto>> artistAlbumsCache;
    final Cache<String, List<SpotifyItemDto>> albumTracksCache;
    final Cache<String, List<SpotifyItemDto>> playlistTracksCache;
    final Cache<String, List<SpotifyItemDto>> recentlyPlayedCache;
    final Cache<String, List<SpotifyItemDto>> topArtistsCache;
    final Cache<String, List<SpotifyItemDto>> userPlaylistsCache;

    public SpotifyWebApiClient(
            SpotifyTokenService tokenService,
            SpotifyConfig config,
            WebClient.Builder builder,
            CacheManager cacheManager) {

        this.tokenService        = tokenService;
        this.spotifyApi          = builder.baseUrl(config.getApiBaseUrl()).build();
        this.searchCache         = nativeCache(cacheManager, "spotify-search");
        this.artistCache         = nativeCache(cacheManager, "spotify-artist");
        this.artistAlbumsCache   = nativeCache(cacheManager, "spotify-artist-albums");
        this.albumTracksCache    = nativeCache(cacheManager, "spotify-album-tracks");
        this.playlistTracksCache = nativeCache(cacheManager, "spotify-playlist-tracks");
        this.recentlyPlayedCache = nativeCache(cacheManager, "spotify-recently-played");
        this.topArtistsCache     = nativeCache(cacheManager, "spotify-top-artists");
        this.userPlaylistsCache  = nativeCache(cacheManager, "spotify-user-playlists");
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches Spotify for artists, albums, and playlists matching {@code query}.
     * Results include the {@code explicit} flag so the caller can filter.
     * Cache key: {@code query}.
     */
    public Mono<RawSearchData> search(String familyId, String query, int limit) {
        RawSearchData cached = searchCache.getIfPresent(query);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/search")
                    .queryParam("q", query)
                    .queryParam("type", "artist,album,playlist")
                    .queryParam("limit", limit)
                    .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiSearchResponse.class))
            .map(this::mapSearchResponse)
            .doOnNext(data -> searchCache.put(query, data));
    }

    // ── Artist ────────────────────────────────────────────────────────────────

    /** Returns artist details (name, imageUrl, genres, URI). Cache key: {@code artistId}. */
    public Mono<SpotifyItemDto> getArtist(String familyId, String artistId) {
        SpotifyItemDto cached = artistCache.getIfPresent(artistId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri("/v1/artists/{id}", artistId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiArtist.class))
            .map(a -> new SpotifyItemDto(a.id(), a.name(), extractImage(a.images()), a.uri(), null))
            .doOnNext(dto -> artistCache.put(artistId, dto));
    }

    // ── Artist Albums ─────────────────────────────────────────────────────────

    /** Returns all albums for an artist. Cache key: {@code artistId}. */
    public Mono<List<SpotifyItemDto>> getArtistAlbums(String familyId, String artistId) {
        List<SpotifyItemDto> cached = artistAlbumsCache.getIfPresent(artistId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/artists/{id}/albums")
                    .queryParam("limit", 50)
                    .build(artistId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiAlbumsPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .map(a -> new SpotifyItemDto(a.id(), a.name(), extractImage(a.images()), a.uri(),
                        firstArtistName(a.artists())))
                    .toList())
            .doOnNext(list -> artistAlbumsCache.put(artistId, list));
    }

    // ── Album Tracks ──────────────────────────────────────────────────────────

    /** Returns all tracks in an album. Cache key: {@code albumId}. */
    public Mono<List<SpotifyItemDto>> getAlbumTracks(String familyId, String albumId) {
        List<SpotifyItemDto> cached = albumTracksCache.getIfPresent(albumId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/albums/{id}/tracks")
                    .queryParam("limit", 50)
                    .build(albumId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiTracksPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .map(t -> new SpotifyItemDto(t.id(), t.name(), null, t.uri(),
                        firstArtistName(t.artists())))
                    .toList())
            .doOnNext(list -> albumTracksCache.put(albumId, list));
    }

    // ── Playlist Tracks ───────────────────────────────────────────────────────

    /** Returns all tracks in a playlist. Cache key: {@code playlistId}. */
    public Mono<List<SpotifyItemDto>> getPlaylistTracks(String familyId, String playlistId) {
        List<SpotifyItemDto> cached = playlistTracksCache.getIfPresent(playlistId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/playlists/{id}/tracks")
                    .queryParam("limit", 50)
                    .build(playlistId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlaylistItemsPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .filter(pi -> pi.track() != null)
                    .map(pi -> new SpotifyItemDto(pi.track().id(), pi.track().name(),
                        null, pi.track().uri(), firstArtistName(pi.track().artists())))
                    .toList())
            .doOnNext(list -> playlistTracksCache.put(playlistId, list));
    }

    // ── Recently Played ───────────────────────────────────────────────────────

    /** Returns recently played tracks for the authenticated user. Cache key: {@code familyId}. */
    public Mono<List<SpotifyItemDto>> getRecentlyPlayed(String familyId, int limit) {
        List<SpotifyItemDto> cached = recentlyPlayedCache.getIfPresent(familyId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/me/player/recently-played")
                    .queryParam("limit", limit)
                    .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlayHistoryPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .filter(h -> h.track() != null)
                    .map(h -> new SpotifyItemDto(h.track().id(), h.track().name(),
                        null, h.track().uri(), firstArtistName(h.track().artists())))
                    .toList())
            .doOnNext(list -> recentlyPlayedCache.put(familyId, list));
    }

    // ── Top Artists ───────────────────────────────────────────────────────────

    /**
     * Returns the user's top artists for a given time range.
     * Cache key: {@code familyId|timeRange}.
     */
    public Mono<List<SpotifyItemDto>> getTopArtists(String familyId, String timeRange, int limit) {
        String key = familyId + "|" + timeRange;
        List<SpotifyItemDto> cached = topArtistsCache.getIfPresent(key);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/me/top/artists")
                    .queryParam("time_range", timeRange)
                    .queryParam("limit", limit)
                    .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiArtistsPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .map(a -> new SpotifyItemDto(a.id(), a.name(), extractImage(a.images()), a.uri(), null))
                    .toList())
            .doOnNext(list -> topArtistsCache.put(key, list));
    }

    // ── User Playlists ────────────────────────────────────────────────────────

    /** Returns the current user's playlists. Cache key: {@code familyId}. */
    public Mono<List<SpotifyItemDto>> getUserPlaylists(String familyId, int limit) {
        List<SpotifyItemDto> cached = userPlaylistsCache.getIfPresent(familyId);
        if (cached != null) return Mono.just(cached);

        return tokenService.getValidAccessToken(familyId)
            .flatMap(token -> spotifyApi.get()
                .uri(u -> u.path("/v1/me/playlists")
                    .queryParam("limit", limit)
                    .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlaylistsPage.class))
            .map(page -> page.items() == null ? List.<SpotifyItemDto>of()
                : page.items().stream()
                    .map(p -> new SpotifyItemDto(p.id(), p.name(), extractImage(p.images()), p.uri(),
                        p.owner() != null ? p.owner().displayName() : null))
                    .toList())
            .doOnNext(list -> userPlaylistsCache.put(familyId, list));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RawSearchData mapSearchResponse(ApiSearchResponse response) {
        List<RawSpotifyItem> artists = response.artists() == null || response.artists().items() == null
            ? List.of()
            : response.artists().items().stream()
                .map(a -> new RawSpotifyItem(a.id(), a.name(), extractImage(a.images()), a.uri(), null, false))
                .toList();

        List<RawSpotifyItem> albums = response.albums() == null || response.albums().items() == null
            ? List.of()
            : response.albums().items().stream()
                .map(a -> new RawSpotifyItem(a.id(), a.name(), extractImage(a.images()), a.uri(),
                    firstArtistName(a.artists()), a.explicit()))
                .toList();

        List<RawSpotifyItem> playlists = response.playlists() == null || response.playlists().items() == null
            ? List.of()
            : response.playlists().items().stream()
                .map(p -> new RawSpotifyItem(p.id(), p.name(), extractImage(p.images()), p.uri(),
                    p.owner() != null ? p.owner().displayName() : null, p.explicit()))
                .toList();

        return new RawSearchData(artists, albums, playlists);
    }

    private static String extractImage(List<ApiImage> images) {
        return (images != null && !images.isEmpty()) ? images.get(0).url() : null;
    }

    private static String firstArtistName(List<ApiArtistRef> artists) {
        return (artists != null && !artists.isEmpty()) ? artists.get(0).name() : null;
    }

    @SuppressWarnings("unchecked")
    private static <V> Cache<String, V> nativeCache(CacheManager cacheManager, String name) {
        // Double-cast through Object to satisfy Java's invariant generics check
        Object raw = ((CaffeineCache) cacheManager.getCache(name)).getNativeCache();
        return (Cache<String, V>) raw;
    }

    // ── Internal Spotify API response types ───────────────────────────────────

    private record ApiImage(
        @JsonProperty("url") String url
    ) {}

    private record ApiArtistRef(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name
    ) {}

    private record ApiOwner(
        @JsonProperty("display_name") String displayName
    ) {}

    private record ApiArtist(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("uri") String uri,
        @JsonProperty("images") List<ApiImage> images,
        @JsonProperty("genres") List<String> genres
    ) {}

    private record ApiAlbum(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("uri") String uri,
        @JsonProperty("images") List<ApiImage> images,
        @JsonProperty("artists") List<ApiArtistRef> artists,
        @JsonProperty("explicit") boolean explicit
    ) {}

    private record ApiPlaylist(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("uri") String uri,
        @JsonProperty("images") List<ApiImage> images,
        @JsonProperty("owner") ApiOwner owner,
        @JsonProperty("explicit") boolean explicit
    ) {}

    private record ApiTrack(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("uri") String uri,
        @JsonProperty("explicit") boolean explicit,
        @JsonProperty("artists") List<ApiArtistRef> artists,
        @JsonProperty("duration_ms") int durationMs
    ) {}

    private record ApiPlaylistItem(
        @JsonProperty("track") ApiTrack track
    ) {}

    private record ApiPlayHistory(
        @JsonProperty("track") ApiTrack track,
        @JsonProperty("played_at") String playedAt
    ) {}

    private record ApiArtistsPage(
        @JsonProperty("items") List<ApiArtist> items
    ) {}

    private record ApiAlbumsPage(
        @JsonProperty("items") List<ApiAlbum> items
    ) {}

    private record ApiPlaylistsPage(
        @JsonProperty("items") List<ApiPlaylist> items
    ) {}

    private record ApiTracksPage(
        @JsonProperty("items") List<ApiTrack> items
    ) {}

    private record ApiPlaylistItemsPage(
        @JsonProperty("items") List<ApiPlaylistItem> items
    ) {}

    private record ApiPlayHistoryPage(
        @JsonProperty("items") List<ApiPlayHistory> items
    ) {}

    private record ApiSearchResponse(
        @JsonProperty("artists") ApiArtistsPage artists,
        @JsonProperty("albums") ApiAlbumsPage albums,
        @JsonProperty("playlists") ApiPlaylistsPage playlists
    ) {}
}
