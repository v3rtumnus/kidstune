package at.kidstune.spotify;

/**
 * Intermediate representation of a Spotify item before explicit-content filtering.
 * Only used within the spotify package.
 */
record RawSpotifyItem(
    String id,
    String title,
    String imageUrl,
    String spotifyUri,
    String artistName,
    boolean explicit
) {}
