package at.kidstune.family;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, String> {

    Optional<Family> findBySpotifyUserId(String spotifyUserId);

    Optional<Family> findByEmail(String email);
}