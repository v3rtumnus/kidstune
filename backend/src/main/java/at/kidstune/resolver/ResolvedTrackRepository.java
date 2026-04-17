package at.kidstune.resolver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ResolvedTrackRepository extends JpaRepository<ResolvedTrack, String> {

    List<ResolvedTrack> findByResolvedAlbumId(String resolvedAlbumId);

    /**
     * Batch-fetch tracks for multiple albums.
     * Playlist tracks sort by playlistPosition; all others sort by disc + track number.
     */
    @Query("""
            SELECT t FROM ResolvedTrack t WHERE t.resolvedAlbumId IN :ids
            ORDER BY
              CASE WHEN t.playlistPosition IS NOT NULL THEN t.playlistPosition ELSE 9999999 END ASC,
              COALESCE(t.discNumber, 0) ASC,
              COALESCE(t.trackNumber, 0) ASC
            """)
    List<ResolvedTrack> findByResolvedAlbumIdInOrderByDiscTrack(@Param("ids") Collection<String> ids);

    /**
     * Finds resolved tracks that (a) belong to the given profile's approved content and
     * (b) have a URI in the given set. Used by liked-songs import to check against the whitelist.
     */
    @Query("""
            SELECT t FROM ResolvedTrack t
            WHERE t.spotifyTrackUri IN :uris
              AND t.resolvedAlbumId IN (
                  SELECT a.id FROM ResolvedAlbum a
                  WHERE a.allowedContentId IN (
                      SELECT c.id FROM AllowedContent c WHERE c.profileId = :profileId
                  )
              )
            """)
    List<ResolvedTrack> findByProfileIdAndSpotifyTrackUriIn(
            @Param("profileId") String profileId,
            @Param("uris") Collection<String> uris);
}
