package at.kidstune.resolver;

import java.util.List;

/**
 * Spotify album data returned by {@link ContentResolverSpotifyClient}.
 * Used internally by {@link ContentResolver} – not a JPA entity.
 */
record AlbumData(
        String id,
        String uri,
        String title,
        String imageUrl,
        String releaseDate,
        int totalTracks,
        List<String> genres,
        String artistName
) {}
