package at.kidstune.content;

import java.util.List;

/**
 * Carries the Spotify metadata needed by {@link ContentTypeClassifier}.
 * Populated by the caller (parent app sends this when adding content).
 * Null-safe: any field may be null/empty and the classifier handles it gracefully.
 */
public record SpotifyItemInfo(
        /** Spotify item type: "track", "album", "artist", "audiobook", "playlist". */
        String type,
        /** Genre tags from Spotify (artist or album genres). May be null or empty. */
        List<String> genres,
        /** Album name used for German audiobook naming-pattern detection. May be null. */
        String albumName,
        /** Total number of tracks on the album (0 if unknown). */
        int totalTracks,
        /** Average track duration in milliseconds (0 if unknown). */
        long averageTrackDurationMs
) {}