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
@Table(name = "play_event")
@Getter
@Setter
public class PlayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", length = 36, nullable = false)
    private String profileId;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Column(name = "track_id", length = 64, nullable = false)
    private String trackId;

    @Column(name = "track_name", nullable = false, length = 255)
    private String trackName;

    @Column(name = "artist_name", length = 255)
    private String artistName;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    /** "TRACK" or "EPISODE" */
    @Column(name = "item_type", length = 16, nullable = false)
    private String itemType;

    @Column(name = "context_type", length = 32)
    private String contextType;

    @Column(name = "context_uri", length = 128)
    private String contextUri;

    @Column(name = "context_name", length = 255)
    private String contextName;

    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(name = "created_at")
    private Instant createdAt;
}
