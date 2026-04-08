package at.kidstune.kids.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A content request created by the child, stored locally.
 *
 * [status] lifecycle:
 *  - "PENDING_UPLOAD" – created offline; not yet sent to backend
 *  - "PENDING"        – sent to backend; awaiting parent decision
 *  - "APPROVED"       – parent approved (content will appear after next sync)
 *  - "REJECTED"       – parent rejected (shown with [parentNote] for 24 h)
 *  - "EXPIRED"        – backend expired the request (> 7 days)
 *
 * [serverId] is null for PENDING_UPLOAD items; set after the backend confirms
 * the request was created.
 */
@Entity(tableName = "local_content_request")
data class LocalContentRequest(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "server_id")
    val serverId: String? = null,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "spotify_uri")
    val spotifyUri: String,

    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "artist_name")
    val artistName: String?,

    @ColumnInfo(name = "content_type")
    val contentType: String,

    /** One of: PENDING_UPLOAD, PENDING, APPROVED, REJECTED, EXPIRED */
    val status: String,

    @ColumnInfo(name = "requested_at")
    val requestedAt: Instant,

    @ColumnInfo(name = "parent_note")
    val parentNote: String? = null,
)
