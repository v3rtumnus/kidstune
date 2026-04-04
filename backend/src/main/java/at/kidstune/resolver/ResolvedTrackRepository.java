package at.kidstune.resolver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ResolvedTrackRepository extends JpaRepository<ResolvedTrack, String> {

    List<ResolvedTrack> findByResolvedAlbumId(String resolvedAlbumId);

    /** Batch-fetch tracks for multiple albums, ordered by disc then track number. */
    @Query("SELECT t FROM ResolvedTrack t WHERE t.resolvedAlbumId IN :ids ORDER BY t.discNumber ASC, t.trackNumber ASC")
    List<ResolvedTrack> findByResolvedAlbumIdInOrderByDiscTrack(@Param("ids") Collection<String> ids);
}
