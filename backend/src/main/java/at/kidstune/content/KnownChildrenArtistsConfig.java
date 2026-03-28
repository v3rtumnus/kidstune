package at.kidstune.content;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code known-artists.yml} configuration into a typed list.
 * Registered via {@code @EnableConfigurationProperties} in {@link at.kidstune.KidstuneApplication}.
 */
@ConfigurationProperties(prefix = "kidstune")
public class KnownChildrenArtistsConfig {

    private List<ArtistEntry> knownChildrenArtists = new ArrayList<>();

    public List<ArtistEntry> getKnownChildrenArtists()                          { return knownChildrenArtists; }
    public void setKnownChildrenArtists(List<ArtistEntry> knownChildrenArtists) { this.knownChildrenArtists = knownChildrenArtists; }

    public record ArtistEntry(String name, int minAge) {}
}