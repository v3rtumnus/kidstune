package at.kidstune.content;

import at.kidstune.content.dto.ContentCheckResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Implements the §4.2 scope resolution algorithm:
 *
 * <pre>
 * isAllowed(trackUri, profileId):
 *   1. Direct TRACK match in allowed_content
 *   2. Track's album URI in allowed_content (ALBUM scope)
 *   3. Any of track's artist URIs in allowed_content (ARTIST scope)
 *   4. Track present in any allowed PLAYLIST for this profile
 *   5. DENIED
 * </pre>
 */
@Service
public class ScopeResolver {

    private final ContentRepository contentRepository;
    private final SpotifyApiClient  spotifyApiClient;

    public ScopeResolver(ContentRepository contentRepository, SpotifyApiClient spotifyApiClient) {
        this.contentRepository = contentRepository;
        this.spotifyApiClient  = spotifyApiClient;
    }

    public Mono<ContentCheckResponse> isAllowed(String trackUri, String profileId) {
        // Step 1 – direct TRACK match
        return db(() -> contentRepository.existsByProfileIdAndSpotifyUri(profileId, trackUri))
                .flatMap(direct -> {
                    if (direct) return Mono.just(ContentCheckResponse.allowed("TRACK_MATCH"));

                    // Step 2 – ALBUM match
                    return spotifyApiClient.getAlbumUriForTrack(trackUri)
                            .flatMap(albumUri -> db(() ->
                                    contentRepository.existsByProfileIdAndSpotifyUri(profileId, albumUri)))
                            .flatMap(albumMatch -> {
                                if (albumMatch) return Mono.just(ContentCheckResponse.allowed("ALBUM_MATCH"));

                                // Step 3 – ARTIST match
                                return spotifyApiClient.getArtistUrisForTrack(trackUri)
                                        .flatMap(artistUris -> db(() ->
                                                artistUris.stream().anyMatch(uri ->
                                                        contentRepository.existsByProfileIdAndSpotifyUri(profileId, uri))))
                                        .flatMap(artistMatch -> {
                                            if (artistMatch) return Mono.just(ContentCheckResponse.allowed("ARTIST_MATCH"));

                                            // Step 4 – PLAYLIST match
                                            return checkPlaylistMatch(trackUri, profileId);
                                        });
                            });
                });
    }

    private Mono<ContentCheckResponse> checkPlaylistMatch(String trackUri, String profileId) {
        return db(() -> contentRepository.findByProfileIdAndScope(profileId, ContentScope.PLAYLIST))
                .flatMap(playlists -> {
                    if (playlists.isEmpty()) return Mono.just(ContentCheckResponse.denied());

                    return Flux.fromIterable(playlists)
                            .flatMap(playlist ->
                                    spotifyApiClient.getTrackUrisInPlaylist(playlist.getSpotifyUri())
                                            .map(tracks -> tracks.contains(trackUri))
                            )
                            .any(Boolean::booleanValue)
                            .map(found -> found
                                    ? ContentCheckResponse.allowed("PLAYLIST_MATCH")
                                    : ContentCheckResponse.denied());
                });
    }

    private <T> Mono<T> db(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
