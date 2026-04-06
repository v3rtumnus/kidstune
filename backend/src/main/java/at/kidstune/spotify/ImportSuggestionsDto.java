package at.kidstune.spotify;

import java.util.List;

/**
 * Result of {@link SpotifyImportService#getImportSuggestions}.
 *
 * Items are grouped by confidence level:
 * <ul>
 *   <li>{@code detectedChildrenContent} – artists matched against known-artists.yml that are
 *       age-appropriate for the profile (pre-selected = true)</li>
 *   <li>{@code playlists} – user playlists from the child's Spotify account</li>
 *   <li>{@code otherArtists} – artists from listening history not matched as children's content</li>
 * </ul>
 */
public record ImportSuggestionsDto(
        List<Item> detectedChildrenContent,
        List<Item> playlists,
        List<Item> otherArtists
) {

    /**
     * A single importable Spotify item (artist or playlist).
     *
     * @param spotifyUri  the Spotify URI (e.g. {@code spotify:artist:xxx})
     * @param title       display name
     * @param imageUrl    cover / profile image (may be null)
     * @param preSelected whether the UI should pre-tick this item for the user
     */
    public record Item(
            String spotifyUri,
            String title,
            String imageUrl,
            boolean preSelected
    ) {}
}
