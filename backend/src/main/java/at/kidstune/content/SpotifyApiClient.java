package at.kidstune.content;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Abstraction over the Spotify Web API for scope resolution.
 * Full implementation comes in Phase 2.3 (Spotify integration).
 * Tests use mocks of this interface.
 */
public interface SpotifyApiClient {

    /** Returns the album URI for the given track URI. */
    Mono<String> getAlbumUriForTrack(String trackUri);

    /** Returns all artist URIs credited on the given track. */
    Mono<List<String>> getArtistUrisForTrack(String trackUri);

    /** Returns all track URIs currently in the given playlist. */
    Mono<List<String>> getTrackUrisInPlaylist(String playlistUri);
}
