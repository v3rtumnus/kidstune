package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Persists the last-known playback position per profile.
 * Used by the Spotify App Remote SDK to resume listening at the correct
 * track and offset when the child re-opens the app.
 */
@Entity(tableName = "local_playback_position")
data class LocalPlaybackPosition(
    /** One row per profile – profile_id is the primary key. */
    @PrimaryKey
    @ColumnInfo(name = "profile_id")
    val profileId: String,

    /** Spotify context URI (album or playlist the child was listening to). */
    @ColumnInfo(name = "context_uri")
    val contextUri: String,

    /** Spotify track URI that was playing. */
    @ColumnInfo(name = "track_uri")
    val trackUri: String,

    /** 0-based index within the context (used with Spotify App Remote skipToIndex). */
    @ColumnInfo(name = "track_index")
    val trackIndex: Int,

    /** Playback position in milliseconds within the track. */
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,

    /** When this row was last written. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
