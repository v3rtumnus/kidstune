package at.kidstune.favorites;

import at.kidstune.auth.SpotifyTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Mirrors KidsTune favorites to the child's Spotify "Liked Songs" (Saved Tracks).
 *
 * Both mirror operations are fire-and-forget: a Spotify API failure must never
 * break the KidsTune favorite operation. The service silently no-ops when the
 * profile has no linked Spotify account.
 *
 * Only {@code spotify:track:*} URIs are mirrored; albums/playlists/artists are
 * silently ignored.
 */
@Service
public class SpotifyFavoriteSyncService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyFavoriteSyncService.class);
    private static final String TRACK_URI_PREFIX = "spotify:track:";

    private final SpotifyTokenService tokenService;
    private final WebClient spotifyApi;

    @Autowired
    public SpotifyFavoriteSyncService(SpotifyTokenService tokenService,
                                      WebClient.Builder webClientBuilder) {
        this(tokenService, "https://api.spotify.com", webClientBuilder);
    }

    /** Package-private constructor for tests – allows overriding the Spotify API base URL. */
    SpotifyFavoriteSyncService(SpotifyTokenService tokenService,
                               String spotifyApiBaseUrl,
                               WebClient.Builder webClientBuilder) {
        this.tokenService = tokenService;
        this.spotifyApi = webClientBuilder.baseUrl(spotifyApiBaseUrl).build();
    }

    /**
     * Adds the track to the profile's Spotify Liked Songs.
     * No-ops (with debug log) if the profile has no linked Spotify account.
     * Swallows Spotify API errors (with warn log).
     */
    public Mono<Void> mirrorAdd(String profileId, String trackUri) {
        if (!trackUri.startsWith(TRACK_URI_PREFIX)) {
            log.debug("mirrorAdd: skipping non-track URI {} for profile {}", trackUri, profileId);
            return Mono.empty();
        }
        if (!tokenService.isProfileSpotifyLinked(profileId)) {
            log.debug("mirrorAdd: profile {} has no linked Spotify account, skipping", profileId);
            return Mono.empty();
        }

        String trackId = trackUri.substring(TRACK_URI_PREFIX.length());
        return tokenService.getValidProfileAccessToken(profileId)
                .flatMap(token -> spotifyApi.put()
                        .uri("/v1/me/tracks?ids={id}", trackId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .doOnError(e -> log.warn("mirrorAdd failed for profile {} track {}: {}",
                        profileId, trackUri, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Removes the track from the profile's Spotify Liked Songs.
     * No-ops (with debug log) if the profile has no linked Spotify account.
     * Swallows Spotify API errors (with warn log).
     */
    public Mono<Void> mirrorRemove(String profileId, String trackUri) {
        if (!trackUri.startsWith(TRACK_URI_PREFIX)) {
            log.debug("mirrorRemove: skipping non-track URI {} for profile {}", trackUri, profileId);
            return Mono.empty();
        }
        if (!tokenService.isProfileSpotifyLinked(profileId)) {
            log.debug("mirrorRemove: profile {} has no linked Spotify account, skipping", profileId);
            return Mono.empty();
        }

        String trackId = trackUri.substring(TRACK_URI_PREFIX.length());
        return tokenService.getValidProfileAccessToken(profileId)
                .flatMap(token -> spotifyApi.delete()
                        .uri("/v1/me/tracks?ids={id}", trackId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity()
                        .then())
                .doOnError(e -> log.warn("mirrorRemove failed for profile {} track {}: {}",
                        profileId, trackUri, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
