package at.kidstune.insights;

import at.kidstune.AbstractIntTest;
import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyWebApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = "insights.poller.enabled=false")
class RecentlyPlayedPollerIntTest extends AbstractIntTest {

    @MockitoBean SpotifyWebApiClient spotifyWebApiClient;

    @Autowired RecentlyPlayedPoller   poller;
    @Autowired PlayEventRepository    eventRepository;
    @Autowired ProfileRepository      profileRepository;
    @Autowired FamilyRepository       familyRepository;

    static final String FAMILY_ID  = UUID.randomUUID().toString();
    static final String PROFILE_ID = UUID.randomUUID().toString();

    // 2 TRACK + 1 EPISODE — mirrors the insights-recently-played.json fixture content
    static final List<SpotifyWebApiClient.RawProfilePlayEvent> THREE_EVENTS = List.of(
        new SpotifyWebApiClient.RawProfilePlayEvent(
            "track1", "Bibi und das Nilpferd", "Hörspiel Studio",
            180_000, "TRACK", Instant.parse("2024-04-21T10:00:00Z"),
            "album", "spotify:album:abc", "{}"),
        new SpotifyWebApiClient.RawProfilePlayEvent(
            "track2", "Bibi und der Zaubertrank", "Hörspiel Studio",
            210_000, "TRACK", Instant.parse("2024-04-21T09:55:00Z"),
            "album", "spotify:album:abc", "{}"),
        new SpotifyWebApiClient.RawProfilePlayEvent(
            "ep1", "Kapitel 3: Der Schatz", "Die Schatzinsel",
            600_000, "EPISODE", Instant.parse("2024-04-21T09:00:00Z"),
            "show", "spotify:show:xyz", "{}")
    );

    ChildProfile profile;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();

        if (!familyRepository.existsById(FAMILY_ID)) {
            Family f = new Family();
            f.setId(FAMILY_ID);
            f.setEmail("insights-poller-test@kidstune.test");
            f.setPasswordHash("irrelevant");
            familyRepository.save(f);
        }

        if (!profileRepository.existsById(PROFILE_ID)) {
            ChildProfile p = new ChildProfile();
            p.setId(PROFILE_ID);
            p.setFamilyId(FAMILY_ID);
            p.setName("Testprofil");
            p.setAvatarIcon(AvatarIcon.BEAR);
            p.setAvatarColor(AvatarColor.BLUE);
            p.setAgeGroup(AgeGroup.SCHOOL);
            p.setSpotifyRefreshToken("encrypted-refresh-token");
            profileRepository.save(p);
        }
        profile = profileRepository.findById(PROFILE_ID).orElseThrow();

        // safe default — overridden in individual tests
        lenient().when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.just(List.of()));
    }

    @Test
    void happyPath_persistsThreeEvents() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.just(THREE_EVENTS));

        poller.pollProfile(profile).block();

        List<PlayEvent> saved = eventRepository.findAll();
        assertThat(saved).hasSize(3);
        assertThat(saved.stream().filter(e -> "EPISODE".equals(e.getItemType()))).hasSize(1);
        assertThat(saved.stream().filter(e -> "TRACK".equals(e.getItemType()))).hasSize(2);
    }

    @Test
    void emptyResponse_persistsZeroEvents() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.just(List.of()));

        poller.pollProfile(profile).block();

        assertThat(eventRepository.findAll()).isEmpty();
    }

    @Test
    void overlappingPoll_deduplicatesEvents() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.just(THREE_EVENTS));
        poller.pollProfile(profile).block();

        // same events polled again → still only 3 rows
        poller.pollProfile(profile).block();

        assertThat(eventRepository.findAll()).hasSize(3);
    }

    @Test
    void spotify429_doesNotCrash_noEventsStored() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.error(
                WebClientResponseException.create(429, "Too Many Requests", null, null, null)));

        poller.pollProfile(profile).block();

        assertThat(eventRepository.findAll()).isEmpty();
    }

    @Test
    void spotify5xx_doesNotCrash_noEventsStored() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.error(
                WebClientResponseException.create(500, "Internal Server Error", null,
                    "{}".getBytes(), null)));

        poller.pollProfile(profile).block();

        assertThat(eventRepository.findAll()).isEmpty();
    }

    @Test
    void invalidGrant_setsTokenExpiredStatus() {
        when(spotifyWebApiClient.getProfileRecentlyPlayed(anyString(), anyLong()))
            .thenReturn(Mono.error(
                WebClientResponseException.create(400, "Bad Request", null,
                    "{\"error\":\"invalid_grant\"}".getBytes(), null)));

        poller.pollProfile(profile).block();

        String status = profileRepository.findById(PROFILE_ID)
            .map(ChildProfile::getInsightsStatus).orElse(null);
        assertThat(status).isEqualTo("TOKEN_EXPIRED");
    }
}
