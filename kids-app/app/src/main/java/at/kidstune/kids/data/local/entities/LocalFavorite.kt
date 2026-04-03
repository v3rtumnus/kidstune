package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A favorited track for a specific profile.
 * [synced] = false means it is queued for upload to the backend on the next sync.
 */
@Entity(tableName = "local_favorite")
data class LocalFavorite(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "spotify_track_uri")
    val spotifyTrackUri: String,

    val title: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String?,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "added_at")
    val addedAt: Instant = Instant.now(),

    /** false = queued for upload on next sync; true = confirmed by backend */
    val synced: Boolean = false
)
