package at.kidstune.spotify;

import java.util.List;

/**
 * Result of {@link SpotifyImportService#getImportSuggestions}.
 *
 * Items are grouped by type:
 * <ul>
 *   <li>{@code artists} – artists from the child's Spotify listening history</li>
 *   <li>{@code playlists} – user playlists from the child's Spotify account</li>
 * </ul>
 */
public record ImportSuggestionsDto(
        List<Item> artists,
        List<Item> playlists
) {

    /**
     * A single importable Spotify item (artist or playlist).
     *
     * @param spotifyUri  the Spotify URI (e.g. {@code spotify:artist:xxx})
     * @param title       display name
     * @param imageUrl    cover / profile image (may be null)
     */
    public record Item(
            String spotifyUri,
            String title,
            String imageUrl
    ) {}
}
