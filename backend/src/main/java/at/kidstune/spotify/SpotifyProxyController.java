package at.kidstune.spotify;

import at.kidstune.common.OwnershipService;
import at.kidstune.common.SecurityUtils;
import at.kidstune.config.RequestThrottleService;
import at.kidstune.spotify.dto.SearchResultsResponse;
import at.kidstune.spotify.dto.SpotifyItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SpotifyProxyController.class);

    private final SpotifySearchService      searchService;
    private final SpotifySuggestionsService suggestionsService;
    private final RequestThrottleService    throttle;
    private final OwnershipService          ownershipService;

    public SpotifyProxyController(SpotifySearchService searchService,
                                  SpotifySuggestionsService suggestionsService,
                                  RequestThrottleService throttle,
                                  OwnershipService ownershipService) {
        this.searchService      = searchService;
        this.suggestionsService = suggestionsService;
        this.throttle           = throttle;
        this.ownershipService   = ownershipService;
    }

    /**
     * Search Spotify for artists, albums, and playlists.
     *
     * <pre>GET /api/v1/spotify/search?q=Bibi&limit=10</pre>
     *
     * Returns results grouped by type, with explicit content filtered out
     * and at most 10 items per type.  Throttled to {@code 10} requests/minute per device.
     */
    @GetMapping("/search")
    public Mono<ResponseEntity<SearchResultsResponse>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        return SecurityUtils.getClaims()
            .flatMap(claims -> {
                String throttleKey = claims.deviceId() != null ? claims.deviceId() : claims.familyId();
                throttle.checkSearchLimit(throttleKey); // throws RateLimitExceededException if over limit
                log.info("Spotify search: query='{}' familyId={}", query, claims.familyId());
                return searchService.search(claims.familyId(), query, limit);
            })
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
            .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId)
                .then(suggestionsService.getSuggestions(familyId, profileId)))
            .map(ResponseEntity::ok);
    }
}
