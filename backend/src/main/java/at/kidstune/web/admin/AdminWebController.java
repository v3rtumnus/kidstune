package at.kidstune.web.admin;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.*;
import at.kidstune.profile.dto.ProfileRequest;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestStatus;
import at.kidstune.resolver.ContentResolver;
import at.kidstune.resolver.ResolvedAlbum;
import at.kidstune.resolver.ResolvedAlbumRepository;
import at.kidstune.resolver.ResolvedTrack;
import at.kidstune.resolver.ResolvedTrackRepository;
import at.kidstune.web.AvatarHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/web/admin")
public class AdminWebController {

    private static final Logger log = LoggerFactory.getLogger(AdminWebController.class);
    private static final int PAGE_SIZE = 50;
    private static final Set<String> CONTENT_SORT_FIELDS =
            Set.of("title", "artistName", "scope", "contentType", "createdAt");

    private final FamilyRepository         familyRepository;
    private final ProfileRepository        profileRepository;
    private final ProfileService           profileService;
    private final ContentRepository        contentRepository;
    private final FavoriteRepository       favoriteRepository;
    private final ContentRequestRepository contentRequestRepository;
    private final ResolvedAlbumRepository  resolvedAlbumRepository;
    private final ResolvedTrackRepository  resolvedTrackRepository;
    private final PairedDeviceRepository   pairedDeviceRepository;
    private final ContentResolver          contentResolver;
    private final AvatarHelper             avatarHelper;
    private final AdminWipeService         wipeService;

    public AdminWebController(FamilyRepository familyRepository,
                              ProfileRepository profileRepository,
                              ProfileService profileService,
                              ContentRepository contentRepository,
                              FavoriteRepository favoriteRepository,
                              ContentRequestRepository contentRequestRepository,
                              ResolvedAlbumRepository resolvedAlbumRepository,
                              ResolvedTrackRepository resolvedTrackRepository,
                              PairedDeviceRepository pairedDeviceRepository,
                              ContentResolver contentResolver,
                              AvatarHelper avatarHelper,
                              AdminWipeService wipeService) {
        this.familyRepository         = familyRepository;
        this.profileRepository        = profileRepository;
        this.profileService           = profileService;
        this.contentRepository        = contentRepository;
        this.favoriteRepository       = favoriteRepository;
        this.contentRequestRepository = contentRequestRepository;
        this.resolvedAlbumRepository  = resolvedAlbumRepository;
        this.resolvedTrackRepository  = resolvedTrackRepository;
        this.pairedDeviceRepository   = pairedDeviceRepository;
        this.contentResolver          = contentResolver;
        this.avatarHelper             = avatarHelper;
        this.wipeService              = wipeService;
    }

    // ── Families ──────────────────────────────────────────────────────────────

    @GetMapping({"/", "/families"})
    public Mono<String> families(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<Family> families = familyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            List<FamilyRow> rows = families.stream().map(f -> new FamilyRow(
                    f,
                    profileRepository.countByFamilyId(f.getId()),
                    maskSpotifyId(f.getSpotifyUserId())
            )).toList();
            model.addAttribute("families", rows);
            model.addAttribute("familyId", familyId);
            return "web/admin/families";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Admin Profiles ────────────────────────────────────────────────────────

    @GetMapping("/profiles")
    public Mono<String> adminProfiles(
            @RequestParam(defaultValue = "0") int page,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("createdAt").descending());
            Page<ChildProfile> profilePage = profileRepository.findAll(pageable);

            List<String> profileIds = profilePage.getContent().stream().map(ChildProfile::getId).toList();
            Map<String, Long> contentCounts = profileIds.isEmpty() ? Map.of() :
                    contentRepository.countGroupedByProfileId(profileIds).stream()
                            .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));

            List<AdminProfileRow> rows = profilePage.getContent().stream().map(p -> new AdminProfileRow(
                    p,
                    avatarHelper.emoji(p.getAvatarIcon()),
                    avatarHelper.cssColor(p.getAvatarColor()),
                    contentCounts.getOrDefault(p.getId(), 0L)
            )).toList();

            model.addAttribute("rows", rows);
            model.addAttribute("page", profilePage);
            model.addAttribute("paginationBase", "/web/admin/profiles?page=");
            model.addAttribute("familyId", familyId);
            return "web/admin/profiles";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/profiles/{id}/edit")
    public Mono<String> editProfileForm(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            ChildProfile profile = profileRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            model.addAttribute("profile", profile);
            model.addAttribute("profileId", id);
            model.addAttribute("avatarIcons", AvatarIcon.values());
            model.addAttribute("avatarColors", AvatarColor.values());
            model.addAttribute("ageGroups", AgeGroup.values());
            model.addAttribute("avatarHelper", avatarHelper);
            model.addAttribute("familyId", familyId);
            return "web/admin/profiles-edit";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/profiles/{id}")
    public Mono<String> updateProfile(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId,
            ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(form -> {
            String      name        = form.getFirst("name");
            AvatarIcon  avatarIcon  = parseEnum(AvatarIcon.class,  form.getFirst("avatarIcon"));
            AvatarColor avatarColor = parseEnum(AvatarColor.class, form.getFirst("avatarColor"));
            AgeGroup    ageGroup    = parseEnum(AgeGroup.class,    form.getFirst("ageGroup"));

            String trimmedName = name == null ? "" : name.strip();
            if (trimmedName.isEmpty() || trimmedName.length() > 100) {
                return Mono.fromCallable(() -> {
                    ChildProfile profile = profileRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    model.addAttribute("profile",      profile);
                    model.addAttribute("profileId",    id);
                    model.addAttribute("avatarIcons",  AvatarIcon.values());
                    model.addAttribute("avatarColors", AvatarColor.values());
                    model.addAttribute("ageGroups",    AgeGroup.values());
                    model.addAttribute("avatarHelper", avatarHelper);
                    model.addAttribute("familyId",     familyId);
                    model.addAttribute("nameError",    "Name muss zwischen 1 und 100 Zeichen lang sein.");
                    return "web/admin/profiles-edit";
                }).subscribeOn(Schedulers.boundedElastic());
            }

            return Mono.fromCallable(() -> {
                ChildProfile existing = profileRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                return existing.getFamilyId();
            }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(profileFamilyId -> profileService.updateProfile(id, profileFamilyId,
                    new ProfileRequest(trimmedName, avatarIcon, avatarColor, ageGroup)))
            .thenReturn("redirect:/web/admin/profiles")
            .onErrorResume(ProfileException.class, ex -> Mono.fromCallable(() -> {
                ChildProfile profile = profileRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                model.addAttribute("profile",      profile);
                model.addAttribute("profileId",    id);
                model.addAttribute("avatarIcons",  AvatarIcon.values());
                model.addAttribute("avatarColors", AvatarColor.values());
                model.addAttribute("ageGroups",    AgeGroup.values());
                model.addAttribute("avatarHelper", avatarHelper);
                model.addAttribute("familyId",     familyId);
                model.addAttribute("nameError",    ex.getMessage());
                return "web/admin/profiles-edit";
            }).subscribeOn(Schedulers.boundedElastic()));
        });
    }

    @PostMapping("/profiles/{id}/delete")
    public Mono<Void> deleteProfile(
            @PathVariable("id") String id,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            ChildProfile profile = profileRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            profileRepository.delete(profile);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then(redirect204(exchange, "/web/admin/profiles"));
    }

    // ── Admin Content ─────────────────────────────────────────────────────────

    @GetMapping("/content")
    public Mono<String> adminContent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            String sortField = CONTENT_SORT_FIELDS.contains(sort) ? sort : "createdAt";
            Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(direction, sortField));
            Page<AllowedContent> contentPage = contentRepository.findAll(pageable);

            List<String> profileIds = contentPage.getContent().stream()
                    .map(AllowedContent::getProfileId).distinct().toList();
            Map<String, String> profileNames = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getName));

            List<AdminContentRow> rows = contentPage.getContent().stream().map(c -> new AdminContentRow(
                    c, profileNames.getOrDefault(c.getProfileId(), c.getProfileId())
            )).toList();

            String baseParams = "sort=" + sortField + "&dir=" + dir + "&";
            model.addAttribute("rows", rows);
            model.addAttribute("page", contentPage);
            model.addAttribute("paginationBase", "/web/admin/content?" + baseParams + "page=");
            model.addAttribute("sortField", sortField);
            model.addAttribute("sortDir", dir);
            model.addAttribute("familyId", familyId);
            return "web/admin/content";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/content/{id}/edit")
    public Mono<String> editContentForm(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            AllowedContent content = contentRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            String profileName = profileRepository.findById(content.getProfileId())
                    .map(ChildProfile::getName).orElse(content.getProfileId());
            model.addAttribute("content", content);
            model.addAttribute("profileName", profileName);
            model.addAttribute("contentTypes", ContentType.values());
            model.addAttribute("contentScopes", ContentScope.values());
            model.addAttribute("familyId", familyId);
            return "web/admin/content-edit";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/content/{id}/edit")
    public Mono<String> updateContent(
            @PathVariable("id") String id,
            @AuthenticationPrincipal String familyId,
            ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(form -> {
            ContentType  contentType = parseEnum(ContentType.class,  form.getFirst("contentType"));
            ContentScope scope       = parseEnum(ContentScope.class, form.getFirst("scope"));
            return Mono.fromCallable(() -> {
                AllowedContent content = contentRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                content.setContentType(contentType);
                content.setScope(scope);
                contentRepository.save(content);
                return "redirect:/web/admin/content";
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    @PostMapping("/content/{id}/delete")
    public Mono<String> deleteContent(
            @PathVariable("id") String id,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            contentRepository.findById(id).ifPresent(contentRepository::delete);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty");
    }

    // ── Admin Resolved ────────────────────────────────────────────────────────

    @GetMapping("/resolved")
    public Mono<String> adminResolved(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<AllowedContent> entries = contentRepository.findAll(
                    Sort.by(Sort.Direction.DESC, "createdAt"));

            List<String> entryIds = entries.stream().map(AllowedContent::getId).toList();
            Map<String, Long> albumCounts = entryIds.isEmpty() ? Map.of() :
                    resolvedAlbumRepository.findByAllowedContentIdInOrderByReleaseDateDesc(entryIds)
                            .stream()
                            .collect(Collectors.groupingBy(
                                    ResolvedAlbum::getAllowedContentId, Collectors.counting()));

            List<String> profileIds = entries.stream()
                    .map(AllowedContent::getProfileId).distinct().toList();
            Map<String, String> profileNames = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getName));

            List<AdminResolvedRow> rows = entries.stream().map(e -> new AdminResolvedRow(
                    e,
                    profileNames.getOrDefault(e.getProfileId(), e.getProfileId()),
                    albumCounts.getOrDefault(e.getId(), 0L)
            )).toList();

            model.addAttribute("rows", rows);
            model.addAttribute("familyId", familyId);
            return "web/admin/resolved";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/resolved/{entryId}/albums")
    public Mono<String> resolvedAlbums(
            @PathVariable("entryId") String entryId,
            Model model) {
        return Mono.fromCallable(() -> {
            List<ResolvedAlbum> albums = resolvedAlbumRepository.findByAllowedContentId(entryId);
            List<String> albumIds = albums.stream().map(ResolvedAlbum::getId).toList();
            Map<String, Long> trackCounts = albumIds.isEmpty() ? Map.of() :
                    resolvedTrackRepository.findByResolvedAlbumIdInOrderByDiscTrack(albumIds)
                            .stream()
                            .collect(Collectors.groupingBy(
                                    ResolvedTrack::getResolvedAlbumId, Collectors.counting()));
            model.addAttribute("albums", albums);
            model.addAttribute("trackCounts", trackCounts);
            model.addAttribute("entryId", entryId);
            return "web/admin/fragments/albums :: albums";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/resolved/albums/{albumId}/tracks")
    public Mono<String> resolvedTracks(
            @PathVariable("albumId") String albumId,
            Model model) {
        return Mono.fromCallable(() -> {
            List<ResolvedTrack> tracks = resolvedTrackRepository.findByResolvedAlbumId(albumId);
            model.addAttribute("tracks", tracks);
            return "web/admin/fragments/tracks :: tracks";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/resolved/{entryId}/re-resolve")
    public Mono<String> reResolve(
            @PathVariable("entryId") String entryId,
            Model model) {
        return Mono.fromCallable(() -> {
            AllowedContent content = contentRepository.findById(entryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            contentResolver.resolveAsync(content);
            model.addAttribute("message", "Re-Resolve gestartet");
            return "web/admin/fragments/flash :: success";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Admin Favorites ───────────────────────────────────────────────────────

    @GetMapping("/favorites")
    public Mono<String> adminFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String profileId,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("addedAt").descending());
            Page<Favorite> favPage;
            if (profileId != null && !profileId.isBlank()) {
                favPage = favoriteRepository.findByProfileId(profileId, pageable);
            } else {
                favPage = favoriteRepository.findAll(pageable);
            }

            List<String> profileIds = favPage.getContent().stream()
                    .map(Favorite::getProfileId).distinct().toList();
            Map<String, ChildProfile> profileMap = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, p -> p));

            List<AdminFavoriteRow> rows = favPage.getContent().stream().map(f -> {
                ChildProfile prof = profileMap.get(f.getProfileId());
                return new AdminFavoriteRow(f,
                        prof != null ? prof.getName() : f.getProfileId(),
                        prof != null && prof.getSpotifyUserId() != null);
            }).toList();

            List<ChildProfile> allProfiles = profileRepository.findAll(Sort.by("name"));
            String baseParams = (profileId != null ? "profileId=" + profileId + "&" : "");
            model.addAttribute("rows", rows);
            model.addAttribute("page", favPage);
            model.addAttribute("paginationBase", "/web/admin/favorites?" + baseParams + "page=");
            model.addAttribute("allProfiles", allProfiles);
            model.addAttribute("selectedProfileId", profileId);
            model.addAttribute("familyId", familyId);
            return "web/admin/favorites";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/favorites/{id}/delete")
    public Mono<String> deleteFavorite(@PathVariable("id") String id) {
        return Mono.fromCallable(() -> {
            favoriteRepository.deleteById(id);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty");
    }

    // ── Admin Requests ────────────────────────────────────────────────────────

    @GetMapping("/requests")
    public Mono<String> adminRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            Model model,
            @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("requestedAt").descending());
            ContentRequestStatus statusFilter = parseStatus(status);
            Page<ContentRequest> reqPage = statusFilter != null
                    ? contentRequestRepository.findByStatus(statusFilter, pageable)
                    : contentRequestRepository.findAll(pageable);

            List<String> profileIds = reqPage.getContent().stream()
                    .map(ContentRequest::getProfileId).distinct().toList();
            Map<String, String> profileNames = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getName));

            List<AdminRequestRow> rows = reqPage.getContent().stream().map(r -> new AdminRequestRow(
                    r, profileNames.getOrDefault(r.getProfileId(), r.getProfileId())
            )).toList();

            String baseParams = (status != null ? "status=" + status + "&" : "");
            model.addAttribute("rows", rows);
            model.addAttribute("page", reqPage);
            model.addAttribute("paginationBase", "/web/admin/requests?" + baseParams + "page=");
            model.addAttribute("statuses", ContentRequestStatus.values());
            model.addAttribute("selectedStatus", status);
            model.addAttribute("familyId", familyId);
            return "web/admin/requests";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/requests/{id}/expire")
    public Mono<String> expireRequest(@PathVariable("id") String id) {
        return Mono.fromCallable(() -> {
            contentRequestRepository.findById(id).ifPresent(req -> {
                req.setStatus(ContentRequestStatus.EXPIRED);
                req.setResolvedAt(Instant.now());
                contentRequestRepository.save(req);
            });
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty");
    }

    @PostMapping("/requests/{id}/delete")
    public Mono<String> deleteRequest(@PathVariable("id") String id) {
        return Mono.fromCallable(() -> {
            contentRequestRepository.deleteById(id);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty");
    }

    // ── Admin Devices ─────────────────────────────────────────────────────────

    @GetMapping("/devices")
    public Mono<String> adminDevices(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<PairedDevice> devices = pairedDeviceRepository.findAll(
                    Sort.by(Sort.Direction.DESC, "createdAt"));

            List<String> profileIds = devices.stream()
                    .filter(d -> d.getProfileId() != null)
                    .map(PairedDevice::getProfileId).distinct().toList();
            Map<String, String> profileNames = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getName));

            List<AdminDeviceRow> rows = devices.stream().map(d -> new AdminDeviceRow(
                    d,
                    d.getProfileId() != null
                            ? profileNames.getOrDefault(d.getProfileId(), d.getProfileId())
                            : null
            )).toList();

            model.addAttribute("rows", rows);
            model.addAttribute("familyId", familyId);
            return "web/admin/devices";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/devices/{id}/delete")
    public Mono<String> deleteDevice(@PathVariable("id") String id) {
        return Mono.fromCallable(() -> {
            pairedDeviceRepository.deleteById(id);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn("web/fragments/empty");
    }

    // ── Danger Zone ───────────────────────────────────────────────────────────

    @GetMapping("/danger-zone")
    public Mono<String> dangerZone(Model model, @AuthenticationPrincipal String familyId) {
        model.addAttribute("familyId", familyId);
        return Mono.just("web/admin/danger-zone");
    }

    @GetMapping("/danger-zone/confirm-step")
    public Mono<String> dangerZoneConfirmStep(Model model) {
        return Mono.just("web/admin/fragments/danger-confirm-step :: step");
    }

    @PostMapping("/danger-zone/wipe")
    public Mono<Void> wipe(
            ServerWebExchange exchange,
            Model model) {

        return exchange.getFormData().flatMap(form -> {
            String confirmText = form.getFirst("confirmText");
            if (!"DELETE".equals(confirmText)) {
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            return Mono.fromRunnable(() -> {
                try {
                    wipeService.wipeAllData();
                } catch (Exception e) {
                    log.error("Wipe failed", e);
                    throw new RuntimeException("Wipe failed: " + e.getMessage(), e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then(redirect204(exchange, "/web/register"))
            .onErrorResume(ex -> {
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                String msg = ex.getMessage() != null ? ex.getMessage() : "Unbekannter Fehler";
                exchange.getResponse().getHeaders().setLocation(
                        URI.create("/web/admin/danger-zone?error=" +
                                java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8)));
                return exchange.getResponse().setComplete();
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String maskSpotifyId(String spotifyUserId) {
        if (spotifyUserId == null || spotifyUserId.length() < 6) return spotifyUserId;
        return "***" + spotifyUserId.substring(spotifyUserId.length() - 6);
    }

    private ContentRequestStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return ContentRequestStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Mono<Void> redirect204(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }

    // ── View model records ────────────────────────────────────────────────────

    public record FamilyRow(Family family, long profileCount, String maskedSpotifyId) {}
    public record AdminProfileRow(ChildProfile profile, String emoji, String cssColor, long contentCount) {}
    public record AdminContentRow(AllowedContent content, String profileName) {}
    public record AdminResolvedRow(AllowedContent content, String profileName, long albumCount) {}
    public record AdminFavoriteRow(Favorite favorite, String profileName, boolean spotifyLinked) {}
    public record AdminRequestRow(ContentRequest request, String profileName) {}
    public record AdminDeviceRow(PairedDevice device, String profileName) {}
}
