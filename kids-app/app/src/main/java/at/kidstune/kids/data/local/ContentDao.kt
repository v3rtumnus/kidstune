package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LocalContentEntry>)

    @Query("SELECT * FROM local_content_entry WHERE profile_id = :profileId ORDER BY title ASC")
    fun getAll(profileId: String): Flow<List<LocalContentEntry>>

    @Query("SELECT * FROM local_content_entry WHERE profile_id = :profileId AND content_type = :type ORDER BY title ASC")
    fun getByType(profileId: String, type: ContentType): Flow<List<LocalContentEntry>>

    @Query("SELECT * FROM local_content_entry WHERE id = :id")
    suspend fun getById(id: String): LocalContentEntry?

    @Query("DELETE FROM local_content_entry WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_content_entry WHERE profile_id = :profileId")
    suspend fun deleteAll(profileId: String)

    @Query("SELECT COUNT(*) FROM local_content_entry WHERE profile_id = :profileId AND content_type = :type")
    suspend fun countByType(profileId: String, type: ContentType): Int

    /** Reactive count of all entries for a profile. Emits a new value whenever the table changes. */
    @Query("SELECT COUNT(*) FROM local_content_entry WHERE profile_id = :profileId")
    fun countAllFlow(profileId: String): Flow<Int>
}
