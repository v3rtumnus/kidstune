package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.kidstune.kids.domain.model.ContentType

/**
 * A resolved album belonging to a [LocalContentEntry].
 * One artist entry can have many albums; one album entry maps to itself.
 */
@Entity(
    tableName = "local_album",
    foreignKeys = [
        ForeignKey(
            entity = LocalContentEntry::class,
            parentColumns = ["id"],
            childColumns  = ["content_entry_id"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("content_entry_id")]
)
data class LocalAlbum(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "content_entry_id")
    val contentEntryId: String,

    @ColumnInfo(name = "spotify_album_uri")
    val spotifyAlbumUri: String,

    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "release_date")
    val releaseDate: String?,

    @ColumnInfo(name = "total_tracks")
    val totalTracks: Int,

    @ColumnInfo(name = "content_type")
    val contentType: ContentType
)
