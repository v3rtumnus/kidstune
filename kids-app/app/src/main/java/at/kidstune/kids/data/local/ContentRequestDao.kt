package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.kidstune.kids.data.local.entities.LocalContentRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: LocalContentRequest)

    @Update
    suspend fun update(request: LocalContentRequest)

    /** All visible requests for a profile (PENDING_UPLOAD + PENDING + REJECTED; not APPROVED/EXPIRED). */
    @Query("""
        SELECT * FROM local_content_request
        WHERE profile_id = :profileId
          AND status IN ('PENDING_UPLOAD', 'PENDING', 'REJECTED')
        ORDER BY requested_at DESC
    """)
    fun getVisible(profileId: String): Flow<List<LocalContentRequest>>

    /** Items waiting to be uploaded to the backend. */
    @Query("SELECT * FROM local_content_request WHERE profile_id = :profileId AND status = 'PENDING_UPLOAD'")
    suspend fun getPendingUpload(profileId: String): List<LocalContentRequest>

    /** Total count of PENDING (not PENDING_UPLOAD) requests for the profile. */
    @Query("SELECT COUNT(*) FROM local_content_request WHERE profile_id = :profileId AND status = 'PENDING'")
    suspend fun countPending(profileId: String): Int

    @Query("DELETE FROM local_content_request WHERE profile_id = :profileId AND status IN ('APPROVED', 'EXPIRED')")
    suspend fun deleteApprovedAndExpired(profileId: String)

    @Query("DELETE FROM local_content_request WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE local_content_request SET status = :status, server_id = :serverId WHERE id = :id")
    suspend fun updateStatusAndServerId(id: String, status: String, serverId: String?)

    @Query("UPDATE local_content_request SET status = :status, parent_note = :parentNote WHERE server_id = :serverId")
    suspend fun updateByServerId(serverId: String, status: String, parentNote: String?)
}
