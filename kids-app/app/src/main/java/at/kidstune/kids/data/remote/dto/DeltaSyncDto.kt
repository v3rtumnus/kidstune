package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Client-side mirror of the backend's DeltaSyncPayload record.
 * Reuses [SyncContentEntryDto] and [SyncFavoriteDto] from SyncDto.kt.
 */
@Serializable
data class DeltaSyncPayloadDto(
    /** New content entries to insert. */
    val added: List<SyncContentEntryDto> = emptyList(),
    /** Updated content entries – local albums/tracks must be replaced. */
    val updated: List<SyncContentEntryDto> = emptyList(),
    /** IDs of content entries to delete (cascades to albums and tracks). */
    val removed: List<String> = emptyList(),
    /** Favorites added on the server since last sync. */
    val favoritesAdded: List<SyncFavoriteDto> = emptyList(),
    /** Track URIs of favorites removed on the server since last sync. */
    val favoritesRemoved: List<String> = emptyList(),
    /** True when the family has a quick-approval PIN configured. */
    val pinAvailable: Boolean = false,
    /** ISO-8601 timestamp to store as the new lastSyncTimestamp. */
    val syncTimestamp: String = ""
)
