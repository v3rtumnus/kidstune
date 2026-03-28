package at.kidstune.content;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnownChildrenArtistsServiceTest {

    KnownChildrenArtistsService service;

    @BeforeEach
    void setUp() {
        KnownChildrenArtistsConfig config = new KnownChildrenArtistsConfig();
        config.setKnownChildrenArtists(List.of(
                new KnownChildrenArtistsConfig.ArtistEntry("Bibi & Tina", 3),
                new KnownChildrenArtistsConfig.ArtistEntry("Peppa Pig", 2),
                new KnownChildrenArtistsConfig.ArtistEntry("Die drei ??? Kids", 6),
                new KnownChildrenArtistsConfig.ArtistEntry("Rolf Zuckowski", 0)
        ));
        service = new KnownChildrenArtistsService(config);
    }

    // ── isKnownChildrenArtist ─────────────────────────────────────────────────

    @Test void known_artist_returns_true() {
        assertThat(service.isKnownChildrenArtist("Bibi & Tina")).isTrue();
    }

    @Test void known_artist_case_insensitive_lowercase() {
        assertThat(service.isKnownChildrenArtist("bibi & tina")).isTrue();
    }

    @Test void known_artist_case_insensitive_uppercase() {
        assertThat(service.isKnownChildrenArtist("PEPPA PIG")).isTrue();
    }

    @Test void unknown_artist_returns_false() {
        assertThat(service.isKnownChildrenArtist("Unknown Band")).isFalse();
    }

    @Test void null_name_returns_false() {
        assertThat(service.isKnownChildrenArtist(null)).isFalse();
    }

    @Test void peppa_pig_returns_true() {
        assertThat(service.isKnownChildrenArtist("Peppa Pig")).isTrue();
    }

    @Test void artist_min_age_zero_is_known() {
        assertThat(service.isKnownChildrenArtist("Rolf Zuckowski")).isTrue();
    }

    // ── getMinAge ─────────────────────────────────────────────────────────────

    @Test void getMinAge_bibi_und_tina_returns_3() {
        assertThat(service.getMinAge("Bibi & Tina")).contains(3);
    }

    @Test void getMinAge_peppa_pig_returns_2() {
        assertThat(service.getMinAge("Peppa Pig")).contains(2);
    }

    @Test void getMinAge_drei_fragezeichen_kids_returns_6() {
        assertThat(service.getMinAge("Die drei ??? Kids")).contains(6);
    }

    @Test void getMinAge_rolf_zuckowski_returns_0() {
        assertThat(service.getMinAge("Rolf Zuckowski")).contains(0);
    }

    @Test void getMinAge_case_insensitive() {
        assertThat(service.getMinAge("bibi & tina")).contains(3);
    }

    @Test void getMinAge_unknown_returns_empty() {
        assertThat(service.getMinAge("Unknown Artist")).isEmpty();
    }

    @Test void getMinAge_null_returns_empty() {
        assertThat(service.getMinAge(null)).isEmpty();
    }
}