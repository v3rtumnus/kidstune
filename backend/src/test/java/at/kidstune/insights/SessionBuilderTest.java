package at.kidstune.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionBuilderTest {

    @Mock PlayEventRepository       eventRepo;
    @Mock ListeningSessionRepository sessionRepo;

    SessionBuilder builder;

    static final String PROFILE = "test-profile-id";
    static final Instant BASE   = Instant.parse("2024-04-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        builder = new SessionBuilder(eventRepo, sessionRepo);
        ReflectionTestUtils.setField(builder, "gapMinutes", 10);
        when(sessionRepo.findByProfileIdAndStartedAt(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("single event produces one session")
    void singleEventOneSession() {
        PlayEvent e = event("t1", BASE, 180_000, "TRACK", null);

        builder.buildAndPersistSessions(PROFILE, List.of(e));

        verify(sessionRepo).save(any());
    }

    @Test
    @DisplayName("two events within gap threshold form one session")
    void twoEventsWithinGapOneSession() {
        PlayEvent e1 = event("t1", BASE,                          180_000, "TRACK", null);
        PlayEvent e2 = event("t2", BASE.plusSeconds(3 * 60 + 30), 120_000, "TRACK", null);

        builder.buildAndPersistSessions(PROFILE, List.of(e1, e2));

        ListeningSession saved = captureSingle();
        assertThat(saved.getEventCount()).isEqualTo(2);
        assertThat(saved.getKind()).isEqualTo("MUSIC");
    }

    @Test
    @DisplayName("gap exactly at threshold stays in same session")
    void gapAtThresholdNotSplit() {
        // e2 starts exactly gapMinutes after e1 ends → still same session
        PlayEvent e1 = event("t1", BASE,                   60_000, "TRACK", null);
        PlayEvent e2 = event("t2", BASE.plusSeconds(10 * 60), 60_000, "TRACK", null);

        builder.buildAndPersistSessions(PROFILE, List.of(e1, e2));

        ListeningSession saved = captureSingle();
        assertThat(saved.getEventCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("gap above threshold splits into two sessions")
    void gapAboveThresholdSplits() {
        PlayEvent e1 = event("t1", BASE,                          180_000, "TRACK", null);
        PlayEvent e2 = event("t2", BASE.plusSeconds(15 * 60 + 1), 120_000, "TRACK", null);

        builder.buildAndPersistSessions(PROFILE, List.of(e1, e2));

        var captor = ArgumentCaptor.forClass(ListeningSession.class);
        verify(sessionRepo).save(argThat(s -> BASE.equals(s.getStartedAt())));
        verify(sessionRepo).save(argThat(s -> BASE.plusSeconds(15 * 60 + 1).equals(s.getStartedAt())));
    }

    @Test
    @DisplayName("all TRACK events produce MUSIC kind")
    void allTrackEventsMusicKind() {
        List<PlayEvent> events = List.of(
            event("t1", BASE,                  120_000, "TRACK", null),
            event("t2", BASE.plusSeconds(150), 120_000, "TRACK", "album")
        );

        builder.buildAndPersistSessions(PROFILE, events);

        assertThat(captureSingle().getKind()).isEqualTo("MUSIC");
    }

    @Test
    @DisplayName("all EPISODE events produce AUDIOBOOK kind")
    void allEpisodeEventsAudiobookKind() {
        List<PlayEvent> events = List.of(
            event("e1", BASE,                  600_000, "EPISODE", "show"),
            event("e2", BASE.plusSeconds(610), 600_000, "EPISODE", "show")
        );

        builder.buildAndPersistSessions(PROFILE, events);

        assertThat(captureSingle().getKind()).isEqualTo("AUDIOBOOK");
    }

    @Test
    @DisplayName("audiobook context type classifies as AUDIOBOOK")
    void audiobookContextType() {
        List<PlayEvent> events = List.of(
            event("e1", BASE,                  600_000, "TRACK", "audiobook")
        );

        builder.buildAndPersistSessions(PROFILE, events);

        assertThat(captureSingle().getKind()).isEqualTo("AUDIOBOOK");
    }

    @Test
    @DisplayName("mixed TRACK and EPISODE events produce MIXED kind")
    void mixedEventsMixedKind() {
        List<PlayEvent> events = List.of(
            event("t1", BASE,                  180_000, "TRACK",   "album"),
            event("e1", BASE.plusSeconds(190), 600_000, "EPISODE", "show")
        );

        builder.buildAndPersistSessions(PROFILE, events);

        assertThat(captureSingle().getKind()).isEqualTo("MIXED");
    }

    @Test
    @DisplayName("MIXED session event count includes both music and audiobook events")
    void mixedSessionEventCount() {
        PlayEvent t1 = event("t1", BASE,                  180_000, "TRACK",   "album");
        PlayEvent t2 = event("t2", BASE.plusSeconds(190), 180_000, "TRACK",   "album");
        PlayEvent e1 = event("e1", BASE.plusSeconds(380), 600_000, "EPISODE", "show");

        ListeningSession session = builder.deriveSession(PROFILE, List.of(t1, t2, e1));

        assertThat(session.getKind()).isEqualTo("MIXED");
        assertThat(session.getEventCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("empty input produces no sessions")
    void emptyInputNoSessions() {
        when(eventRepo.findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(sessionRepo.findMaxStartedAtByProfileId(PROFILE))
            .thenReturn(Optional.of(BASE));

        builder.rebuildRecent(PROFILE);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    @DisplayName("events within gap arriving in a later poll cycle merge into the previous session")
    void crossPollMergingWithinGapThreshold() {
        // e1 was already persisted; e2 arrives in the next poll within the gap → should merge
        PlayEvent e1 = event("t1", BASE,              180_000, "TRACK", null); // ends BASE+3min
        PlayEvent e2 = event("t2", BASE.plusSeconds(300), 120_000, "TRACK", null); // gap = 2 min < 10 min

        when(sessionRepo.findMaxStartedAtByProfileId(PROFILE)).thenReturn(Optional.of(BASE));
        when(eventRepo.findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(PROFILE, BASE))
            .thenReturn(List.of(e1, e2));

        builder.rebuildRecent(PROFILE);

        verify(sessionRepo).save(argThat(s -> s.getEventCount() == 2));
    }

    @Test
    @DisplayName("events outside gap arriving in a later poll cycle stay in separate sessions")
    void crossPollNoMergingAboveGapThreshold() {
        // e1 ends BASE+3min; e2 starts BASE+15min → gap = 12 min > 10 min
        PlayEvent e1 = event("t1", BASE,              180_000, "TRACK", null);
        PlayEvent e2 = event("t2", BASE.plusSeconds(900), 120_000, "TRACK", null);

        when(sessionRepo.findMaxStartedAtByProfileId(PROFILE)).thenReturn(Optional.of(BASE));
        when(eventRepo.findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(PROFILE, BASE))
            .thenReturn(List.of(e1, e2));

        builder.rebuildRecent(PROFILE);

        verify(sessionRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("session duration is start to (last played_at + last duration)")
    void sessionDurationComputation() {
        PlayEvent e1 = event("t1", BASE,                  60_000, "TRACK", null);
        PlayEvent e2 = event("t2", BASE.plusSeconds(120), 60_000, "TRACK", null);

        builder.buildAndPersistSessions(PROFILE, List.of(e1, e2));

        // started_at=BASE, ended_at=BASE+180s → 180 seconds
        assertThat(captureSingle().getDurationSeconds()).isEqualTo(180);
    }

    @Test
    @DisplayName("re-derivation is idempotent: updates existing session")
    void rederivationIdempotent() {
        PlayEvent e1 = event("t1", BASE, 180_000, "TRACK", null);
        ListeningSession existing = new ListeningSession();
        existing.setProfileId(PROFILE);
        existing.setStartedAt(BASE);
        existing.setEndedAt(BASE.plusSeconds(180));
        existing.setDurationSeconds(180);
        existing.setKind("MUSIC");
        existing.setEventCount(1);

        when(sessionRepo.findByProfileIdAndStartedAt(eq(PROFILE), eq(BASE)))
            .thenReturn(Optional.of(existing));

        builder.buildAndPersistSessions(PROFILE, List.of(e1));

        verify(sessionRepo).save(existing);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlayEvent event(String trackId, Instant playedAt, int durationMs,
                             String itemType, String contextType) {
        PlayEvent e = new PlayEvent();
        e.setProfileId(PROFILE);
        e.setTrackId(trackId);
        e.setTrackName(trackId);
        e.setPlayedAt(playedAt);
        e.setDurationMs(durationMs);
        e.setItemType(itemType);
        e.setContextType(contextType);
        return e;
    }

    private ListeningSession captureSingle() {
        var captor = ArgumentCaptor.forClass(ListeningSession.class);
        verify(sessionRepo).save(captor.capture());
        return captor.getValue();
    }
}
