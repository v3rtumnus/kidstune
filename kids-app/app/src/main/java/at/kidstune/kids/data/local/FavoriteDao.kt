package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.kidstune.kids.data.local.entities.LocalFavorite
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: LocalFavorite)

    @Query("DELETE FROM local_favorite WHERE profile_id = :profileId AND spotify_track_uri = :trackUri")
    suspend fun deleteByUri(profileId: String, trackUri: String)

    @Query("SELECT * FROM local_favorite WHERE profile_id = :profileId ORDER BY added_at DESC")
    fun getAll(profileId: String): Flow<List<LocalFavorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM local_favorite WHERE profile_id = :profileId AND spotify_track_uri = :trackUri)")
    fun existsByTrackUri(profileId: String, trackUri: String): Flow<Boolean>

    @Query("SELECT * FROM local_favorite WHERE profile_id = :profileId AND synced = 0")
    suspend fun getUnsynced(profileId: String): List<LocalFavorite>

    @Query("UPDATE local_favorite SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Deletes all synced favorites for a profile (used before re-inserting from a full sync). */
    @Query("DELETE FROM local_favorite WHERE profile_id = :profileId AND synced = 1")
    suspend fun deleteAllSynced(profileId: String)
}
