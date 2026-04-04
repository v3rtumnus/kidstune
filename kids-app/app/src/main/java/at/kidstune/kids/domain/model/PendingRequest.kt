package at.kidstune.kids.domain.model

import java.time.Instant

data class PendingRequest(
    val id: String,
    val tile: DiscoverTile,
    val status: RequestStatus,
    val requestedAt: Instant,
    val parentNote: String? = null
)
