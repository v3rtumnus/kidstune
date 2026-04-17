package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.kidstune.kids.data.local.entities.LocalTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<LocalTrack>)

    /** Tracks ordered by disc and track number for chapter-based listening. */
    @Query("SELECT * FROM local_track WHERE album_id = :albumId ORDER BY disc_number ASC, track_number ASC")
    fun getByAlbumId(albumId: String): Flow<List<LocalTrack>>

    @Query("SELECT * FROM local_track WHERE album_id = :albumId ORDER BY disc_number ASC, track_number ASC")
    suspend fun getByAlbumIdOnce(albumId: String): List<LocalTrack>

    /**
     * Returns the 0-based index of a track within its album, ordered by disc/track number.
     * Used for Spotify App Remote SDK's skipToIndex when resuming a chapter.
     */
    @Query("""
        SELECT COUNT(*) FROM local_track t2
        WHERE t2.album_id = (SELECT album_id FROM local_track WHERE spotify_track_uri = :trackUri)
          AND (t2.disc_number < (SELECT disc_number FROM local_track WHERE spotify_track_uri = :trackUri)
               OR (t2.disc_number = (SELECT disc_number FROM local_track WHERE spotify_track_uri = :trackUri)
                   AND t2.track_number < (SELECT track_number FROM local_track WHERE spotify_track_uri = :trackUri)))
    """)
    suspend fun getIndexByUri(trackUri: String): Int

    /** Fetch a single track by its Spotify URI – used by [PlaybackController] to look up Room metadata. */
    @Query("SELECT * FROM local_track WHERE spotify_track_uri = :trackUri LIMIT 1")
    suspend fun getByUri(trackUri: String): LocalTrack?

    /** All tracks for a playlist content entry, ordered by playlist position. */
    @Query("""
        SELECT t.* FROM local_track t
        INNER JOIN local_album a ON t.album_id = a.id
        WHERE a.content_entry_id = :contentEntryId
        ORDER BY t.playlist_position ASC
    """)
    fun getByContentEntryId(contentEntryId: String): Flow<List<LocalTrack>>

    @Query("DELETE FROM local_track WHERE album_id = :albumId")
    suspend fun deleteByAlbumId(albumId: String)
}
