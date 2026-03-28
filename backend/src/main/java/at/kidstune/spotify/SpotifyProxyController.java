package at.kidstune.spotify;

import at.kidstune.common.SecurityUtils;
import at.kidstune.spotify.dto.SearchResultsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Proxies Spotify search for the Parent App and Kids App Discover feature.
 *
 * Secured with PARENT role (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/spotify")
public class SpotifyProxyController {

    private final SpotifySearchService searchService;

    public SpotifyProxyController(SpotifySearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Search Spotify for artists, albums, and playlists.
     *
     * <pre>GET /api/v1/spotify/search?q=Bibi&limit=10</pre>
     *
     * Returns results grouped by type, with explicit content filtered out
     * and at most 10 items per type.
     */
    @GetMapping("/search")
    public Mono<ResponseEntity<SearchResultsResponse>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> searchService.search(familyId, query, limit))
            .map(ResponseEntity::ok);
    }
}
