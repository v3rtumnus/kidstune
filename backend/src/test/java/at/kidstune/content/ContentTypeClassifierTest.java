package at.kidstune.content;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeClassifierTest {

    static final long TWO_MIN    = 2 * 60_000L;
    static final long THREE_MIN  = 3 * 60_000L;
    static final long FIVE_MIN   = 5 * 60_000L;
    static final long FIVE_MIN_1 = FIVE_MIN + 1;
    static final long EIGHT_MIN  = 8 * 60_000L;

    ContentTypeClassifier classifier;

    @BeforeEach
    void setUp() { classifier = new ContentTypeClassifier(); }

    // ── Null / empty input ────────────────────────────────────────────────────

    @Test void null_item_returns_music() {
        assertThat(classifier.classify(null)).isEqualTo(ContentType.MUSIC);
    }

    @Test void no_signals_returns_music() {
        assertThat(classify("track", List.of(), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    // ── Strong: Spotify audiobook type ───────────────────────────────────────

    @Test void type_audiobook_lowercase_returns_audiobook() {
        assertThat(classify("audiobook", List.of(), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void type_AUDIOBOOK_uppercase_returns_audiobook() {
        assertThat(classify("AUDIOBOOK", List.of(), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void type_Audiobook_mixed_case_returns_audiobook() {
        assertThat(classify("Audiobook", List.of(), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void type_audiobook_beats_children_music_genre() {
        assertThat(classify("audiobook", List.of("kindermusik"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Strong: hörspiel genre ────────────────────────────────────────────────

    @Test void genre_hoerspiel_lowercase_returns_audiobook() {
        assertThat(classify("track", List.of("hörspiel"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_Hoerspiel_capital_H_returns_audiobook() {
        assertThat(classify("track", List.of("Hörspiel"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_HOERSPIEL_uppercase_returns_audiobook() {
        assertThat(classify("track", List.of("HÖRSPIEL"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_hoerspiel_beats_children_music_genre() {
        assertThat(classify("track", List.of("hörspiel", "kindermusik"), null, 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_hoerspiel_with_few_short_tracks_still_returns_audiobook() {
        assertThat(classify("album", List.of("hörspiel"), null, 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Strong: audiobook genre ───────────────────────────────────────────────

    @Test void genre_audiobook_returns_audiobook() {
        assertThat(classify("album", List.of("audiobook"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_AUDIOBOOK_uppercase_returns_audiobook() {
        assertThat(classify("album", List.of("AUDIOBOOK"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_audiobook_in_multi_genre_list_returns_audiobook() {
        assertThat(classify("album", List.of("rock", "audiobook", "pop"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Strong: spoken word genre ─────────────────────────────────────────────

    @Test void genre_spoken_word_returns_audiobook() {
        assertThat(classify("track", List.of("spoken word"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_SPOKEN_WORD_uppercase_returns_audiobook() {
        assertThat(classify("track", List.of("SPOKEN WORD"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_spoken_word_in_multi_genre_list_returns_audiobook() {
        assertThat(classify("track", List.of("german spoken word", "children"), null, 1, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void genre_spoken_word_with_short_tracks_returns_audiobook() {
        assertThat(classify("album", List.of("spoken word"), null, 3, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    // ── Medium: track count + duration ───────────────────────────────────────

    @Test void tracks_25_avg_8min_returns_audiobook() {
        assertThat(classify("album", List.of(), null, 25, EIGHT_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void tracks_21_avg_6min_returns_audiobook() {
        assertThat(classify("album", List.of(), null, 21, 6 * 60_000L)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void tracks_exactly_20_avg_8min_returns_music_not_over_threshold() {
        assertThat(classify("album", List.of(), null, 20, EIGHT_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void tracks_25_avg_exactly_5min_returns_music_not_over_threshold() {
        assertThat(classify("album", List.of(), null, 25, FIVE_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void tracks_25_avg_5min_plus_1ms_returns_audiobook() {
        assertThat(classify("album", List.of(), null, 25, FIVE_MIN_1)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void tracks_30_avg_2min_returns_music_short_tracks() {
        assertThat(classify("album", List.of(), null, 30, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void tracks_1000_avg_10min_returns_audiobook() {
        assertThat(classify("album", List.of(), null, 1000, 10 * 60_000L)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void tracks_0_returns_music() {
        assertThat(classify("album", List.of(), null, 0, 0L)).isEqualTo(ContentType.MUSIC);
    }

    // ── Medium: album name pattern ────────────────────────────────────────────

    @Test void album_Folge_1_returns_audiobook() {
        assertThat(classify("album", List.of(), "Folge 1 - Das große Abenteuer", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Folge_42_returns_audiobook() {
        assertThat(classify("album", List.of(), "Folge 42", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Episode_1_returns_audiobook() {
        assertThat(classify("album", List.of(), "Episode 1", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Episode_99_subtitle_returns_audiobook() {
        assertThat(classify("album", List.of(), "Episode 99 - The Final Chapter", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Teil_1_returns_audiobook() {
        assertThat(classify("album", List.of(), "Teil 1", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Teil_15_returns_audiobook() {
        assertThat(classify("album", List.of(), "Teil 15", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Kapitel_3_returns_audiobook() {
        assertThat(classify("album", List.of(), "Kapitel 3", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Kapitel_100_returns_audiobook() {
        assertThat(classify("album", List.of(), "Kapitel 100", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_folge_lowercase_returns_audiobook() {
        assertThat(classify("album", List.of(), "folge 1", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_kapitel_lowercase_returns_audiobook() {
        assertThat(classify("album", List.of(), "kapitel 5 - der verlorene ring", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_embedded_Folge_number_returns_audiobook() {
        assertThat(classify("album", List.of(), "Die Drei Fragezeichen - Folge 1", 5, TWO_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void album_Folge_no_number_returns_music() {
        assertThat(classify("album", List.of(), "Die beste Folge aller Zeiten", 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void album_Kapitel_no_number_returns_music() {
        assertThat(classify("album", List.of(), "Das letzte Kapitel", 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void album_Episode_letter_not_digit_returns_music() {
        assertThat(classify("album", List.of(), "Episode A - The Beginning", 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void album_null_returns_music() {
        assertThat(classify("album", List.of(), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void album_generic_volume_number_returns_music() {
        assertThat(classify("album", List.of(), "Best of Music Vol. 3", 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    // ── Children's music genres ───────────────────────────────────────────────

    @Test void genre_kindermusik_returns_music() {
        assertThat(classify("album", List.of("kindermusik"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void genre_KINDERMUSIK_uppercase_returns_music() {
        assertThat(classify("album", List.of("KINDERMUSIK"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void genre_kinderlieder_returns_music() {
        assertThat(classify("album", List.of("kinderlieder"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void genre_childrens_music_returns_music() {
        assertThat(classify("album", List.of("children's music"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void genre_CHILDRENS_MUSIC_uppercase_returns_music() {
        assertThat(classify("album", List.of("CHILDREN'S MUSIC"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void genre_kindermusik_in_multi_genre_list_returns_music() {
        assertThat(classify("album", List.of("pop", "kindermusik"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test void null_genres_returns_music() {
        assertThat(classify("album", null, null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void empty_genre_list_returns_music() {
        assertThat(classify("album", List.of(), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void medium_signal_fires_before_children_music_genre() {
        // 25 tracks + 8 min avg should return AUDIOBOOK even if kindermusik is present,
        // because medium signal is checked before the children's music genre check.
        assertThat(classify("album", List.of("kindermusik"), null, 25, EIGHT_MIN)).isEqualTo(ContentType.AUDIOBOOK);
    }

    @Test void multiple_genres_none_match_returns_music() {
        assertThat(classify("album", List.of("pop", "rock", "indie"), null, 5, TWO_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void ten_tracks_three_min_no_genre_returns_music_by_default() {
        assertThat(classify("album", List.of(), null, 10, THREE_MIN)).isEqualTo(ContentType.MUSIC);
    }

    @Test void type_track_no_signals_returns_music() {
        assertThat(classify("track", List.of(), null, 0, 0L)).isEqualTo(ContentType.MUSIC);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContentType classify(String type, List<String> genres, String albumName,
                                  int totalTracks, long avgDurationMs) {
        return classifier.classify(new SpotifyItemInfo(type, genres, albumName, totalTracks, avgDurationMs));
    }
}