package at.kidstune.content;

import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.BulkAddContentRequest;
import at.kidstune.content.dto.ContentCheckResponse;
import at.kidstune.content.dto.ContentResponse;
import at.kidstune.content.dto.ImportContentRequest;
import at.kidstune.content.dto.ImportContentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    // ── GET /api/v1/profiles/{profileId}/content ──────────────────────────────

    @GetMapping("/api/v1/profiles/{profileId}/content")
    public Mono<ResponseEntity<List<ContentResponse>>> listContent(
            @PathVariable String profileId,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) ContentScope scope,
            @RequestParam(required = false) String search) {

        return contentService.listContent(profileId, type, scope, search)
                .map(ResponseEntity::ok);
    }

    // ── POST /api/v1/profiles/{profileId}/content ─────────────────────────────

    @PostMapping("/api/v1/profiles/{profileId}/content")
    public Mono<ResponseEntity<ContentResponse>> addContent(
            @PathVariable String profileId,
            @RequestBody @Valid AddContentRequest request) {

        return contentService.addContent(profileId, request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    // ── POST /api/v1/content/bulk ─────────────────────────────────────────────

    @PostMapping("/api/v1/content/bulk")
    public Mono<ResponseEntity<List<ContentResponse>>> addBulk(
            @RequestBody @Valid BulkAddContentRequest request) {

        return contentService.addContentBulk(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    // ── POST /api/v1/content/import ──────────────────────────────────────────

    @PostMapping("/api/v1/content/import")
    public Mono<ResponseEntity<ImportContentResponse>> importContent(
            @RequestBody @Valid ImportContentRequest request) {

        return contentService.importContent(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r));
    }

    // ── DELETE /api/v1/profiles/{profileId}/content/{id} ─────────────────────

    @DeleteMapping("/api/v1/profiles/{profileId}/content/{id}")
    public Mono<ResponseEntity<Void>> removeContent(
            @PathVariable String profileId,
            @PathVariable String id) {

        return contentService.removeContent(profileId, id)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    // ── GET /api/v1/profiles/{profileId}/content/check/{spotifyUri} ──────────

    @GetMapping("/api/v1/profiles/{profileId}/content/check/{spotifyUri}")
    public Mono<ResponseEntity<ContentCheckResponse>> checkContent(
            @PathVariable String profileId,
            @PathVariable String spotifyUri) {

        return contentService.checkContent(profileId, spotifyUri)
                .map(ResponseEntity::ok);
    }
}
