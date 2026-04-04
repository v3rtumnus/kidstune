package at.kidstune.resolver;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResolvedTrackRepository extends JpaRepository<ResolvedTrack, String> {

    List<ResolvedTrack> findByResolvedAlbumId(String resolvedAlbumId);
}
