package at.kidstune.web;

import at.kidstune.content.*;
import at.kidstune.content.dto.AddContentRequest;
import at.kidstune.content.dto.BulkAddContentRequest;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/web/profiles/{profileId}/content")
public class ContentWebController {

    private static final Logger log = LoggerFactory.getLogger(ContentWebController.class);

    private final ContentService       contentService;
    private final ContentRepository    contentRepository;
    private final ProfileRepository    profileRepository;
    private final SpotifySearchService searchService;
    private final FavoriteRepository   favoriteRepository;

    public ContentWebController(ContentService contentService,
                                ContentRepository contentRepository,
                                ProfileRepository profileRepository,
                                SpotifySearchService searchService,
                                FavoriteRepository favoriteRepository) {
        this.contentService     = contentService;
        this.contentRepository  = contentRepository;
        this.profileRepository  = profileRepository;
        this.searchService      = searchService;
        this.favoriteRepository = favoriteRepository;
    }

    // ── GET /web/profiles/{profileId}/content ─────────────────────────────────

    @GetMapping
    public Mono<String> list(
            @PathVariable("profileId")                           String profileId,
            @RequestParam(value = "type",   required = false)   String type,
            @RequestParam(value = "scope",  required = false)   String scope,
            @RequestParam(value = "search", required = false)   String search,
            @RequestParam(value = "tab",    defaultValue = "content") String tab,
            Model model,
            @AuthenticationPrincipal String familyId) {

        if ("favorites".equals(tab)) {
            return Mono.fromCallable(() -> {
                ChildProfile profile = getProfileForFamily(profileId, familyId);
                model.addAttribute("profile",    profile);
                model.addAttribute("profileId",  profileId);
                model.addAttribute("familyId",   familyId);
                model.addAttribute("activeTab",  "favorites");
                model.addAttribute("favorites",  favoriteRepository.findByProfileId(profileId));
                return "web/content/index";
            }).subscribeOn(Schedulers.boundedElastic());
        }

        ContentType  typeFilter  = parseEnum(ContentType.class,  type);
        ContentScope scopeFilter = parseEnum(ContentScope.class, scope);

        return Mono.fromCallable(() -> {
            ChildProfile profile = getProfileForFamily(profileId, familyId);
            model.addAttribute("profile",     profile);
            model.addAttribute("profileId",   profileId);
            model.addAttribute("familyId",    familyId);
            model.addAttribute("typeFilter",  type);
            model.addAttribute("scopeFilter", scope);
            model.addAttribute("searchQuery", search);
            model.addAttribute("activeTab",   "content");
            return profile;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(profile -> contentService.listContent(profileId, typeFilter, scopeFilter, search))
        .doOnNext(items -> model.addAttribute("contentItems", items))
        .thenReturn("web/content/index");
    }

    // ── GET /web/profiles/{profileId}/content/add ─────────────────────────────

    @GetMapping("/add")
    public Mono<String> addForm(
            @PathVariable("profileId") String profileId,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            ChildProfile profile = getProfileForFamily(profileId, familyId);
            List<ChildProfile> siblings = profileRepository.findByFamilyId(familyId).stream()
                    .filter(p -> !p.getId().equals(profileId))
                    .toList();

            model.addAttribute("profile",    profile);
            model.addAttribute("profileId",  profileId);
            model.addAttribute("familyId",   familyId);
            model.addAttribute("siblings",   siblings);
            model.addAttribute("scopes",     ContentScope.values());
            return "web/content/add";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/profiles/{profileId}/content/search (HTMX) ─────────────────

    @PostMapping("/search")
    public Mono<String> search(
            @PathVariable("profileId") String profileId,
            Model model,
            @AuthenticationPrincipal String familyId,
            ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(form -> {
            String query = form.getFirst("query");
            if (query == null || query.isBlank()) {
                model.addAttribute("results", null);
                model.addAttribute("query", "");
                return Mono.just("web/fragments/search-results :: results");
            }

            return searchService.search(familyId, query.strip(), 20)
                    .doOnNext(results -> {
                        model.addAttribute("results",   results);
                        model.addAttribute("query",     query.strip());
                        model.addAttribute("profileId", profileId);
                        model.addAttribute("scopes",    ContentScope.values());
                    })
                    .thenReturn("web/fragments/search-results :: results")
                    .onErrorResume(e -> {
                        log.warn("Spotify search failed for family {}: {}", familyId, e.getMessage());
                        model.addAttribute("searchError", "Suche fehlgeschlagen. Bitte stelle sicher, dass dein Spotify-Konto verbunden ist.");
                        model.addAttribute("profileId",   profileId);
                        model.addAttribute("scopes",      ContentScope.values());
                        return Mono.just("web/fragments/search-results :: results");
                    });
        });
    }

    // ── POST /web/profiles/{profileId}/content ────────────────────────────────

    @PostMapping
    public Mono<Void> add(
            @PathVariable("profileId") String profileId,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return exchange.getFormData().flatMap(form -> {
            String       spotifyUri  = form.getFirst("spotifyUri");
            ContentScope scope       = parseEnum(ContentScope.class, form.getFirst("scope"));
            String       title       = form.getFirst("title");
            String       imageUrl    = form.getFirst("imageUrl");
            String       artistName  = form.getFirst("artistName");
            ContentType  contentType = parseEnum(ContentType.class, form.getFirst("contentType"));
            List<String> siblingIds  = form.get("siblingIds");

            // Validate profile ownership
            return Mono.fromCallable(() -> getProfileForFamily(profileId, familyId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(profile -> {
                        AddContentRequest req = new AddContentRequest(
                                spotifyUri, scope, title, imageUrl, artistName, contentType, null);

                        if (siblingIds != null && !siblingIds.isEmpty()) {
                            // Add to main profile + siblings
                            List<String> allProfileIds = new java.util.ArrayList<>();
                            allProfileIds.add(profileId);
                            allProfileIds.addAll(siblingIds);
                            BulkAddContentRequest bulk = new BulkAddContentRequest(
                                    spotifyUri, scope, title, imageUrl, artistName, contentType, null, allProfileIds);
                            return contentService.addContentBulk(bulk).then();
                        }
                        return contentService.addContent(profileId, req).then();
                    })
                    .then(Mono.fromRunnable(() -> {
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(
                                URI.create("/web/profiles/" + profileId + "/content"));
                    }))
                    .then(exchange.getResponse().setComplete());
        });
    }

    // ── POST /web/profiles/{profileId}/content/{id}/delete (HTMX) ────────────

    @PostMapping("/{id}/delete")
    public Mono<String> delete(
            @PathVariable("profileId") String profileId,
            @PathVariable("id")        String id,
            Model model,
            @AuthenticationPrincipal String familyId) {

        // Validate profile ownership
        return Mono.fromCallable(() -> getProfileForFamily(profileId, familyId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(profile -> contentService.removeContent(profileId, id))
                .thenReturn("web/fragments/empty :: empty")
                .onErrorResume(e -> {
                    log.warn("Content delete failed for profile {}: {}", profileId, e.getMessage());
                    return Mono.just("web/fragments/empty :: empty");
                });
    }

    // ── POST /web/profiles/{profileId}/content/favorites/{id}/delete (HTMX) ──

    @PostMapping("/favorites/{id}/delete")
    public Mono<String> deleteFavorite(
            @PathVariable("profileId") String profileId,
            @PathVariable("id")        String id,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            // Validate profile ownership before deleting
            getProfileForFamily(profileId, familyId);
            favoriteRepository.findById(id).ifPresent(fav -> {
                if (fav.getProfileId().equals(profileId)) {
                    favoriteRepository.delete(fav);
                }
            });
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty :: empty");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChildProfile getProfileForFamily(String profileId, String familyId) {
        return profileRepository.findById(profileId)
                .filter(p -> p.getFamilyId().equals(familyId))
                .orElseThrow(() -> new at.kidstune.profile.ProfileException(
                        "Profil nicht gefunden", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}