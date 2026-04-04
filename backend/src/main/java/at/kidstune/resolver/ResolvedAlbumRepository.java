package at.kidstune.resolver;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResolvedAlbumRepository extends JpaRepository<ResolvedAlbum, String> {

    List<ResolvedAlbum> findByAllowedContentId(String allowedContentId);

    void deleteByAllowedContentId(String allowedContentId);
}
