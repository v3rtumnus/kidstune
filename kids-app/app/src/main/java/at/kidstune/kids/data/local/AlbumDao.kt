package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.kidstune.kids.data.local.entities.LocalAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<LocalAlbum>)

    /** Albums ordered by newest first (for browsing grid). */
    @Query("SELECT * FROM local_album WHERE content_entry_id = :entryId ORDER BY release_date DESC")
    fun getByContentEntryId(entryId: String): Flow<List<LocalAlbum>>

    @Query("SELECT * FROM local_album WHERE content_entry_id = :entryId ORDER BY release_date DESC")
    suspend fun getByContentEntryIdOnce(entryId: String): List<LocalAlbum>

    @Query("SELECT * FROM local_album WHERE id = :id")
    suspend fun getById(id: String): LocalAlbum?

    @Query("DELETE FROM local_album WHERE content_entry_id = :entryId")
    suspend fun deleteByContentEntryId(entryId: String)
}
