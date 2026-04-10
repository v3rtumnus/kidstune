package at.kidstune.favorites;

import at.kidstune.common.OwnershipService;
import at.kidstune.common.SecurityUtils;
import at.kidstune.favorites.dto.AddFavoriteRequest;
import at.kidstune.favorites.dto.FavoriteResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/favorites")
public class FavoritesController {

    private static final Logger log = LoggerFactory.getLogger(FavoritesController.class);

    private final FavoriteService           favoriteService;
    private final SpotifyFavoriteSyncService spotifySyncService;
    private final OwnershipService           ownershipService;

    public FavoritesController(FavoriteService favoriteService,
                                SpotifyFavoriteSyncService spotifySyncService,
                                OwnershipService ownershipService) {
        this.favoriteService    = favoriteService;
        this.spotifySyncService = spotifySyncService;
        this.ownershipService   = ownershipService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<FavoriteResponse>>> list(@PathVariable String profileId) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
                .then(Mono.fromCallable(() -> favoriteService.listFavorites(profileId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<FavoriteResponse>> add(
            @PathVariable String profileId,
            @Valid @RequestBody AddFavoriteRequest req) {

        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
                .then(Mono.fromCallable(() -> favoriteService.addFavorite(profileId, req))
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnSuccess(saved ->
                        spotifySyncService.mirrorAdd(profileId, req.spotifyTrackUri())
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe(null, e -> log.warn("Spotify mirror-add failed for profile {}: {}", profileId, e.getMessage()))
                )
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @DeleteMapping("/{trackUri}")
    public Mono<ResponseEntity<Void>> remove(
            @PathVariable String profileId,
            @PathVariable String trackUri) {

        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
                .then(Mono.fromCallable(() -> {
                            favoriteService.removeFavorite(profileId, trackUri);
                            return trackUri;
                        }).subscribeOn(Schedulers.boundedElastic()))
                .doOnSuccess(uri ->
                        spotifySyncService.mirrorRemove(profileId, uri)
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe(null, e -> log.warn("Spotify mirror-remove failed for profile {}: {}", profileId, e.getMessage()))
                )
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
