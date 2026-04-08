package at.kidstune.spotify;

import at.kidstune.common.SecurityUtils;
import at.kidstune.spotify.dto.SearchResultsResponse;
import at.kidstune.spotify.dto.SpotifyItemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Proxies Spotify search and suggestions for the Kids App Discover feature.
 *
 * GET /api/v1/spotify/search  – PARENT role only (secured via SecurityConfig)
 * GET /api/v1/spotify/suggestions – KIDS or PARENT role (secured via SecurityConfig)
 */
@RestController
@RequestMapping("/api/v1/spotify")
public class SpotifyProxyController {

    private final SpotifySearchService      searchService;
    private final SpotifySuggestionsService suggestionsService;

    public SpotifyProxyController(SpotifySearchService searchService,
                                  SpotifySuggestionsService suggestionsService) {
        this.searchService      = searchService;
        this.suggestionsService = suggestionsService;
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

    /**
     * Returns personalised album suggestions for the Discover idle state,
     * derived from the profile's approved content and favourite tracks.
     *
     * <pre>GET /api/v1/spotify/suggestions?profileId={profileId}</pre>
     *
     * Accessible by KIDS and PARENT roles. Results are cached 1 hour per profile.
     */
    @GetMapping("/suggestions")
    public Mono<ResponseEntity<List<SpotifyItemDto>>> suggestions(
            @RequestParam("profileId") String profileId) {
        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> suggestionsService.getSuggestions(familyId, profileId))
            .map(ResponseEntity::ok);
    }
}
