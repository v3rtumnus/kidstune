package at.kidstune.resolver;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Spotify Web API client used exclusively by {@link ContentResolver}.
 *
 * Returns richer data types than {@code SpotifyWebApiClient} (duration, track
 * numbers, album genres) so the resolver can store complete metadata and classify
 * albums with {@link at.kidstune.content.ContentTypeClassifier}.
 *
 * All list endpoints (artist albums, album tracks, playlist tracks) are fully
 * paginated using Reactor's {@code expand()} operator, so artists with hundreds
 * of episodes (TKKG ~300, Benjamin Blümchen ~130, Bibi Blocksberg ~120) are
 * resolved completely rather than being silently truncated at 50.
 */
@Component
class ContentResolverSpotifyClient {

    static final int PAGE_SIZE = 50;

    private final SpotifyTokenService tokenService;
    private final WebClient spotifyApi;

    ContentResolverSpotifyClient(SpotifyTokenService tokenService,
                                  SpotifyConfig config,
                                  WebClient.Builder builder) {
        this.tokenService = tokenService;
        this.spotifyApi   = builder.baseUrl(config.getApiBaseUrl()).build();
    }

    // ── Artist albums (paginated) ─────────────────────────────────────────────

    /**
     * Returns ALL albums for an artist, paginating through all offset pages.
     * Note: album objects from this endpoint do not include genre information.
     */
    Mono<List<AlbumData>> getArtistAlbums(String familyId, String artistId) {
        return tokenService.getValidAccessToken(familyId)
                .flatMapMany(token ->
                        fetchArtistAlbumsPage(token, artistId, 0)
                                .expand(page -> page.next() != null
                                        ? fetchArtistAlbumsPage(token, artistId, nextOffset(page.offset(), page.limit()))
                                        : Mono.empty())
                                .flatMap(page -> page.items() == null
                                        ? Flux.empty()
                                        : Flux.fromIterable(page.items()))
                )
                .map(a -> new AlbumData(
                        a.id(), a.uri(), a.name(),
                        extractImage(a.images()),
                        a.releaseDate(),
                        a.totalTracks() != null ? a.totalTracks() : 0,
                        List.of(),
                        firstArtistName(a.artists())))
                .collectList();
    }

    private Mono<ApiAlbumsPage> fetchArtistAlbumsPage(String token, String artistId, int offset) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/artists/{id}/albums")
                        .queryParam("limit", PAGE_SIZE)
                        .queryParam("offset", offset)
                        .queryParam("include_groups", "album,single")
                        .build(artistId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiAlbumsPage.class);
    }

    // ── Full album (with genres) ───────────────────────────────────────────────

    /** Returns full album details including genres, release date and total tracks. */
    Mono<AlbumData> getAlbumDetails(String familyId, String albumId) {
        return tokenService.getValidAccessToken(familyId)
                .flatMap(token -> spotifyApi.get()
                        .uri("/v1/albums/{id}", albumId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(ApiAlbumFull.class))
                .map(a -> new AlbumData(
                        a.id(), a.uri(), a.name(),
                        extractImage(a.images()),
                        a.releaseDate(),
                        a.totalTracks() != null ? a.totalTracks() : 0,
                        a.genres() != null ? a.genres() : List.of(),
                        firstArtistName(a.artists())));
    }

    // ── Album tracks (paginated) ──────────────────────────────────────────────

    /**
     * Returns ALL tracks in an album, paginating through all offset pages.
     * The {@code albumUri}/{@code albumTitle}/{@code albumImageUrl} parameters are
     * propagated onto each returned {@link TrackData} so callers can group tracks
     * without a separate album lookup.
     */
    Mono<List<TrackData>> getAlbumTracks(String familyId, String albumId,
                                          String albumUri, String albumTitle,
                                          String albumImageUrl) {
        return tokenService.getValidAccessToken(familyId)
                .flatMapMany(token ->
                        fetchAlbumTracksPage(token, albumId, 0)
                                .expand(page -> page.next() != null
                                        ? fetchAlbumTracksPage(token, albumId, nextOffset(page.offset(), page.limit()))
                                        : Mono.empty())
                                .flatMap(page -> page.items() == null
                                        ? Flux.empty()
                                        : Flux.fromIterable(page.items()))
                )
                .map(t -> new TrackData(
                        t.uri(), t.name(), firstArtistName(t.artists()),
                        t.durationMs(), t.trackNumber(), t.discNumber(),
                        albumUri, albumTitle, albumImageUrl))
                .collectList();
    }

    private Mono<ApiTracksPage> fetchAlbumTracksPage(String token, String albumId, int offset) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/albums/{id}/tracks")
                        .queryParam("limit", PAGE_SIZE)
                        .queryParam("offset", offset)
                        .build(albumId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiTracksPage.class);
    }

    // ── Playlist tracks (paginated) ───────────────────────────────────────────

    /**
     * Returns ALL tracks in a playlist, paginating through all offset pages.
     * Each track carries the album reference so the resolver can group by album
     * without extra API calls.
     */
    Mono<List<TrackData>> getPlaylistTracks(String familyId, String playlistId) {
        return tokenService.getValidAccessToken(familyId)
                .flatMapMany(token ->
                        fetchPlaylistTracksPage(token, playlistId, 0)
                                .expand(page -> page.next() != null
                                        ? fetchPlaylistTracksPage(token, playlistId, nextOffset(page.offset(), page.limit()))
                                        : Mono.empty())
                                .flatMap(page -> page.items() == null
                                        ? Flux.empty()
                                        : Flux.fromIterable(page.items()))
                )
                .filter(pi -> pi.track() != null)
                .map(pi -> {
                    ApiPlaylistTrack t = pi.track();
                    String albumUri   = t.album() != null ? t.album().uri() : null;
                    String albumTitle = t.album() != null ? t.album().name() : null;
                    String albumImg   = t.album() != null ? extractImage(t.album().images()) : null;
                    return new TrackData(
                            t.uri(), t.name(), firstArtistName(t.artists()),
                            t.durationMs(), t.trackNumber(), t.discNumber(),
                            albumUri, albumTitle, albumImg);
                })
                .collectList();
    }

    private Mono<ApiPlaylistItemsPage> fetchPlaylistTracksPage(String token, String playlistId, int offset) {
        return spotifyApi.get()
                .uri(u -> u.path("/v1/playlists/{id}/tracks")
                        .queryParam("limit", PAGE_SIZE)
                        .queryParam("offset", offset)
                        .build(playlistId))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(ApiPlaylistItemsPage.class);
    }

    // ── Single track ──────────────────────────────────────────────────────────

    /** Returns a single track with full detail including embedded album info. */
    Mono<TrackData> getTrack(String familyId, String trackId) {
        return tokenService.getValidAccessToken(familyId)
                .flatMap(token -> spotifyApi.get()
                        .uri("/v1/tracks/{id}", trackId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(ApiTrackFull.class))
                .map(t -> {
                    String albumUri   = t.album() != null ? t.album().uri() : null;
                    String albumTitle = t.album() != null ? t.album().name() : null;
                    String albumImg   = t.album() != null ? extractImage(t.album().images()) : null;
                    return new TrackData(
                            t.uri(), t.name(), firstArtistName(t.artists()),
                            t.durationMs(), t.trackNumber(), t.discNumber(),
                            albumUri, albumTitle, albumImg);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractImage(List<ApiImage> images) {
        return (images != null && !images.isEmpty()) ? images.get(0).url() : null;
    }

    private static String firstArtistName(List<ApiArtistRef> artists) {
        return (artists != null && !artists.isEmpty()) ? artists.get(0).name() : null;
    }

    private static int nextOffset(Integer offset, Integer limit) {
        return (offset != null ? offset : 0) + (limit != null ? limit : PAGE_SIZE);
    }

    // ── Spotify response records ──────────────────────────────────────────────

    private record ApiImage(
            @JsonProperty("url") String url
    ) {}

    private record ApiArtistRef(
            @JsonProperty("id")   String id,
            @JsonProperty("name") String name
    ) {}

    private record ApiAlbumRef(
            @JsonProperty("id")     String id,
            @JsonProperty("name")   String name,
            @JsonProperty("uri")    String uri,
            @JsonProperty("images") List<ApiImage> images
    ) {}

    /** SimplifiedAlbumObject – returned by /v1/artists/{id}/albums */
    private record ApiAlbum(
            @JsonProperty("id")           String id,
            @JsonProperty("name")         String name,
            @JsonProperty("uri")          String uri,
            @JsonProperty("images")       List<ApiImage> images,
            @JsonProperty("artists")      List<ApiArtistRef> artists,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("total_tracks") Integer totalTracks
    ) {}

    /** Full AlbumObject – returned by /v1/albums/{id} (includes genres). */
    private record ApiAlbumFull(
            @JsonProperty("id")           String id,
            @JsonProperty("name")         String name,
            @JsonProperty("uri")          String uri,
            @JsonProperty("images")       List<ApiImage> images,
            @JsonProperty("artists")      List<ApiArtistRef> artists,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("total_tracks") Integer totalTracks,
            @JsonProperty("genres")       List<String> genres
    ) {}

    /** SimplifiedTrackObject – returned by /v1/albums/{id}/tracks */
    private record ApiTrack(
            @JsonProperty("id")           String id,
            @JsonProperty("name")         String name,
            @JsonProperty("uri")          String uri,
            @JsonProperty("artists")      List<ApiArtistRef> artists,
            @JsonProperty("duration_ms")  int durationMs,
            @JsonProperty("track_number") int trackNumber,
            @JsonProperty("disc_number")  int discNumber
    ) {}

    /** TrackObject with embedded album – returned by /v1/playlists/{id}/tracks */
    private record ApiPlaylistTrack(
            @JsonProperty("id")           String id,
            @JsonProperty("name")         String name,
            @JsonProperty("uri")          String uri,
            @JsonProperty("artists")      List<ApiArtistRef> artists,
            @JsonProperty("duration_ms")  int durationMs,
            @JsonProperty("track_number") int trackNumber,
            @JsonProperty("disc_number")  int discNumber,
            @JsonProperty("album")        ApiAlbumRef album
    ) {}

    /** Full TrackObject – returned by /v1/tracks/{id} */
    private record ApiTrackFull(
            @JsonProperty("id")           String id,
            @JsonProperty("name")         String name,
            @JsonProperty("uri")          String uri,
            @JsonProperty("artists")      List<ApiArtistRef> artists,
            @JsonProperty("duration_ms")  int durationMs,
            @JsonProperty("track_number") int trackNumber,
            @JsonProperty("disc_number")  int discNumber,
            @JsonProperty("album")        ApiAlbumRef album
    ) {}

    private record ApiPlaylistItem(
            @JsonProperty("track") ApiPlaylistTrack track
    ) {}

    /** Paginated envelope for /v1/artists/{id}/albums */
    private record ApiAlbumsPage(
            @JsonProperty("items")  List<ApiAlbum> items,
            @JsonProperty("total")  Integer total,
            @JsonProperty("limit")  Integer limit,
            @JsonProperty("offset") Integer offset,
            @JsonProperty("next")   String next
    ) {}

    /** Paginated envelope for /v1/albums/{id}/tracks */
    private record ApiTracksPage(
            @JsonProperty("items")  List<ApiTrack> items,
            @JsonProperty("total")  Integer total,
            @JsonProperty("limit")  Integer limit,
            @JsonProperty("offset") Integer offset,
            @JsonProperty("next")   String next
    ) {}

    /** Paginated envelope for /v1/playlists/{id}/tracks */
    private record ApiPlaylistItemsPage(
            @JsonProperty("items")  List<ApiPlaylistItem> items,
            @JsonProperty("total")  Integer total,
            @JsonProperty("limit")  Integer limit,
            @JsonProperty("offset") Integer offset,
            @JsonProperty("next")   String next
    ) {}
}