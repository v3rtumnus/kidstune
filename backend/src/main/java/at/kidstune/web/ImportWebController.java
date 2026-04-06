package at.kidstune.web;

import at.kidstune.profile.ProfileException;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Web-dashboard endpoint for the import wizard (phase 6).
 *
 * POST /api/v1/profiles/{profileId}/import-liked-songs is protected by the web session chain
 * (session cookie auth, not JWT) so it can only be called from the authenticated dashboard.
 */
@Controller
public class ImportWebController {

    private final SpotifyImportService spotifyImportService;
    private final ProfileRepository    profileRepository;

    public ImportWebController(SpotifyImportService spotifyImportService,
                               ProfileRepository profileRepository) {
        this.spotifyImportService = spotifyImportService;
        this.profileRepository    = profileRepository;
    }

    /**
     * Called by the import wizard after content import completes.
     * Imports the child's Spotify Liked Songs as KidsTune favorites.
     *
     * Only tracks that are already in the child's approved content are imported —
     * the parent's whitelist is never bypassed.
     *
     * @return {@code { "imported": N }} where N is the number of new favorites created
     */
    @PostMapping("/api/v1/profiles/{profileId}/import-liked-songs")
    @ResponseBody
    public Mono<ResponseEntity<Map<String, Integer>>> importLikedSongs(
            @PathVariable String profileId,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            // Verify profile belongs to this family
            profileRepository.findById(profileId)
                    .filter(p -> p.getFamilyId().equals(familyId))
                    .orElseThrow(() -> new ProfileException(
                            "Profil nicht gefunden", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));
            return profileId;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(id -> spotifyImportService.importLikedSongsAsFavorites(id))
        .map(count -> ResponseEntity.ok(Map.of("imported", count)));
    }
}
