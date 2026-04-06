package at.kidstune.content;

import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.BulkAddContentRequest;
import at.kidstune.content.dto.ContentCheckResponse;
import at.kidstune.content.dto.ContentResponse;
import at.kidstune.content.dto.ImportContentRequest;
import at.kidstune.content.dto.ImportContentResponse;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.resolver.ContentResolver;
import at.kidstune.sync.DeletionLog;
import at.kidstune.sync.DeletionLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ContentService {

    private final ContentRepository     contentRepository;
    private final ScopeResolver         scopeResolver;
    private final ContentTypeClassifier classifier;
    private final ContentResolver       resolver;
    private final DeletionLogRepository deletionLogRepository;
    private final ProfileRepository     profileRepository;

    public ContentService(ContentRepository contentRepository,
                          ScopeResolver scopeResolver,
                          ContentTypeClassifier classifier,
                          ContentResolver resolver,
                          DeletionLogRepository deletionLogRepository,
                          ProfileRepository profileRepository) {
        this.contentRepository    = contentRepository;
        this.scopeResolver        = scopeResolver;
        this.classifier           = classifier;
        this.resolver             = resolver;
        this.deletionLogRepository = deletionLogRepository;
        this.profileRepository    = profileRepository;
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
            ContentType type = resolveContentType(request.contentTypeOverride(), request.spotifyItemInfo());
            AllowedContent entity = buildEntity(profileId, request.spotifyUri(), request.scope(),
                    request.title(), request.imageUrl(), request.artistName(), type);
            return contentRepository.save(entity);
        })
        .doOnSuccess(resolver::resolveAsync)
        .map(ContentResponse::from);
    }

    // ── Bulk add ──────────────────────────────────────────────────────────────

    public Mono<List<ContentResponse>> addContentBulk(BulkAddContentRequest request) {
        return db(() -> {
            ContentType type = resolveContentType(request.contentTypeOverride(), request.spotifyItemInfo());
            List<AllowedContent> saved = new ArrayList<>();
            for (String profileId : request.profileIds()) {
                if (!contentRepository.existsByProfileIdAndSpotifyUri(profileId, request.spotifyUri())) {
                    AllowedContent entity = buildEntity(profileId, request.spotifyUri(), request.scope(),
                            request.title(), request.imageUrl(), request.artistName(), type);
                    saved.add(contentRepository.save(entity));
                }
            }
            return saved;
        })
        .doOnSuccess(savedList -> savedList.forEach(resolver::resolveAsync))
        .map(savedList -> savedList.stream().map(ContentResponse::from).toList());
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Batch-imports content from the import wizard.
     *
     * For each item in the request, creates one {@link AllowedContent} row per profile
     * (skipping profiles that already have that URI). Triggers {@link ContentResolver}
     * asynchronously for every newly created row.
     *
     * @return summary with total created count and per-profile breakdown
     */
    public Mono<ImportContentResponse> importContent(ImportContentRequest request) {
        return db(() -> {
            // profileId → newContentCount
            Map<String, Integer> countByProfile = new LinkedHashMap<>();

            List<AllowedContent> saved = new ArrayList<>();

            for (ImportContentRequest.ImportItem item : request.items()) {
                ContentType type = resolveContentType(item.contentTypeOverride(), item.spotifyItemInfo());

                for (String profileId : item.profileIds()) {
                    if (!contentRepository.existsByProfileIdAndSpotifyUri(profileId, item.spotifyUri())) {
                        AllowedContent entity = buildEntity(profileId, item.spotifyUri(), item.scope(),
                                item.title(), item.imageUrl(), item.artistName(), type);
                        saved.add(contentRepository.save(entity));
                        countByProfile.merge(profileId, 1, Integer::sum);
                    }
                }
            }

            // Build per-profile summary (preserve insertion order)
            List<ImportContentResponse.ProfileSummary> profiles = countByProfile.entrySet().stream()
                    .map(e -> {
                        String name = profileRepository.findById(e.getKey())
                                .map(p -> p.getName())
                                .orElse(e.getKey());
                        return new ImportContentResponse.ProfileSummary(e.getKey(), name, e.getValue());
                    })
                    .toList();

            // Trigger async resolution for every saved entry
            saved.forEach(resolver::resolveAsync);

            int totalCreated = saved.size();
            return new ImportContentResponse(totalCreated, profiles);
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
            logDeletion(profileId, content.getSpotifyUri());
            return null;
        });
    }

    private void logDeletion(String profileId, String spotifyUri) {
        DeletionLog entry = new DeletionLog();
        entry.setProfileId(profileId);
        entry.setType(DeletionLog.DeletionType.CONTENT);
        entry.setSpotifyUri(spotifyUri);
        deletionLogRepository.save(entry);
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    public Mono<ContentCheckResponse> checkContent(String profileId, String spotifyUri) {
        return scopeResolver.isAllowed(spotifyUri, profileId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Manual override always takes precedence.
     * If no override, classify via heuristic.
     * If no item info provided, default to MUSIC.
     */
    private ContentType resolveContentType(ContentType override, SpotifyItemInfo itemInfo) {
        if (override != null) return override;
        return classifier.classify(itemInfo);   // classify(null) → MUSIC
    }

    private AllowedContent buildEntity(String profileId, String spotifyUri, ContentScope scope,
                                       String title, String imageUrl, String artistName,
                                       ContentType contentType) {
        AllowedContent entity = new AllowedContent();
        entity.setProfileId(profileId);
        entity.setSpotifyUri(spotifyUri);
        entity.setScope(scope);
        entity.setTitle(title);
        entity.setImageUrl(imageUrl);
        entity.setArtistName(artistName);
        entity.setContentType(contentType);
        return entity;
    }

    private <T> Mono<T> db(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}