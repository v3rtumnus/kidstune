package at.kidstune.content;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure, stateless classifier that maps Spotify item metadata to {@link ContentType}.
 *
 * Implements the §4.3 heuristic in order:
 * <ol>
 *   <li>Strong signals – Spotify native audiobook type, audiobook/hörspiel/spoken-word genre</li>
 *   <li>Medium signals – track count + average duration, German episode naming pattern</li>
 *   <li>Children's music genres – explicit MUSIC signal</li>
 *   <li>Default → MUSIC</li>
 * </ol>
 *
 * Manual parent override (contentTypeOverride) is applied in
 * {@link ContentService} before this classifier is called.
 */
@Service
public class ContentTypeClassifier {

    private static final long FIVE_MINUTES_MS    = 5L * 60 * 1_000;
    private static final int  TRACK_COUNT_THRESHOLD = 20;

    /** Genres that unambiguously signal audiobook/spoken-word content (case-insensitive substring). */
    private static final Set<String> AUDIOBOOK_GENRES = Set.of(
            "hörspiel", "audiobook", "spoken word"
    );

    /** Genres that signal children's music content (case-insensitive substring). */
    private static final Set<String> CHILDREN_MUSIC_GENRES = Set.of(
            "children's music", "kindermusik", "kinderlieder"
    );

    /** Common German naming patterns for serialised audiobooks/Hörspiele. */
    private static final Pattern EPISODE_PATTERN =
            Pattern.compile("(?i)(Folge|Episode|Teil|Kapitel)\\s+\\d+");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Classifies a Spotify item.
     *
     * @param item metadata provided by the caller; null → returns MUSIC
     */
    public ContentType classify(SpotifyItemInfo item) {
        if (item == null) return ContentType.MUSIC;

        // ── Strong signals ────────────────────────────────────────────────────
        if ("audiobook".equalsIgnoreCase(item.type())) {
            return ContentType.AUDIOBOOK;
        }

        if (containsAudiobookGenre(item.genres())) {
            return ContentType.AUDIOBOOK;
        }

        // ── Medium signals ────────────────────────────────────────────────────
        if (item.totalTracks() > TRACK_COUNT_THRESHOLD
                && item.averageTrackDurationMs() > FIVE_MINUTES_MS) {
            return ContentType.AUDIOBOOK;
        }

        if (item.albumName() != null
                && EPISODE_PATTERN.matcher(item.albumName()).find()) {
            return ContentType.AUDIOBOOK;
        }

        // ── Children's music signal ───────────────────────────────────────────
        if (containsChildrenMusicGenre(item.genres())) {
            return ContentType.MUSIC;
        }

        // ── Default ───────────────────────────────────────────────────────────
        return ContentType.MUSIC;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean containsAudiobookGenre(List<String> genres) {
        return matchesAnySignal(genres, AUDIOBOOK_GENRES);
    }

    private boolean containsChildrenMusicGenre(List<String> genres) {
        return matchesAnySignal(genres, CHILDREN_MUSIC_GENRES);
    }

    private boolean matchesAnySignal(List<String> genres, Set<String> signals) {
        if (genres == null || genres.isEmpty()) return false;
        return genres.stream()
                .filter(g -> g != null && !g.isBlank())
                .anyMatch(g -> {
                    String lower = g.toLowerCase();
                    return signals.stream().anyMatch(lower::contains);
                });
    }
}