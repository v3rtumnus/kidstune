package at.kidstune.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<AllowedContent, String> {

    List<AllowedContent> findByProfileId(String profileId);

    List<AllowedContent> findByProfileIdAndContentType(String profileId, ContentType contentType);

    Optional<AllowedContent> findByProfileIdAndSpotifyUri(String profileId, String spotifyUri);

    boolean existsByProfileIdAndSpotifyUri(String profileId, String spotifyUri);

    List<AllowedContent> findByProfileIdAndScope(String profileId, ContentScope scope);
}
