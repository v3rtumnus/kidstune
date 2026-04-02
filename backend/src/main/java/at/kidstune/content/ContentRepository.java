package at.kidstune.content;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<AllowedContent, String> {

    List<AllowedContent> findByProfileId(String profileId);

    List<AllowedContent> findByProfileIdAndContentType(String profileId, ContentType contentType);

    Optional<AllowedContent> findByProfileIdAndSpotifyUri(String profileId, String spotifyUri);

    boolean existsByProfileIdAndSpotifyUri(String profileId, String spotifyUri);

    List<AllowedContent> findByProfileIdAndScope(String profileId, ContentScope scope);

    @Query("SELECT COUNT(c) FROM AllowedContent c WHERE c.profileId IN :profileIds")
    long countByProfileIdIn(@Param("profileIds") List<String> profileIds);

    @Query("SELECT c.profileId, COUNT(c) FROM AllowedContent c WHERE c.profileId IN :profileIds GROUP BY c.profileId")
    List<Object[]> countGroupedByProfileId(@Param("profileIds") List<String> profileIds);

    @Query("SELECT c FROM AllowedContent c WHERE c.profileId IN :profileIds ORDER BY c.createdAt DESC")
    List<AllowedContent> findRecentByProfileIds(@Param("profileIds") List<String> profileIds, Pageable pageable);
}
