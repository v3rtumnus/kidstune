package at.kidstune.spotify.dto;

/**
 * Enriched representation of a Spotify item (artist, album, or playlist)
 * returned by the KidsTune Spotify proxy API.
 */
public record SpotifyItemDto(
    String id,
    String title,
    String imageUrl,
    String spotifyUri,
    String artistName
) {}
