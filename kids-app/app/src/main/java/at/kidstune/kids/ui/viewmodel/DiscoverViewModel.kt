package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import at.kidstune.kids.data.mock.MockDiscoverData
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

data class DiscoverState(
    val query: String = "",
    val suggestions: List<DiscoverTile> = MockDiscoverData.mockSuggestions,
    val searchResults: List<DiscoverTile> = emptyList(),
    val pendingRequests: List<PendingRequest> = emptyList(),
    val requestedUris: Set<String> = emptySet()
)

sealed interface DiscoverIntent {
    data class UpdateQuery(val q: String) : DiscoverIntent
    data object SubmitSearch : DiscoverIntent
    data class RequestContent(val tile: DiscoverTile) : DiscoverIntent
    data class DismissRejected(val id: String) : DiscoverIntent
}

class DiscoverViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        DiscoverState(
            pendingRequests = MockDiscoverData.mockPendingRequests,
            requestedUris   = MockDiscoverData.mockPendingRequests
                .map { it.tile.spotifyUri }
                .toSet()
        )
    )
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    fun onIntent(intent: DiscoverIntent) {
        when (intent) {
            is DiscoverIntent.UpdateQuery -> {
                val q = intent.q
                _state.update {
                    it.copy(
                        query         = q,
                        searchResults = if (q.isNotEmpty()) MockDiscoverData.mockSearchResults
                                        else emptyList()
                    )
                }
            }
            is DiscoverIntent.SubmitSearch -> Unit // already handled by UpdateQuery

            is DiscoverIntent.RequestContent -> {
                val tile = intent.tile
                val newRequest = PendingRequest(
                    id          = "req-${System.currentTimeMillis()}",
                    tile        = tile,
                    status      = RequestStatus.PENDING,
                    requestedAt = Instant.now()
                )
                _state.update {
                    it.copy(
                        pendingRequests = it.pendingRequests + newRequest,
                        requestedUris   = it.requestedUris + tile.spotifyUri
                    )
                }
            }

            is DiscoverIntent.DismissRejected -> {
                _state.update {
                    it.copy(
                        pendingRequests = it.pendingRequests.filter { r -> r.id != intent.id }
                    )
                }
            }
        }
    }
}

/**
 * Returns a human-readable time label for a pending request.
 * Exposed as internal so it can be unit-tested.
 *
 * @param requestedAt  when the request was submitted
 * @param now          injectable "now" for testing (defaults to Instant.now())
 */
internal fun pendingRequestTimeLabel(
    requestedAt: Instant,
    now: Instant = Instant.now()
): String {
    val elapsed = Duration.between(requestedAt, now)
    return when {
        elapsed.toMinutes() < 60       -> "Mama/Papa schauen sich das an"
        elapsed.toHours()   < 24       -> "Gestern gewünscht"
        else                           -> "Vor ein paar Tagen gewünscht"
    }
}
