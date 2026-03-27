package at.kidstune.content;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Placeholder implementation of SpotifyApiClient used until Phase 2.3
 * wires in the real Spotify Web API client.
 *
 * Returns neutral values so scope resolution always falls through to DENIED
 * for steps 2–4 (album / artist / playlist). Direct TRACK matches (step 1)
 * still work because those only touch the database.
 *
 * Integration tests that need controlled Spotify behaviour override this bean
 * with {@code @MockitoBean SpotifyApiClient}.
 */
@Component
public class StubSpotifyApiClient implements SpotifyApiClient {

    @Override
    public Mono<String> getAlbumUriForTrack(String trackUri) {
        return Mono.just("spotify:album:unresolved");
    }

    @Override
    public Mono<List<String>> getArtistUrisForTrack(String trackUri) {
        return Mono.just(List.of());
    }

    @Override
    public Mono<List<String>> getTrackUrisInPlaylist(String playlistUri) {
        return Mono.just(List.of());
    }
}
