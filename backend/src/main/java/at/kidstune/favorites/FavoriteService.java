package at.kidstune.favorites;

import at.kidstune.favorites.dto.AddFavoriteRequest;
import at.kidstune.favorites.dto.FavoriteResponse;
import at.kidstune.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;
    private final ProfileRepository profileRepo;

    public FavoriteService(FavoriteRepository favoriteRepo, ProfileRepository profileRepo) {
        this.favoriteRepo = favoriteRepo;
        this.profileRepo  = profileRepo;
    }

    public List<FavoriteResponse> listFavorites(String profileId) {
        requireProfileExists(profileId);
        return favoriteRepo.findByProfileId(profileId)
                .stream().map(FavoriteResponse::from).toList();
    }

    @Transactional
    public FavoriteResponse addFavorite(String profileId, AddFavoriteRequest req) {
        requireProfileExists(profileId);

        if (favoriteRepo.existsByProfileIdAndSpotifyTrackUri(profileId, req.spotifyTrackUri())) {
            // Idempotent – return the existing favorite
            return favoriteRepo.findByProfileId(profileId).stream()
                    .filter(f -> f.getSpotifyTrackUri().equals(req.spotifyTrackUri()))
                    .map(FavoriteResponse::from)
                    .findFirst()
                    .orElseThrow();
        }

        Favorite f = new Favorite();
        f.setProfileId(profileId);
        f.setSpotifyTrackUri(req.spotifyTrackUri());
        f.setTrackTitle(req.trackTitle());
        f.setTrackImageUrl(req.trackImageUrl());
        f.setArtistName(req.artistName());
        return FavoriteResponse.from(favoriteRepo.save(f));
    }

    @Transactional
    public void removeFavorite(String profileId, String trackUri) {
        requireProfileExists(profileId);
        if (!favoriteRepo.existsByProfileIdAndSpotifyTrackUri(profileId, trackUri)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found");
        }
        favoriteRepo.deleteByProfileIdAndSpotifyTrackUri(profileId, trackUri);
    }

    private void requireProfileExists(String profileId) {
        if (!profileRepo.existsById(profileId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }
}
