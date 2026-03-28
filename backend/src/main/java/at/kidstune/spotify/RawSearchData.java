package at.kidstune.spotify;

import java.util.List;

/**
 * Raw search results from SpotifyWebApiClient, before explicit-content filtering.
 * Items include the {@code explicit} flag so SpotifySearchService can filter them.
 */
record RawSearchData(
    List<RawSpotifyItem> artists,
    List<RawSpotifyItem> albums,
    List<RawSpotifyItem> playlists
) {}
