package at.kidstune.content;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides lookup access to the known children's artists list loaded from
 * {@code known-artists.yml}. Used by the import heuristic (phase 6) and
 * available as an additional signal for other components.
 *
 * All name lookups are case-insensitive.
 */
@Service
public class KnownChildrenArtistsService {

    /** name (lowercase) → min_age */
    private final Map<String, Integer> artistMinAgeMap;

    public KnownChildrenArtistsService(KnownChildrenArtistsConfig config) {
        this.artistMinAgeMap = config.getKnownChildrenArtists().stream()
                .collect(Collectors.toMap(
                        e -> e.name().toLowerCase(),
                        KnownChildrenArtistsConfig.ArtistEntry::minAge,
                        (a, b) -> a   // keep first on duplicate name
                ));
    }

    /**
     * Returns true if the given artist name (case-insensitive) is in the
     * known children's artists list.
     */
    public boolean isKnownChildrenArtist(String name) {
        if (name == null) return false;
        return artistMinAgeMap.containsKey(name.toLowerCase());
    }

    /**
     * Returns the minimum recommended age for the given artist, or
     * {@link Optional#empty()} if the artist is not in the list.
     */
    public Optional<Integer> getMinAge(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(artistMinAgeMap.get(name.toLowerCase()));
    }
}