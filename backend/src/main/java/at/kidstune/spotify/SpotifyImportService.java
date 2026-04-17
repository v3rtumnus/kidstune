package at.kidstune.spotify;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Spotify import for per-profile (child's own) Spotify accounts.
 *
 * All Spotify API calls use the child's own profile token via
 * {@link SpotifyTokenService#getValidProfileAccessToken(String)} — never the parent's family token.
 */
@Service
public class SpotifyImportService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyImportService.class);

    private static final int LIKED_SONGS_PAGE_SIZE = 50;

    private final SpotifyTokenService tokenService;
    private final FavoriteRepository  favoriteRepository;
    private final ContentRepository   contentRepository;
    private final WebClient           spotifyApi;

    @Autowired
    public SpotifyImportService(SpotifyTokenService tokenService,
                                FavoriteRepository favoriteRepository,
                                ContentRepository contentRepository,
                                SpotifyConfig spotifyConfig,
                                WebClient.Builder webClientBuilder) {
        this(tokenService, favoriteRepository,
                contentRepository, spotifyConfig.getApiBaseUrl(), webClientBuilder);
    }

    /** Package-private constructor for tests – allows overriding the Spotify API base URL. */
    SpotifyImportService(SpotifyTokenService tokenService,
                         FavoriteRepository favoriteRepository,
                         ContentRepository contentRepository,
                         String spotifyApiBaseUrl,
                         WebClient.Builder webClientBuilder) {
        this.tokenService     = tokenService;
        this.favoriteRepository  = favoriteRepository;
        this.contentRepository   = contentRepository;
        this.spotifyApi          = webClientBuilder.baseUrl(spotifyApiBaseUrl).build();
    }

    // ── getImportSuggestions ──────────────────────────────────────────────────

    /**
     * Fetches the child's Spotify listening history and playlists.
     *
     * @throws ProfileSpotifyNotLinkedException if the profile has no linked Spotify account
     */
    public Mono<ImportSuggestionsDto> getImportSuggestions(String profileId) {
        if (!tokenService.isProfileSpotifyLinked(profileId)) {
            return Mono.error(new ProfileSpotifyNotLinkedException(profileId));
        }

        return tokenService.getValidProfileAccessToken(profileId)
                .flatMap(token -> fetchAllSources(token)
                        .map(this::buildSuggestions));
    }

    private Mono<AllSources> fetchAllSources(String token) {
        Mono<List<ApiArtist>> recentArtists = fetchRecentlyPlayedArtists(token);
        Mono<List<ApiArtist>> topMedium     = fetchTopArtists(token, "medium_term");
        Mono<List<ApiArtist>> topLong       = fetchTopArtists(token, "long_term");
        Mono<List<ApiPlaylist>> playlists   = fetchUserPlaylists(token);

        return Mono.zip(recentArtists, topMedium, topLong, playlists)
                .map(t -> new AllSources(t.getT1(), t.getT2(), t.getT3(), t.getT4()));
    }

    private Mono<List<ApiArtist>> fetchRecentlyPlayedArtists(String token) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/me/player/recently-played").queryParam("limit", 50).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlayHistoryPage.class)
                .map(page -> {
                    if (page.items() == null) return List.<ApiArtist>of();
                    // Extract the primary artist from each recently-played track; deduplicate by name
                    Map<String, ApiArtist> byName = new LinkedHashMap<>();
                    for (ApiPlayHistory h : page.items()) {
                        if (h.track() == null || h.track().artists() == null) continue;
                        for (ApiArtistRef ref : h.track().artists()) {
                            String key = ref.name().toLowerCase();
                            // Artist refs from recently-played have no image/uri — use stub
                            byName.putIfAbsent(key,
                                    new ApiArtist(ref.id(), ref.name(), null, null, List.of()));
                            break; // only primary artist
                        }
                    }
                    return new ArrayList<>(byName.values());
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch recently-played for import: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<List<ApiArtist>> fetchTopArtists(String token, String timeRange) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/me/top/artists")
                        .queryParam("time_range", timeRange)
                        .queryParam("limit", 50)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiArtistsPage.class)
                .map(page -> page.items() == null ? List.<ApiArtist>of() : page.items())
                .onErrorResume(e -> {
                    log.warn("Failed to fetch top artists ({}) for import: {}", timeRange, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<List<ApiPlaylist>> fetchUserPlaylists(String token) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/me/playlists").queryParam("limit", 50).build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlaylistsPage.class)
                .map(page -> page.items() == null ? List.<ApiPlaylist>of() : page.items())
                .onErrorResume(e -> {
                    log.warn("Failed to fetch user playlists for import: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private ImportSuggestionsDto buildSuggestions(AllSources sources) {
        // Merge all artists; top-artists take precedence (have image + uri)
        Map<String, ApiArtist> merged = new LinkedHashMap<>();
        // Insert recently-played first (lower priority)
        for (ApiArtist a : sources.recentArtists()) {
            if (a.name() != null) merged.putIfAbsent(a.name().toLowerCase(), a);
        }
        // Top artists override (have richer data)
        for (ApiArtist a : sources.topMedium()) {
            if (a.name() != null) merged.put(a.name().toLowerCase(), a);
        }
        for (ApiArtist a : sources.topLong()) {
            if (a.name() != null) merged.put(a.name().toLowerCase(), a);
        }

        List<ImportSuggestionsDto.Item> artists   = new ArrayList<>();
        List<ImportSuggestionsDto.Item> playlists = new ArrayList<>();

        for (ApiArtist artist : merged.values()) {
            artists.add(new ImportSuggestionsDto.Item(
                    artist.uri(),
                    artist.name(),
                    extractImage(artist.images())
            ));
        }

        for (ApiPlaylist pl : sources.playlists()) {
            playlists.add(new ImportSuggestionsDto.Item(
                    pl.uri(),
                    pl.name(),
                    extractImage(pl.images())
            ));
        }

        return new ImportSuggestionsDto(artists, playlists);
    }

    // ── importLikedSongsAsFavorites ───────────────────────────────────────────

    /**
     * Imports all of the child's Spotify Liked Songs as KidsTune favorites.
     *
     * Each liked song is also added to the profile's AllowedContent (TRACK scope) so it
     * is accessible on the device without requiring separate parental approval.
     * Silently returns 0 if the profile has no linked Spotify account (no exception).
     *
     * @return count of new Favorite rows created
     */
    public Mono<Integer> importLikedSongsAsFavorites(String profileId) {
        if (!tokenService.isProfileSpotifyLinked(profileId)) {
            log.debug("importLikedSongsAsFavorites: profile {} has no linked Spotify account, skipping", profileId);
            return Mono.just(0);
        }

        return tokenService.getValidProfileAccessToken(profileId)
                .flatMap(this::fetchAllLikedTracks)
                .flatMap(tracks -> Mono.fromCallable(() ->
                        saveAllLikedSongs(profileId, tracks)
                ).subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<List<ApiSavedTrack>> fetchAllLikedTracks(String token) {
        return fetchLikedTracksPage(token, 0, new ArrayList<>());
    }

    private Mono<List<ApiSavedTrack>> fetchLikedTracksPage(String token, int offset, List<ApiSavedTrack> acc) {
        final int finalOffset = offset;

        return spotifyApi.get()
                .uri(u -> u.path("/v1/me/tracks")
                        .queryParam("limit", LIKED_SONGS_PAGE_SIZE)
                        .queryParam("offset", finalOffset)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiSavedTracksPage.class)
                .flatMap(page -> {
                    if (page.items() == null || page.items().isEmpty()) return Mono.just(acc);
                    for (ApiSavedTrack item : page.items()) {
                        if (item.track() != null && item.track().uri() != null) {
                            acc.add(item);
                        }
                    }
                    if (page.next() == null) {
                        return Mono.just(acc);
                    }
                    return fetchLikedTracksPage(token, offset + LIKED_SONGS_PAGE_SIZE, acc);
                });
    }

    private int saveAllLikedSongs(String profileId, List<ApiSavedTrack> savedTracks) {
        if (savedTracks.isEmpty()) return 0;

        int created = 0;
        for (ApiSavedTrack item : savedTracks) {
            ApiTrackRef track = item.track();
            if (track == null || track.uri() == null) continue;

            String uri        = track.uri();
            String title      = track.name() != null ? track.name() : uri;
            String artistName = (track.artists() != null && !track.artists().isEmpty())
                    ? track.artists().get(0).name() : null;
            String imageUrl   = (track.album() != null) ? extractImage(track.album().images()) : null;

            // Add to AllowedContent as TRACK scope (idempotent)
            if (!contentRepository.existsByProfileIdAndSpotifyUri(profileId, uri)) {
                AllowedContent ac = new AllowedContent();
                ac.setProfileId(profileId);
                ac.setSpotifyUri(uri);
                ac.setScope(ContentScope.TRACK);
                ac.setContentType(ContentType.MUSIC);
                ac.setTitle(title);
                ac.setArtistName(artistName);
                ac.setImageUrl(imageUrl);
                ac.setAddedBy("liked-songs-import");
                contentRepository.save(ac);
            }

            // Add as Favorite (idempotent)
            if (!favoriteRepository.existsByProfileIdAndSpotifyTrackUri(profileId, uri)) {
                Favorite fav = new Favorite();
                fav.setProfileId(profileId);
                fav.setSpotifyTrackUri(uri);
                fav.setTrackTitle(title);
                fav.setTrackImageUrl(imageUrl);
                fav.setArtistName(artistName);
                favoriteRepository.save(fav);
                created++;
            }
        }
        return created;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractImage(List<ApiImage> images) {
        return (images != null && !images.isEmpty()) ? images.get(0).url() : null;
    }

    // ── Internal records ──────────────────────────────────────────────────────

    private record AllSources(
            List<ApiArtist> recentArtists,
            List<ApiArtist> topMedium,
            List<ApiArtist> topLong,
            List<ApiPlaylist> playlists
    ) {}

    private record ApiImage(
            @JsonProperty("url") String url
    ) {}

    private record ApiArtistRef(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name
    ) {}

    private record ApiArtist(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("uri") String uri,
            @JsonProperty("images") List<ApiImage> images,
            @JsonProperty("genres") List<String> genres
    ) {}

    private record ApiPlaylist(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("uri") String uri,
            @JsonProperty("images") List<ApiImage> images
    ) {}

    private record ApiAlbum(
            @JsonProperty("images") List<ApiImage> images
    ) {}

    private record ApiTrackRef(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("uri") String uri,
            @JsonProperty("artists") List<ApiArtistRef> artists,
            @JsonProperty("album") ApiAlbum album
    ) {}

    private record ApiPlayHistory(
            @JsonProperty("track") ApiTrackRef track
    ) {}

    private record ApiPlayHistoryPage(
            @JsonProperty("items") List<ApiPlayHistory> items
    ) {}

    private record ApiArtistsPage(
            @JsonProperty("items") List<ApiArtist> items
    ) {}

    private record ApiPlaylistsPage(
            @JsonProperty("items") List<ApiPlaylist> items
    ) {}

    private record ApiSavedTrack(
            @JsonProperty("track") ApiTrackRef track
    ) {}

    private record ApiSavedTracksPage(
            @JsonProperty("items") List<ApiSavedTrack> items,
            @JsonProperty("next") String next
    ) {}
}
