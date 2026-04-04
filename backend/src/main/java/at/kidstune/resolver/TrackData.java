package at.kidstune.resolver;

/**
 * Spotify track data returned by {@link ContentResolverSpotifyClient}.
 * Used internally by {@link ContentResolver} – not a JPA entity.
 */
record TrackData(
        String uri,
        String title,
        String artistName,
        long durationMs,
        int trackNumber,
        int discNumber,
        String albumUri,
        String albumTitle,
        String albumImageUrl
) {}
