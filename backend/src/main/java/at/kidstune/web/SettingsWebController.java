package at.kidstune.web;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.UUID;

@Controller
@RequestMapping("/web/settings")
public class SettingsWebController {

    private static final Logger log = LoggerFactory.getLogger(SettingsWebController.class);

    private static final String[] SPOTIFY_SCOPES = {
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-library-read",
            "user-read-recently-played",
            "playlist-read-private",
            "streaming",
            "user-read-private"
    };

    private static final String PKCE_PREFIX = "settings_pkce_";

    private final FamilyRepository  familyRepository;
    private final SpotifyTokenService spotifyTokenService;
    private final SpotifyConfig      spotifyConfig;

    public SettingsWebController(FamilyRepository familyRepository,
                                 SpotifyTokenService spotifyTokenService,
                                 SpotifyConfig spotifyConfig) {
        this.familyRepository    = familyRepository;
        this.spotifyTokenService = spotifyTokenService;
        this.spotifyConfig       = spotifyConfig;
    }

    // ── GET /web/settings ────────────────────────────────────────────────────

    @GetMapping
    public Mono<String> settingsPage(
            @RequestParam(value = "spotify", required = false) String spotifyStatus,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            Family family = familyRepository.findById(familyId).orElseThrow();
            model.addAttribute("notificationEmails",
                    family.getNotificationEmails() != null ? family.getNotificationEmails() : "");
            model.addAttribute("familyId", familyId);
            model.addAttribute("saved", false);
            model.addAttribute("spotifyConnected",   family.getSpotifyUserId() != null);
            model.addAttribute("spotifyUserId",      family.getSpotifyUserId());
            model.addAttribute("spotifyJustConnected", "connected".equals(spotifyStatus));
            return "web/settings";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/settings ───────────────────────────────────────────────────

    @PostMapping
    public Mono<String> saveSettings(
            @RequestParam("notificationEmails") String notificationEmails,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            String trimmed = notificationEmails == null ? "" : notificationEmails.strip();
            Family family = familyRepository.findById(familyId).orElseThrow();
            family.setNotificationEmails(trimmed.isEmpty() ? null : trimmed);
            familyRepository.save(family);

            model.addAttribute("notificationEmails", trimmed);
            model.addAttribute("familyId", familyId);
            model.addAttribute("saved", true);
            model.addAttribute("spotifyConnected", family.getSpotifyUserId() != null);
            model.addAttribute("spotifyUserId",    family.getSpotifyUserId());
            model.addAttribute("spotifyJustConnected", false);
            return "web/settings";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── GET /web/settings/connect-spotify ────────────────────────────────────
    // Initiates the Spotify OAuth PKCE flow using the settings-specific redirect URI.

    @GetMapping("/connect-spotify")
    public Mono<Void> connectSpotify(
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        String codeVerifier  = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state         = UUID.randomUUID().toString();

        return exchange.getSession().flatMap(session -> {
            session.getAttributes().put(PKCE_PREFIX + state, codeVerifier);

            String authUrl = spotifyConfig.getAccountsBaseUrl() + "/authorize?" +
                    "client_id="             + encode(spotifyConfig.getClientId()) +
                    "&response_type=code" +
                    "&redirect_uri="         + encode(spotifyConfig.getWebRedirectUri()) +
                    "&scope="                + encode(String.join(" ", SPOTIFY_SCOPES)) +
                    "&state="                + encode(state) +
                    "&code_challenge_method=S256" +
                    "&code_challenge="       + encode(codeChallenge);

            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(authUrl));
            return exchange.getResponse().setComplete();
        });
    }

    // ── GET /web/settings/spotify-callback ───────────────────────────────────
    // Spotify redirects here after the parent grants access from the settings page.

    @GetMapping("/spotify-callback")
    public Mono<Void> spotifyCallback(
            @RequestParam("code")  String code,
            @RequestParam("state") String state,
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return exchange.getSession().flatMap(session -> {
            String verifier = session.getAttribute(PKCE_PREFIX + state);
            if (verifier == null) {
                log.warn("Settings Spotify callback: missing or expired PKCE state '{}' for family {}",
                        state, familyId);
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(
                        URI.create("/web/settings?spotify=error"));
                return exchange.getResponse().setComplete();
            }
            session.getAttributes().remove(PKCE_PREFIX + state);

            return spotifyTokenService
                    .connectSpotifyToFamily(familyId, code, verifier, spotifyConfig.getWebRedirectUri())
                    .then(Mono.fromRunnable(() -> {
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(
                                URI.create("/web/settings?spotify=connected"));
                    }))
                    .then(exchange.getResponse().setComplete())
                    .onErrorResume(e -> {
                        log.error("Settings Spotify callback failed for family {}: {}", familyId, e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(
                                URI.create("/web/settings?spotify=error"));
                        return exchange.getResponse().setComplete();
                    });
        });
    }

    // ── POST /web/settings/disconnect-spotify ────────────────────────────────

    @PostMapping("/disconnect-spotify")
    public Mono<Void> disconnectSpotify(
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromRunnable(() -> {
            Family family = familyRepository.findById(familyId).orElseThrow();
            family.setSpotifyUserId(null);
            family.setSpotifyRefreshToken(null);
            familyRepository.save(family);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then(Mono.fromRunnable(() -> {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/web/settings"));
        }))
        .then(exchange.getResponse().setComplete());
    }

    // ── PKCE helpers ─────────────────────────────────────────────────────────

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
}