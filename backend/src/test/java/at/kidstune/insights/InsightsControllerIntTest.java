package at.kidstune.insights;

import at.kidstune.AbstractIntTest;
import at.kidstune.auth.DeviceType;
import at.kidstune.auth.JwtTokenService;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.insights.dto.DayResponse;
import at.kidstune.insights.dto.RangeResponse;
import at.kidstune.insights.dto.TodayResponse;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyWebApiClient;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "insights.poller.enabled=false")
class InsightsControllerIntTest extends AbstractIntTest {

    @MockitoBean SpotifyWebApiClient spotifyWebApiClient;

    @Autowired JwtTokenService           jwtTokenService;
    @Autowired FamilyRepository          familyRepository;
    @Autowired ProfileRepository         profileRepository;
    @Autowired PlayEventRepository       eventRepository;
    @Autowired ListeningSessionRepository sessionRepository;
    @Autowired InsightsService           insightsService;

    @LocalServerPort int port;
    WebTestClient client;
    String        parentToken;

    static final String FAMILY_ID          = UUID.randomUUID().toString();
    static final String PROFILE_CONNECTED  = UUID.randomUUID().toString();
    static final String PROFILE_DISCONNECTED = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        eventRepository.deleteAll();
        sessionRepository.deleteAll();

        parentToken = jwtTokenService.createDeviceToken(
            FAMILY_ID, "insights-test-device", DeviceType.PARENT);

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("insights-ctrl-test@kidstune.test");
            f.setPasswordHash("irrelevant");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_CONNECTED)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_CONNECTED);
            p.setFamilyId(FAMILY_ID);
            p.setName("Verbundenes Profil");
            p.setAvatarIcon(AvatarIcon.BEAR);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            p.setSpotifyRefreshToken("encrypted-refresh-token");
            profileRepository.save(p);
        }

        if (!profileRepository.existsById(PROFILE_DISCONNECTED)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_DISCONNECTED);
            p.setFamilyId(FAMILY_ID);
            p.setName("Nicht verbundenes Profil");
            p.setAvatarIcon(AvatarIcon.BUNNY);
            p.setAvatarColor(AvatarColor.GREEN);
            p.setAgeGroup(AgeGroup.SCHOOL);
            profileRepository.save(p);
        }

        // default stubs — overridden per-test where needed
        lenient().when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.just(List.of()));
        lenient().when(spotifyWebApiClient.getCurrentlyPlayingForProfile(anyString()))
            .thenReturn(Mono.just(Optional.empty()));

        // clear live cache between tests to avoid cross-test interference
        @SuppressWarnings("unchecked")
        Cache<String, ?> liveCache = (Cache<String, ?>) ReflectionTestUtils.getField(insightsService, "liveCache");
        if (liveCache != null) liveCache.invalidateAll();
    }

    // ── /today ────────────────────────────────────────────────────────────────

    @Test
    void todayConnectedProfile_returns200WithConnectedTrue() {
        client.get()
            .uri("/api/v1/insights/profiles/{id}/today?tz=Europe/Vienna", PROFILE_CONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(TodayResponse.class)
            .value(r -> assertThat(r.connected()).isTrue());
    }

    @Test
    void todayDisconnectedProfile_returns200WithConnectedFalse() {
        client.get()
            .uri("/api/v1/insights/profiles/{id}/today?tz=Europe/Vienna", PROFILE_DISCONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(TodayResponse.class)
            .value(r -> {
                assertThat(r.connected()).isFalse();
                assertThat(r.status()).isEqualTo("NOT_CONNECTED");
                assertThat(r.sessions()).isEmpty();
            });
    }

    @Test
    void todayWithEvents_computesCorrectTotals() {
        seedEventsForToday();

        client.get()
            .uri("/api/v1/insights/profiles/{id}/today?tz=Europe/Vienna", PROFILE_CONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(TodayResponse.class)
            .value(r -> {
                assertThat(r.totals().musicSeconds()).isEqualTo(360);      // 2 × 180s
                assertThat(r.totals().audiobookSeconds()).isEqualTo(600);  // 1 × 600s
                assertThat(r.totals().totalSeconds()).isEqualTo(960);
            });
    }

    // ── /day ─────────────────────────────────────────────────────────────────

    @Test
    void dayDisconnectedProfile_returns200WithConnectedFalse() {
        client.get()
            .uri("/api/v1/insights/profiles/{id}/day?date=2024-04-21&tz=Europe/Vienna",
                PROFILE_DISCONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(DayResponse.class)
            .value(r -> assertThat(r.connected()).isFalse());
    }

    @Test
    void dayConnectedProfile_returns200() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        client.get()
            .uri("/api/v1/insights/profiles/{id}/day?date={date}&tz=Europe/Vienna",
                PROFILE_CONNECTED, today)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(DayResponse.class)
            .value(r -> assertThat(r.connected()).isTrue());
    }

    // ── /range ────────────────────────────────────────────────────────────────

    @Test
    void rangeDisconnectedProfile_returns200WithConnectedFalse() {
        client.get()
            .uri("/api/v1/insights/profiles/{id}/range?from=2024-04-01&to=2024-04-21",
                PROFILE_DISCONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(RangeResponse.class)
            .value(r -> assertThat(r.connected()).isFalse());
    }

    @Test
    void rangeConnectedProfile_returns200WithSummaries() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String weekAgo = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE);

        client.get()
            .uri("/api/v1/insights/profiles/{id}/range?from={from}&to={to}",
                PROFILE_CONNECTED, weekAgo, today)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(RangeResponse.class)
            .value(r -> {
                assertThat(r.connected()).isTrue();
                assertThat(r.dailySummaries()).isNotNull();
            });
    }

    // ── /live ─────────────────────────────────────────────────────────────────

    @Test
    void liveDisconnectedProfile_returns200WithConnectedFalse() {
        client.get()
            .uri("/api/v1/insights/profiles/{id}/live", PROFILE_DISCONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.connected").isEqualTo(false);
    }

    @Test
    void liveConnectedProfile_nothingPlaying_returns204() {
        when(spotifyWebApiClient.getCurrentlyPlayingForProfile(PROFILE_CONNECTED))
            .thenReturn(Mono.just(Optional.empty()));

        client.get()
            .uri("/api/v1/insights/profiles/{id}/live", PROFILE_CONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    void liveConnectedProfile_playing_returns200WithTrackInfo() {
        when(spotifyWebApiClient.getCurrentlyPlayingForProfile(PROFILE_CONNECTED))
            .thenReturn(Mono.just(Optional.of(new SpotifyWebApiClient.RawCurrentlyPlaying(
                "track123", "Bibi und der Zaubertrank", "Europa",
                180_000, 30_000, "TRACK", true))));

        client.get()
            .uri("/api/v1/insights/profiles/{id}/live", PROFILE_CONNECTED)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + parentToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.playing").isEqualTo(true)
            .jsonPath("$.trackName").isEqualTo("Bibi und der Zaubertrank");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void seedEventsForToday() {
        ZoneId vienna = ZoneId.of("Europe/Vienna");
        Instant todayMorning = LocalDate.now(vienna).atTime(10, 0).atZone(vienna).toInstant();
        PlayEvent t1 = newEvent("t1", "Titel 1", todayMorning,                  180_000, "TRACK",   "album", "spotify:album:abc");
        PlayEvent t2 = newEvent("t2", "Titel 2", todayMorning.plusSeconds(200), 180_000, "TRACK",   "album", "spotify:album:abc");
        PlayEvent e1 = newEvent("e1", "Kapitel 1", todayMorning.minusSeconds(3600), 600_000, "EPISODE", "show", "spotify:show:xyz");
        eventRepository.save(t1);
        eventRepository.save(t2);
        eventRepository.save(e1);
    }

    private PlayEvent newEvent(String trackId, String name, Instant playedAt,
                               int durationMs, String itemType,
                               String ctxType, String ctxUri) {
        PlayEvent e = new PlayEvent();
        e.setProfileId(PROFILE_CONNECTED);
        e.setTrackId(trackId);
        e.setTrackName(name);
        e.setArtistName("Testartist");
        e.setPlayedAt(playedAt);
        e.setDurationMs(durationMs);
        e.setItemType(itemType);
        e.setContextType(ctxType);
        e.setContextUri(ctxUri);
        e.setCreatedAt(Instant.now());
        return e;
    }
}
