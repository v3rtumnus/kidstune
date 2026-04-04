package at.kidstune.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    List<Favorite> findByProfileId(String profileId);

    List<Favorite> findByProfileIdAndAddedAtAfter(String profileId, Instant since);
}
