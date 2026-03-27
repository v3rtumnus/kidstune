package at.kidstune.content;

import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.BulkAddContentRequest;
import at.kidstune.content.dto.ContentCheckResponse;
import at.kidstune.content.dto.ContentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final ScopeResolver     scopeResolver;

    public ContentService(ContentRepository contentRepository, ScopeResolver scopeResolver) {
        this.contentRepository = contentRepository;
        this.scopeResolver     = scopeResolver;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    public Mono<List<ContentResponse>> listContent(
            String profileId,
            ContentType typeFilter,
            ContentScope scopeFilter,
            String searchQuery) {

        return db(() -> {
            Stream<AllowedContent> stream = contentRepository.findByProfileId(profileId).stream();

            if (typeFilter != null) {
                stream = stream.filter(c -> c.getContentType() == typeFilter);
            }
            if (scopeFilter != null) {
                stream = stream.filter(c -> c.getScope() == scopeFilter);
            }
            if (searchQuery != null && !searchQuery.isBlank()) {
                String q = searchQuery.toLowerCase();
                stream = stream.filter(c ->
                        (c.getTitle()      != null && c.getTitle().toLowerCase().contains(q)) ||
                        (c.getArtistName() != null && c.getArtistName().toLowerCase().contains(q)));
            }
            return stream.map(ContentResponse::from).toList();
        });
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    public Mono<ContentResponse> addContent(String profileId, AddContentRequest request) {
        return db(() -> {
            if (contentRepository.existsByProfileIdAndSpotifyUri(profileId, request.spotifyUri())) {
                throw new ContentException(
                        "Content already exists for this profile", "DUPLICATE_CONTENT", HttpStatus.CONFLICT);
            }
            AllowedContent entity = buildEntity(profileId, request.spotifyUri(), request.scope(),
                    request.title(), request.imageUrl(), request.artistName(), request.contentTypeOverride());
            return ContentResponse.from(contentRepository.save(entity));
        });
    }

    // ── Bulk add ──────────────────────────────────────────────────────────────

    public Mono<List<ContentResponse>> addContentBulk(BulkAddContentRequest request) {
        return db(() -> {
            List<ContentResponse> results = new ArrayList<>();
            for (String profileId : request.profileIds()) {
                if (!contentRepository.existsByProfileIdAndSpotifyUri(profileId, request.spotifyUri())) {
                    AllowedContent entity = buildEntity(profileId, request.spotifyUri(), request.scope(),
                            request.title(), request.imageUrl(), request.artistName(), request.contentTypeOverride());
                    results.add(ContentResponse.from(contentRepository.save(entity)));
                }
            }
            return results;
        });
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    public Mono<Void> removeContent(String profileId, String contentId) {
        return db(() -> {
            AllowedContent content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new ContentException(
                            "Content not found", "CONTENT_NOT_FOUND", HttpStatus.NOT_FOUND));
            if (!content.getProfileId().equals(profileId)) {
                throw new ContentException(
                        "Content not found", "CONTENT_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
            contentRepository.delete(content);
            return null;
        });
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    public Mono<ContentCheckResponse> checkContent(String profileId, String spotifyUri) {
        return scopeResolver.isAllowed(spotifyUri, profileId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AllowedContent buildEntity(String profileId, String spotifyUri, ContentScope scope,
                                       String title, String imageUrl, String artistName,
                                       ContentType contentTypeOverride) {
        AllowedContent entity = new AllowedContent();
        entity.setProfileId(profileId);
        entity.setSpotifyUri(spotifyUri);
        entity.setScope(scope);
        entity.setTitle(title);
        entity.setImageUrl(imageUrl);
        entity.setArtistName(artistName);
        entity.setContentType(contentTypeOverride != null ? contentTypeOverride : ContentType.MUSIC);
        return entity;
    }

    private <T> Mono<T> db(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
