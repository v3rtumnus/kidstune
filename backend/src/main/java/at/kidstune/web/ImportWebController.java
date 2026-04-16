package at.kidstune.web;

import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentService;
import at.kidstune.content.dto.ImportContentRequest;
import at.kidstune.content.dto.ImportContentResponse;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileException;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyImportService;
import at.kidstune.spotify.ImportSuggestionsDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.*;

/**
 * Web-dashboard controller for the import wizard (phase 6).
 *
 * Serves the multi-step Spotify import wizard at /web/import and also provides
 * the legacy liked-songs REST endpoint at /api/v1/profiles/{profileId}/import-liked-songs.
 */
@Controller
public class ImportWebController {

    private static final Logger log = LoggerFactory.getLogger(ImportWebController.class);

    /** WebSession key used to flash the import result across the POST-Redirect-GET. */
    static final String SESSION_IMPORT_RESULT = "importResult";

    private final SpotifyImportService spotifyImportService;
    private final ContentService       contentService;
    private final ProfileRepository    profileRepository;
    private final SpotifyTokenService  spotifyTokenService;
    private final ObjectMapper         objectMapper;

    public ImportWebController(SpotifyImportService spotifyImportService,
                               ContentService contentService,
                               ProfileRepository profileRepository,
                               SpotifyTokenService spotifyTokenService,
                               ObjectMapper objectMapper) {
        this.spotifyImportService = spotifyImportService;
        this.contentService       = contentService;
        this.profileRepository    = profileRepository;
        this.spotifyTokenService  = spotifyTokenService;
        this.objectMapper         = objectMapper;
    }

    // ── Wizard: GET /web/import ────────────────────────────────────────────────

    @GetMapping("/web/import")
    public Mono<String> importStep1(Model model,
                                    @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            List<ProfileImportItem> items = profiles.stream()
                    .map(p -> new ProfileImportItem(p,
                            spotifyTokenService.isProfileSpotifyLinked(p.getId())))
                    .toList();
            model.addAttribute("profiles", items);
            model.addAttribute("familyId", familyId);
            return "web/import/step1";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Wizard: POST /web/import/suggestions (HTMX) ───────────────────────────

    @PostMapping("/web/import/suggestions")
    public Mono<String> loadSuggestions(
            Model model,
            @AuthenticationPrincipal String familyId,
            ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(form -> {
            List<String> profileIds = form.get("allProfileIds");
            return loadSuggestionsInternal(profileIds, model, familyId);
        });
    }

    private Mono<String> loadSuggestionsInternal(
            List<String> profileIds,
            Model model,
            String familyId) {

        if (profileIds == null || profileIds.isEmpty()) {
            model.addAttribute("noProfilesSelected", true);
            return Mono.just("web/fragments/import-suggestions :: suggestions");
        }

        return Mono.fromCallable(() -> {
            // Verify each profile belongs to this family
            List<ChildProfile> validProfiles = profileIds.stream()
                    .map(id -> profileRepository.findById(id)
                            .filter(p -> p.getFamilyId().equals(familyId))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
            return validProfiles;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(profiles -> {
            if (profiles.isEmpty()) {
                model.addAttribute("noProfilesSelected", true);
                return Mono.just("web/fragments/import-suggestions :: suggestions");
            }

            // Find first profile with Spotify linked — its listening history drives suggestions
            Optional<ChildProfile> source = profiles.stream()
                    .filter(p -> spotifyTokenService.isProfileSpotifyLinked(p.getId()))
                    .findFirst();

            if (source.isEmpty()) {
                model.addAttribute("noSpotifyLinked", true);
                model.addAttribute("profiles", profiles);
                return Mono.just("web/fragments/import-suggestions :: suggestions");
            }

            return spotifyImportService.getImportSuggestions(source.get().getId())
                    .doOnNext(suggestions -> {
                        model.addAttribute("suggestions", suggestions);
                        model.addAttribute("profiles", profiles);
                        model.addAttribute("noSpotifyLinked", false);
                        model.addAttribute("noProfilesSelected", false);
                    })
                    .thenReturn("web/fragments/import-suggestions :: suggestions")
                    .onErrorResume(e -> {
                        log.warn("Failed to load import suggestions for family {}: {}", familyId, e.getMessage());
                        model.addAttribute("suggestionsError", true);
                        model.addAttribute("profiles", profiles);
                        return Mono.just("web/fragments/import-suggestions :: suggestions");
                    });
        });
    }

    // ── Wizard: POST /web/import ───────────────────────────────────────────────

    @PostMapping("/web/import")
    public Mono<Void> executeImport(
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return exchange.getFormData().flatMap(form -> {
            String       itemsJson    = form.getFirst("itemsJson");
            List<String> allProfileIds = form.get("allProfileIds");
            return executeImportInternal(itemsJson, allProfileIds, exchange, familyId);
        });
    }

    private Mono<Void> executeImportInternal(
            String itemsJson,
            List<String> allProfileIds,
            ServerWebExchange exchange,
            String familyId) {

        return Mono.fromCallable(() -> buildImportRequest(itemsJson, familyId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(importItems -> {
                    if (importItems.isEmpty()) {
                        return Mono.just(new ImportContentResponse(0, List.of()));
                    }
                    return contentService.importContent(new ImportContentRequest(importItems));
                })
                .flatMap(importResponse ->
                    importLikedSongsForProfiles(allProfileIds, familyId)
                            .map(likedCounts -> buildSuccessEntries(importResponse, likedCounts, familyId))
                )
                .flatMap(entries ->
                    exchange.getSession().flatMap(session -> {
                        session.getAttributes().put(SESSION_IMPORT_RESULT, new ImportResult(entries));
                        return Mono.empty();
                    })
                )
                .then(Mono.fromRunnable(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create("/web/import/success"));
                }))
                .then(exchange.getResponse().setComplete());
    }

    // ── Wizard: GET /web/import/success ───────────────────────────────────────

    @GetMapping("/web/import/success")
    public Mono<String> importSuccess(Model model,
                                      ServerWebExchange exchange,
                                      @AuthenticationPrincipal String familyId) {
        return exchange.getSession().map(session -> {
            ImportResult result = (ImportResult) session.getAttribute(SESSION_IMPORT_RESULT);
            session.getAttributes().remove(SESSION_IMPORT_RESULT);
            model.addAttribute("result", result != null ? result : new ImportResult(List.of()));
            model.addAttribute("familyId", familyId);
            return "web/import/success";
        });
    }

    // ── Legacy REST endpoint ──────────────────────────────────────────────────

    /**
     * Called by the import wizard after content import completes.
     * Imports the child's Spotify Liked Songs as KidsTune favorites.
     *
     * @return {@code { "imported": N }} where N is the number of new favorites created
     */
    @PostMapping("/api/v1/profiles/{profileId}/import-liked-songs")
    @ResponseBody
    public Mono<ResponseEntity<Map<String, Integer>>> importLikedSongs(
            @PathVariable String profileId,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            profileRepository.findById(profileId)
                    .filter(p -> p.getFamilyId().equals(familyId))
                    .orElseThrow(() -> new ProfileException(
                            "Profil nicht gefunden", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));
            return profileId;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(id -> spotifyImportService.importLikedSongsAsFavorites(id))
        .map(count -> ResponseEntity.ok(Map.of("imported", count)));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<ImportContentRequest.ImportItem> buildImportRequest(String itemsJson, String familyId) {
        if (itemsJson == null || itemsJson.isBlank() || itemsJson.equals("[]")) {
            return List.of();
        }
        List<WizardItem> wizardItems;
        try {
            wizardItems = objectMapper.readValue(itemsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse itemsJson: {}", e.getMessage());
            return List.of();
        }

        return wizardItems.stream()
                .filter(wi -> wi.spotifyUri() != null && wi.profileIds() != null && !wi.profileIds().isEmpty())
                .map(wi -> {
                    ContentScope scope;
                    try {
                        scope = ContentScope.valueOf(wi.scope().toUpperCase());
                    } catch (Exception e) {
                        scope = ContentScope.ARTIST;
                    }
                    // Verify each profile actually belongs to this family
                    List<String> validProfiles = wi.profileIds().stream()
                            .filter(pid -> profileRepository.findById(pid)
                                    .map(p -> p.getFamilyId().equals(familyId))
                                    .orElse(false))
                            .toList();
                    if (validProfiles.isEmpty()) return null;
                    return new ImportContentRequest.ImportItem(
                            wi.spotifyUri(), scope, wi.title(), wi.imageUrl(), wi.artistName(),
                            null, null, validProfiles);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Imports liked songs for all selected profiles that have Spotify linked.
     * Profiles without Spotify silently return 0 (no exception).
     *
     * @return Mono of map: profileId → liked-songs-imported count
     */
    private Mono<Map<String, Integer>> importLikedSongsForProfiles(List<String> allProfileIds,
                                                                    String familyId) {
        if (allProfileIds == null || allProfileIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<String> validIds = allProfileIds.stream()
                .filter(id -> profileRepository.findById(id)
                        .map(p -> p.getFamilyId().equals(familyId))
                        .orElse(false)
                        && spotifyTokenService.isProfileSpotifyLinked(id))
                .toList();

        if (validIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return Flux.fromIterable(validIds)
                .flatMap(pid -> spotifyImportService.importLikedSongsAsFavorites(pid)
                        .onErrorReturn(0)
                        .map(count -> Map.entry(pid, count)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private List<SuccessEntry> buildSuccessEntries(ImportContentResponse importResponse,
                                                    Map<String, Integer> likedCounts,
                                                    String familyId) {
        // Index content counts by profileId
        Map<String, Integer> contentCountByProfile = new HashMap<>();
        for (ImportContentResponse.ProfileSummary summary : importResponse.profiles()) {
            contentCountByProfile.put(summary.id(), summary.newContentCount());
        }

        // Union of all profileIds (those that got content + those that got liked songs)
        Set<String> allIds = new LinkedHashSet<>();
        importResponse.profiles().stream().map(ImportContentResponse.ProfileSummary::id).forEach(allIds::add);
        allIds.addAll(likedCounts.keySet());

        return allIds.stream()
                .map(pid -> {
                    String name = profileRepository.findById(pid)
                            .map(ChildProfile::getName)
                            .orElse(pid);
                    int content = contentCountByProfile.getOrDefault(pid, 0);
                    int liked   = likedCounts.getOrDefault(pid, 0);
                    return new SuccessEntry(name, content, liked);
                })
                .filter(e -> e.contentAdded() > 0 || e.likedSongsImported() > 0)
                .toList();
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** View model for profile selection in step 1. */
    public record ProfileImportItem(ChildProfile profile, boolean spotifyLinked) {}

    /** Parsed item from the JS-serialized itemsJson form field. */
    record WizardItem(
            String spotifyUri,
            String scope,
            String title,
            String imageUrl,
            String artistName,
            List<String> profileIds
    ) {}

    /** Per-profile result row stored in session and shown on success page. */
    public record SuccessEntry(String profileName, int contentAdded, int likedSongsImported) {}

    /** Flash data stored in WebSession across the POST-Redirect-GET. */
    public record ImportResult(List<SuccessEntry> entries) {}
}
