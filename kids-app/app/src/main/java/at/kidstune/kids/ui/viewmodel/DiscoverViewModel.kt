package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.repository.DiscoverRepository
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class DiscoverState(
    val query: String = "",
    val suggestions: List<DiscoverTile> = emptyList(),
    val searchResults: List<DiscoverTile> = emptyList(),
    val pendingRequests: List<PendingRequest> = emptyList(),
    val requestedUris: Set<String> = emptySet(),
    val isLoadingSuggestions: Boolean = false,
    val isSearching: Boolean = false,
    val rateLimitMessage: String? = null,
    val showCelebration: Boolean = false,
    val newlyApprovedUris: Set<String> = emptySet(),
)

sealed interface DiscoverIntent {
    data class UpdateQuery(val q: String) : DiscoverIntent
    data object SubmitSearch : DiscoverIntent
    data class RequestContent(val tile: DiscoverTile) : DiscoverIntent
    data class DismissRejected(val id: String) : DiscoverIntent
    data object DismissCelebration : DiscoverIntent
    data object StartVoiceInput : DiscoverIntent
    data class VoiceInputResult(val text: String) : DiscoverIntent
}

/** Minimum gap between two consecutive search calls (rate-limiting). */
private val SEARCH_DEBOUNCE_MS = 5_000L

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discoverRepository: DiscoverRepository,
    private val contentDao: ContentDao,
    private val prefs: ProfilePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    internal var lastSearchAt: Instant = Instant.EPOCH
    private var searchJob: Job? = null
    private var previousContentCount: Int? = null

    /** URIs that were PENDING when the screen was last refreshed. */
    private val pendingUrisSnapshot = mutableSetOf<String>()

    init {
        val profileId = prefs.boundProfileId
        if (profileId != null) {
            loadSuggestions(profileId)
            observeRequests(profileId)
            observeContentCount(profileId)
        }
    }

    fun onIntent(intent: DiscoverIntent) {
        when (intent) {
            is DiscoverIntent.UpdateQuery    -> handleQueryUpdate(intent.q)
            is DiscoverIntent.SubmitSearch   -> Unit  // handled by UpdateQuery
            is DiscoverIntent.RequestContent -> handleRequestContent(intent.tile)
            is DiscoverIntent.DismissRejected -> handleDismissRejected(intent.id)
            is DiscoverIntent.DismissCelebration -> _state.update { it.copy(showCelebration = false) }
            is DiscoverIntent.StartVoiceInput -> Unit  // UI layer launches SpeechRecognizer
            is DiscoverIntent.VoiceInputResult -> handleQueryUpdate(intent.text)
        }
    }

    // ── Private handlers ──────────────────────────────────────────────────────

    private fun handleQueryUpdate(q: String) {
        _state.update { it.copy(query = q, rateLimitMessage = null) }

        if (q.isEmpty()) {
            searchJob?.cancel()
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        // Rate limiting: block if a search ran within SEARCH_DEBOUNCE_MS
        val elapsed = Duration.between(lastSearchAt, Instant.now()).toMillis()
        if (elapsed < SEARCH_DEBOUNCE_MS) {
            _state.update {
                it.copy(rateLimitMessage = "Bitte warte kurz bevor du erneut suchst!")
            }
            return
        }

        // Cancel any pending search before starting a new one
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(q)
        }
    }

    private fun performSearch(q: String) {
        val profileId = prefs.boundProfileId ?: return
        lastSearchAt = Instant.now()
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = discoverRepository.search(profileId, q)
            _state.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    private fun handleRequestContent(tile: DiscoverTile) {
        val profileId = prefs.boundProfileId ?: return
        val pendingCount = _state.value.pendingRequests.count { it.status == RequestStatus.PENDING }
        if (pendingCount >= 3) return  // UI already disables the button; guard here too

        // Optimistically mark as requested
        _state.update {
            it.copy(requestedUris = it.requestedUris + tile.spotifyUri)
        }

        viewModelScope.launch {
            discoverRepository.requestContent(profileId, tile)
            // Room Flow in observeRequests() will pick up the change automatically
        }
    }

    private fun handleDismissRejected(id: String) {
        viewModelScope.launch {
            discoverRepository.getVisibleRequests(prefs.boundProfileId ?: return@launch)
            // Just remove from local Room display
            _state.update {
                it.copy(pendingRequests = it.pendingRequests.filter { r -> r.id != id })
            }
        }
    }

    private fun loadSuggestions(profileId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSuggestions = true) }
            val suggestions = discoverRepository.fetchSuggestions(profileId)
            _state.update { it.copy(suggestions = suggestions, isLoadingSuggestions = false) }
        }
    }

    private fun observeRequests(profileId: String) {
        viewModelScope.launch {
            discoverRepository.getVisibleRequests(profileId).collect { requests ->
                // Track pending URIs for celebration detection
                pendingUrisSnapshot.addAll(
                    requests.filter { it.status == RequestStatus.PENDING }
                            .map { it.tile.spotifyUri }
                )

                val requestedUris = requests
                    .filter { it.status == RequestStatus.PENDING }
                    .map { it.tile.spotifyUri }
                    .toSet()

                _state.update {
                    it.copy(
                        pendingRequests = requests,
                        requestedUris   = requestedUris,
                    )
                }
            }
        }
    }

    /**
     * Watches the Room content count. When it increases and we had pending requests,
     * some of them were approved → trigger celebration.
     */
    private fun observeContentCount(profileId: String) {
        viewModelScope.launch {
            contentDao.countAllFlow(profileId)
                .distinctUntilChanged()
                .collect { count ->
                    val prev = previousContentCount
                    if (prev != null && count > prev && pendingUrisSnapshot.isNotEmpty()) {
                        // New content arrived; at least one pending request was approved
                        _state.update { it.copy(showCelebration = true) }
                        pendingUrisSnapshot.clear()
                    }
                    previousContentCount = count
                }
        }
    }
}

// ── Time-label helper (internal so tests can call it directly) ─────────────────

/**
 * Returns a human-readable time label for a pending request.
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
        elapsed.toMinutes() < 60 -> "Mama/Papa schauen sich das an"
        elapsed.toHours()   < 24 -> "Gestern gewünscht"
        else                     -> "Vor ein paar Tagen gewünscht"
    }
}
