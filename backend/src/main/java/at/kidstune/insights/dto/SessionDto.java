package at.kidstune.insights.dto;

import at.kidstune.insights.ListeningSession;

import java.time.Instant;
import java.util.List;

public record SessionDto(
        Instant startedAt,
        Instant endedAt,
        int     durationSeconds,
        String  kind,
        int     eventCount,
        List<PlayEventDto> events
) {
    public static SessionDto from(ListeningSession s, List<PlayEventDto> events) {
        return new SessionDto(s.getStartedAt(), s.getEndedAt(),
                s.getDurationSeconds(), s.getKind(), s.getEventCount(), events);
    }

    public static SessionDto from(ListeningSession s) {
        return from(s, List.of());
    }
}
