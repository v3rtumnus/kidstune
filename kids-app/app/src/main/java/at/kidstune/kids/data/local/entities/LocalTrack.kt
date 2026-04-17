package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single playable track. The [spotifyTrackUri] is what the Spotify App Remote SDK
 * needs to start playback – it is never fetched at runtime; it always comes from Room.
 */
@Entity(
    tableName = "local_track",
    foreignKeys = [
        ForeignKey(
            entity = LocalAlbum::class,
            parentColumns = ["id"],
            childColumns  = ["album_id"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("album_id")]
)
data class LocalTrack(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "album_id")
    val albumId: String,

    @ColumnInfo(name = "spotify_track_uri")
    val spotifyTrackUri: String,

    val title: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int,

    @ColumnInfo(name = "disc_number")
    val discNumber: Int,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    /** Position within a Spotify playlist (0-based). Null for non-playlist tracks. */
    @ColumnInfo(name = "playlist_position")
    val playlistPosition: Int? = null
)
