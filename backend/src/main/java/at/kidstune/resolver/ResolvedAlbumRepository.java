package at.kidstune.resolver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ResolvedAlbumRepository extends JpaRepository<ResolvedAlbum, String> {

    List<ResolvedAlbum> findByAllowedContentId(String allowedContentId);

    /** Returns the set of allowedContentIds that have at least one resolved album. */
    @Query("SELECT DISTINCT a.allowedContentId FROM ResolvedAlbum a")
    Set<String> findAllowedContentIdsWithAlbums();

    void deleteByAllowedContentId(String allowedContentId);

    /** Batch-fetch albums for multiple content entries, newest release first. */
    @Query("SELECT a FROM ResolvedAlbum a WHERE a.allowedContentId IN :ids ORDER BY a.releaseDate DESC")
    List<ResolvedAlbum> findByAllowedContentIdInOrderByReleaseDateDesc(@Param("ids") Collection<String> ids);

    /** Albums belonging to existing content that have been re-resolved since the given timestamp. */
    @Query("SELECT a FROM ResolvedAlbum a WHERE a.allowedContentId IN :ids AND a.resolvedAt > :since")
    List<ResolvedAlbum> findByAllowedContentIdInAndResolvedAtAfter(
            @Param("ids") Collection<String> ids, @Param("since") Instant since);
}
