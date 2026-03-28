package at.kidstune.spotify.dto;

import java.util.List;

/** Grouped Spotify search results returned by GET /api/v1/spotify/search. */
public record SearchResultsResponse(
    List<SpotifyItemDto> artists,
    List<SpotifyItemDto> albums,
    List<SpotifyItemDto> playlists
) {}
