package at.kidstune.favorites;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    List<Favorite> findByProfileId(String profileId);

    Page<Favorite> findByProfileId(String profileId, Pageable pageable);

    List<Favorite> findByProfileIdAndAddedAtAfter(String profileId, Instant since);

    boolean existsByProfileIdAndSpotifyTrackUri(String profileId, String spotifyTrackUri);

    void deleteByProfileIdAndSpotifyTrackUri(String profileId, String spotifyTrackUri);
}
