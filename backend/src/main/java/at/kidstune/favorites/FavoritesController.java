package at.kidstune.favorites;

import at.kidstune.favorites.dto.AddFavoriteRequest;
import at.kidstune.favorites.dto.FavoriteResponse;
import jakarta.validation.Valid;
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

    private final FavoriteService          favoriteService;
    private final SpotifyFavoriteSyncService spotifySyncService;

    public FavoritesController(FavoriteService favoriteService,
                                SpotifyFavoriteSyncService spotifySyncService) {
        this.favoriteService   = favoriteService;
        this.spotifySyncService = spotifySyncService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<FavoriteResponse>>> list(@PathVariable String profileId) {
        return Mono.fromCallable(() -> favoriteService.listFavorites(profileId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<FavoriteResponse>> add(
            @PathVariable String profileId,
            @Valid @RequestBody AddFavoriteRequest req) {

        return Mono.fromCallable(() -> favoriteService.addFavorite(profileId, req))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(saved ->
                        // Fire-and-forget Spotify mirror
                        spotifySyncService.mirrorAdd(profileId, req.spotifyTrackUri())
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe()
                )
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @DeleteMapping("/{trackUri}")
    public Mono<ResponseEntity<Void>> remove(
            @PathVariable String profileId,
            @PathVariable String trackUri) {

        return Mono.fromCallable(() -> { favoriteService.removeFavorite(profileId, trackUri); return trackUri; })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(uri ->
                        spotifySyncService.mirrorRemove(profileId, uri)
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe()
                )
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
