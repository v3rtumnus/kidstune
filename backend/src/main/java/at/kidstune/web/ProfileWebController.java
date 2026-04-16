package at.kidstune.web;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.content.ContentRepository;
import at.kidstune.profile.*;
import at.kidstune.profile.dto.ProfileRequest;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/web/profiles")
public class ProfileWebController {

    private static final Logger log = LoggerFactory.getLogger(ProfileWebController.class);

    private static final String[] CHILD_SPOTIFY_SCOPES = {
            "user-read-private",
            "user-library-read",
            "user-library-modify",
            "user-read-recently-played",
            "user-top-read",
            "playlist-read-private"
    };

    private static final String PKCE_PREFIX    = "profile_pkce_";
    private static final String SESSION_PROFILE = "linkSpotifyProfileId";

    private final ProfileRepository    profileRepository;
    private final ProfileService       profileService;
    private final ContentRepository    contentRepository;
    private final SpotifyTokenService  spotifyTokenService;
    private final SpotifyConfig        spotifyConfig;
    private final AvatarHelper         avatarHelper;

    public ProfileWebController(ProfileRepository profileRepository,
                                ProfileService profileService,
                                ContentRepository contentRepository,
                                SpotifyTokenService spotifyTokenService,
                                SpotifyConfig spotifyConfig,
                                AvatarHelper avatarHelper) {
        this.profileRepository  = profileRepository;
        this.profileService     = profileService;
        this.contentRepository  = contentRepository;
        this.spotifyTokenService = spotifyTokenService;
        this.spotifyConfig      = spotifyConfig;
        this.avatarHelper       = avatarHelper;
    }

    // ── GET /web/profiles ─────────────────────────────────────────────────────

    @GetMapping
    public Mono<String> list(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            List<String> profileIds     = profiles.stream().map(ChildProfile::getId).toList();

            Map<String, Long> contentCounts = profileIds.isEmpty()
                    ? Map.of()
                    : contentRepository.countGroupedByProfileId(profileIds).stream()
                        .collect(Collectors.toMap(
                                row -> (String) row[0],
                                row -> (Long)   row[1]));

            List<ProfileListItem> items = profiles.stream()
                    .map(p -> new ProfileListItem(
                            p,
                            avatarHelper.emoji(p.getAvatarIcon()),
                            avatarHelper.cssColor(p.getAvatarColor()),
                            contentCounts.getOrDefault(p.getId(), 0L),
                            p.getSpotifyUserId() != null
                    )).toList();

            model.addAttribute("profiles",  items);
            model.addAttribute("familyId",  familyId);
            return "web/profiles/index";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── GET /web/profiles/new ─────────────────────────────────────────────────

    @GetMapping("/new")
    public Mono<String> newForm(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            addFormLookups(model);
            model.addAttribute("familyId", familyId);
            model.addAttribute("profile",  new ProfileFormData());
            return "web/profiles/form";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/profiles ────────────────────────────────────────────────────

    @PostMapping
    public Mono<String> create(
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
                    addFormLookups(model);
                    model.addAttribute("familyId",   familyId);
                    model.addAttribute("profile",    new ProfileFormData(trimmedName, avatarIcon, avatarColor, ageGroup));
                    model.addAttribute("nameError",  "Name muss zwischen 1 und 100 Zeichen lang sein.");
                    return "web/profiles/form";
                }).subscribeOn(Schedulers.boundedElastic());
            }

            ProfileRequest req = new ProfileRequest(trimmedName, avatarIcon, avatarColor, ageGroup);
            return profileService.createProfile(familyId, req)
                    .thenReturn("redirect:/web/profiles")
                    .onErrorResume(ProfileException.class, ex -> Mono.fromCallable(() -> {
                        addFormLookups(model);
                        model.addAttribute("familyId",  familyId);
                        model.addAttribute("profile",   new ProfileFormData(trimmedName, avatarIcon, avatarColor, ageGroup));
                        model.addAttribute("nameError", ex.getMessage());
                        return "web/profiles/form";
                    }).subscribeOn(Schedulers.boundedElastic()));
        });
    }

    // ── GET /web/profiles/{id}/edit ───────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public Mono<String> editForm(
            @PathVariable("id") String id,
            @RequestParam(value = "linked",   required = false) String linked,
            @RequestParam(value = "unlinked", required = false) String unlinked,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            ChildProfile profile = getProfileForFamily(id, familyId);
            addFormLookups(model);
            model.addAttribute("familyId",       familyId);
            model.addAttribute("profileId",      id);
            model.addAttribute("profile",        new ProfileFormData(
                    profile.getName(), profile.getAvatarIcon(),
                    profile.getAvatarColor(), profile.getAgeGroup()));
            model.addAttribute("spotifyLinked",     profile.getSpotifyUserId() != null);
            model.addAttribute("spotifyUserId",     profile.getSpotifyUserId());
            model.addAttribute("justLinked",        linked != null);
            model.addAttribute("justUnlinked",      unlinked != null);
            return "web/profiles/edit";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/profiles/{id} ───────────────────────────────────────────────

    @PostMapping("/{id}")
    public Mono<String> update(
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
                    ChildProfile profile = getProfileForFamily(id, familyId);
                    addFormLookups(model);
                    model.addAttribute("familyId",      familyId);
                    model.addAttribute("profileId",     id);
                    model.addAttribute("profile",       new ProfileFormData(trimmedName, avatarIcon, avatarColor, ageGroup));
                    model.addAttribute("nameError",     "Name muss zwischen 1 und 100 Zeichen lang sein.");
                    model.addAttribute("spotifyLinked", profile.getSpotifyUserId() != null);
                    model.addAttribute("spotifyUserId", profile.getSpotifyUserId());
                    return "web/profiles/edit";
                }).subscribeOn(Schedulers.boundedElastic());
            }

            ProfileRequest req = new ProfileRequest(trimmedName, avatarIcon, avatarColor, ageGroup);
            return profileService.updateProfile(id, familyId, req)
                    .thenReturn("redirect:/web/profiles")
                    .onErrorResume(ProfileException.class, ex -> Mono.fromCallable(() -> {
                        ChildProfile profile = getProfileForFamily(id, familyId);
                        addFormLookups(model);
                        model.addAttribute("familyId",      familyId);
                        model.addAttribute("profileId",     id);
                        model.addAttribute("profile",       new ProfileFormData(trimmedName, avatarIcon, avatarColor, ageGroup));
                        model.addAttribute("nameError",     ex.getMessage());
                        model.addAttribute("spotifyLinked", profile.getSpotifyUserId() != null);
                        model.addAttribute("spotifyUserId", profile.getSpotifyUserId());
                        return "web/profiles/edit";
                    }).subscribeOn(Schedulers.boundedElastic()));
        });
    }

    // ── POST /web/profiles/{id}/delete ────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public Mono<Void> delete(
            @PathVariable("id") String id,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return profileService.deleteProfile(id, familyId)
                .then(redirect204(exchange, "/web/profiles"));
    }

    // ── POST /web/profiles/{id}/unlink-spotify ────────────────────────────────

    @PostMapping("/{id}/unlink-spotify")
    public Mono<Void> unlinkSpotify(
            @PathVariable("id") String id,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromRunnable(() -> {
            ChildProfile profile = getProfileForFamily(id, familyId);
            profile.setSpotifyUserId(null);
            profile.setSpotifyRefreshToken(null);
            profileRepository.save(profile);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then(redirect204(exchange, "/web/profiles/" + id + "/edit?unlinked=true"));
    }

    // ── GET /web/profiles/{id}/link-spotify ───────────────────────────────────

    @GetMapping("/{id}/link-spotify")
    public Mono<Void> linkSpotify(
            @PathVariable("id") String id,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        // Validate ownership before starting the OAuth flow
        return Mono.fromCallable(() -> getProfileForFamily(id, familyId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(profile -> exchange.getSession())
                .flatMap(session -> {
                    String verifier   = generateCodeVerifier();
                    String challenge  = generateCodeChallenge(verifier);
                    String state      = UUID.randomUUID().toString();

                    session.getAttributes().put(PKCE_PREFIX + state, verifier);
                    session.getAttributes().put(SESSION_PROFILE + state, id);

                    String authUrl = spotifyConfig.getAccountsBaseUrl() + "/authorize?"
                            + "client_id="             + encode(spotifyConfig.getClientId())
                            + "&response_type=code"
                            + "&redirect_uri="         + encode(spotifyConfig.getProfileRedirectUri())
                            + "&scope="                + encode(String.join(" ", CHILD_SPOTIFY_SCOPES))
                            + "&state="                + encode(state)
                            + "&code_challenge_method=S256"
                            + "&code_challenge="       + encode(challenge);

                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create(authUrl));
                    return exchange.getResponse().setComplete();
                });
    }

    // ── GET /web/profiles/spotify-callback ───────────────────────────────────

    @GetMapping("/spotify-callback")
    public Mono<Void> spotifyCallback(
            @RequestParam("code")  String code,
            @RequestParam("state") String state,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return exchange.getSession().flatMap(session -> {
            String verifier   = session.getAttribute(PKCE_PREFIX + state);
            String profileId  = session.getAttribute(SESSION_PROFILE + state);

            if (verifier == null || profileId == null) {
                log.warn("Profile Spotify callback: missing PKCE state '{}' for family {}", state, familyId);
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create("/web/profiles"));
                return exchange.getResponse().setComplete();
            }

            session.getAttributes().remove(PKCE_PREFIX + state);
            session.getAttributes().remove(SESSION_PROFILE + state);

            return spotifyTokenService
                    .connectSpotifyToProfile(profileId, code, verifier, spotifyConfig.getProfileRedirectUri())
                    .then(Mono.fromRunnable(() -> {
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(
                                URI.create("/web/profiles/" + profileId + "/edit?linked=true"));
                    }))
                    .then(exchange.getResponse().setComplete())
                    .onErrorResume(e -> {
                        log.error("Profile Spotify callback failed for family {}: {}", familyId, e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(
                                URI.create("/web/profiles/" + profileId + "/edit?spotify=error"));
                        return exchange.getResponse().setComplete();
                    });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChildProfile getProfileForFamily(String profileId, String familyId) {
        return profileRepository.findById(profileId)
                .filter(p -> p.getFamilyId().equals(familyId))
                .orElseThrow(() -> new at.kidstune.profile.ProfileException(
                        "Profil nicht gefunden", "PROFILE_NOT_FOUND",
                        HttpStatus.NOT_FOUND));
    }

    private void addFormLookups(Model model) {
        model.addAttribute("avatarIcons",  AvatarIcon.values());
        model.addAttribute("avatarColors", AvatarColor.values());
        model.addAttribute("ageGroups",    AgeGroup.values());
        model.addAttribute("avatarHelper", avatarHelper);
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        return Mono.fromRunnable(() -> {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(location));
        });
    }

    private Mono<Void> redirect204(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ── View model records ────────────────────────────────────────────────────

    public record ProfileListItem(
            ChildProfile profile,
            String emoji,
            String cssColor,
            long contentCount,
            boolean spotifyLinked
    ) {}

    public static class ProfileFormData {
        public String      name;
        public AvatarIcon  avatarIcon;
        public AvatarColor avatarColor;
        public AgeGroup    ageGroup;

        public ProfileFormData() {}

        public ProfileFormData(String name, AvatarIcon avatarIcon,
                               AvatarColor avatarColor, AgeGroup ageGroup) {
            this.name        = name;
            this.avatarIcon  = avatarIcon;
            this.avatarColor = avatarColor;
            this.ageGroup    = ageGroup;
        }
    }
}