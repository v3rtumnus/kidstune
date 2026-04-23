package at.kidstune.insights;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SessionBuilder {

    private static final Logger log = LoggerFactory.getLogger(SessionBuilder.class);

    private final PlayEventRepository      eventRepo;
    private final ListeningSessionRepository sessionRepo;

    @Value("${insights.session.gap-minutes:10}")
    private int gapMinutes;

    public SessionBuilder(PlayEventRepository eventRepo,
                          ListeningSessionRepository sessionRepo) {
        this.eventRepo   = eventRepo;
        this.sessionRepo = sessionRepo;
    }

    /**
     * Rebuilds sessions for {@code profileId} starting from the latest known session
     * end, or the last 48 hours if no sessions exist yet.  Safe to re-run — upserts
     * by (profile_id, started_at).
     */
    @Transactional
    public void rebuildRecent(String profileId) {
        Optional<Instant> lastSessionEnd = sessionRepo.findMaxEndedAtByProfileId(profileId);
        Instant from = lastSessionEnd.orElse(Instant.now().minus(48, ChronoUnit.HOURS));

        List<PlayEvent> events = eventRepo
                .findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(profileId, from);

        if (events.isEmpty()) return;

        buildAndPersistSessions(profileId, events);
    }

    // package-private for tests
    @Transactional
    void buildAndPersistSessions(String profileId, List<PlayEvent> events) {
        if (events.isEmpty()) return;

        long gapMillis = (long) gapMinutes * 60_000;
        List<List<PlayEvent>> groups = groupIntoSessions(events, gapMillis);

        for (List<PlayEvent> group : groups) {
            ListeningSession session = deriveSession(profileId, group);
            upsert(session);
        }

        log.debug("Rebuilt {} session(s) for profile {}", groups.size(), profileId);
    }

    private List<List<PlayEvent>> groupIntoSessions(List<PlayEvent> events, long gapMillis) {
        List<List<PlayEvent>> groups = new ArrayList<>();
        List<PlayEvent> current = new ArrayList<>();

        for (PlayEvent event : events) {
            if (current.isEmpty()) {
                current.add(event);
                continue;
            }

            PlayEvent prev = current.get(current.size() - 1);
            long prevEndMs = prev.getPlayedAt().toEpochMilli() + prev.getDurationMs();
            long gap = event.getPlayedAt().toEpochMilli() - prevEndMs;

            if (gap > gapMillis) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(event);
        }

        if (!current.isEmpty()) groups.add(current);
        return groups;
    }

    ListeningSession deriveSession(String profileId, List<PlayEvent> events) {
        PlayEvent first = events.get(0);
        PlayEvent last  = events.get(events.size() - 1);

        Instant startedAt = first.getPlayedAt();
        Instant endedAt   = last.getPlayedAt().plusMillis(last.getDurationMs());
        int durationSec   = (int) ChronoUnit.SECONDS.between(startedAt, endedAt);

        String kind = classifyKind(events);

        ListeningSession s = new ListeningSession();
        s.setProfileId(profileId);
        s.setStartedAt(startedAt);
        s.setEndedAt(endedAt);
        s.setDurationSeconds(Math.max(0, durationSec));
        s.setKind(kind);
        s.setEventCount(events.size());
        return s;
    }

    private String classifyKind(List<PlayEvent> events) {
        boolean hasMusic     = false;
        boolean hasAudiobook = false;

        for (PlayEvent e : events) {
            if (isAudiobook(e)) {
                hasAudiobook = true;
            } else {
                hasMusic = true;
            }
        }

        if (hasAudiobook && !hasMusic) return "AUDIOBOOK";
        if (hasMusic && !hasAudiobook) return "MUSIC";
        return "MIXED";
    }

    private boolean isAudiobook(PlayEvent e) {
        if (e.getKind() != null) return "AUDIOBOOK".equals(e.getKind());
        return "EPISODE".equals(e.getItemType()) ||
               "audiobook".equals(e.getContextType()) ||
               "show".equals(e.getContextType());
    }

    private void upsert(ListeningSession incoming) {
        sessionRepo.findByProfileIdAndStartedAt(
                incoming.getProfileId(), incoming.getStartedAt())
            .ifPresentOrElse(existing -> {
                existing.setEndedAt(incoming.getEndedAt());
                existing.setDurationSeconds(incoming.getDurationSeconds());
                existing.setKind(incoming.getKind());
                existing.setEventCount(incoming.getEventCount());
                sessionRepo.save(existing);
            }, () -> sessionRepo.save(incoming));
    }
}
