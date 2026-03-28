package at.kidstune.spotify;

import at.kidstune.spotify.dto.SearchResultsResponse;
import at.kidstune.spotify.dto.SpotifyItemDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Wraps SpotifyWebApiClient to provide kid-safe search results.
 *
 * Responsibilities:
 *  - Groups results by type (artists, albums, playlists)
 *  - Filters out explicit content (Spotify's {@code explicit} flag)
 *  - Limits results to {@value #MAX_PER_TYPE} items per type
 */
@Service
public class SpotifySearchService {

    static final int MAX_PER_TYPE = 10;

    private final SpotifyWebApiClient apiClient;

    public SpotifySearchService(SpotifyWebApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Searches Spotify and returns grouped, filtered, and limited results.
     *
     * @param familyId family whose Spotify token is used for the API call
     * @param query    search query string
     * @param limit    maximum results per type; capped at {@value #MAX_PER_TYPE}
     */
    public Mono<SearchResultsResponse> search(String familyId, String query, int limit) {
        int effectiveLimit = Math.min(limit, MAX_PER_TYPE);

        return apiClient.search(familyId, query, effectiveLimit)
            .map(raw -> new SearchResultsResponse(
                toItemDtos(raw.artists(), false, effectiveLimit),
                toItemDtos(raw.albums(),  true,  effectiveLimit),
                toItemDtos(raw.playlists(), true, effectiveLimit)
            ));
    }

    private static List<SpotifyItemDto> toItemDtos(
            List<RawSpotifyItem> items,
            boolean filterExplicit,
            int limit) {

        return items.stream()
            .filter(item -> !filterExplicit || !item.explicit())
            .limit(limit)
            .map(item -> new SpotifyItemDto(
                item.id(),
                item.title(),
                item.imageUrl(),
                item.spotifyUri(),
                item.artistName()
            ))
            .toList();
    }
}
