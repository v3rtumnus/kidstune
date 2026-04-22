package at.kidstune.insights;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "listening_session")
@Getter
@Setter
public class ListeningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /** "MUSIC", "AUDIOBOOK", or "MIXED" */
    @Column(name = "kind", length = 16, nullable = false)
    private String kind;

    @Column(name = "event_count", nullable = false)
    private int eventCount;
}
