package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import java.time.Instant

/**
 * Top-level content entry – the tiles the child sees in the browsing grid.
 * One row per allowed content item (artist, album, playlist, or track).
 */
@Entity(tableName = "local_content_entry")
data class LocalContentEntry(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "spotify_uri")
    val spotifyUri: String,

    val scope: ContentScope,

    @ColumnInfo(name = "content_type")
    val contentType: ContentType,

    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "artist_name")
    val artistName: String?,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Instant = Instant.now()
)
