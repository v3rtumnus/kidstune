package at.kidstune.kids.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import at.kidstune.kids.data.local.entities.LocalPlaybackPosition

@Dao
interface PlaybackPositionDao {

    /** Insert or replace the playback position for a profile. */
    @Upsert
    suspend fun upsert(position: LocalPlaybackPosition)

    /** Returns null if no position has ever been saved for this profile. */
    @Query("SELECT * FROM local_playback_position WHERE profile_id = :profileId")
    suspend fun getByProfileId(profileId: String): LocalPlaybackPosition?
}
