package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.PinApproveException
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
    /** URIs approved since the last sync — used to highlight tiles in the Discover grid. */
    val newlyApprovedUris: Set<String> = emptySet(),
    /** True when the most recent search call failed due to a network or server error. */
    val searchError: Boolean = false,
    /** True when suggestions could not be loaded (network unavailable). */
    val suggestionsError: Boolean = false,

    // ── PIN quick-approval flow ───────────────────────────────────────────────

    /** True when the family has configured a quick-approval PIN. */
    val pinAvailable: Boolean = false,
    /** The tile currently being acted on in the approval / PIN flow. */
    val pinFlowTile: DiscoverTile? = null,
    /** Show the "Anfragen" vs. "Elternteil fragen" choice overlay. */
    val showApprovalChoice: Boolean = false,
    /** Show the numeric PIN entry overlay. */
    val showPinPad: Boolean = false,
    /** Digits entered so far (max 4). */
    val pinDigits: String = "",
    /** True after a wrong PIN — cleared automatically on next digit. */
    val pinError: Boolean = false,
    /** True after the backend returns 429 (too many attempts). */
    val pinTooManyAttempts: Boolean = false,
    /** True while waiting for the backend to respond to a PIN attempt. */
    val pinSubmitting: Boolean = false,
)

sealed interface DiscoverIntent {
    data class UpdateQuery(val q: String) : DiscoverIntent
    data class RequestContent(val tile: DiscoverTile) : DiscoverIntent
    data class DismissRejected(val id: String) : DiscoverIntent
    data object DismissCelebration : DiscoverIntent
    data object StartVoiceInput : DiscoverIntent
    data class VoiceInputResult(val text: String) : DiscoverIntent

    // ── PIN approval intents ──────────────────────────────────────────────────
    /** Open the choice overlay for [tile] (sent when pinAvailable = true). */
    data class OpenApprovalChoice(val tile: DiscoverTile) : DiscoverIntent
    /** From the choice overlay: proceed with a normal async request. */
    data object SendRequestFromChoice : DiscoverIntent
    /** From the choice overlay: open the PIN pad. */
    data object OpenPinPad : DiscoverIntent
    /** A numpad digit was tapped. Auto-submits when the 4th digit is entered. */
    data class PinDigit(val digit: Char) : DiscoverIntent
    /** Delete the last digit. */
    data object PinBackspace : DiscoverIntent
    /** User cancelled the PIN flow (back button or explicit cancel). */
    data object PinCancel : DiscoverIntent
    /** 30-second inactivity timeout on the PIN pad. */
    data object PinTimeout : DiscoverIntent
    /** After 3 failed attempts: fall back to the normal request flow. */
    data object RetryAsRequest : DiscoverIntent
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

    /**
     * URIs that are currently PENDING — replaced on every [observeRequests] emission so
     * stale entries do not accumulate and trigger spurious celebrations.
     */
    private val pendingUrisSnapshot = mutableSetOf<String>()

    init {
        val profileId = prefs.boundProfileId
        _state.update { it.copy(pinAvailable = prefs.pinAvailable) }
        if (profileId != null) {
            loadSuggestions(profileId)
            observeRequests(profileId)
            observeContentCount(profileId)
        }
    }

    fun onIntent(intent: DiscoverIntent) {
        when (intent) {
            is DiscoverIntent.UpdateQuery        -> handleQueryUpdate(intent.q)
            is DiscoverIntent.RequestContent     -> handleRequestContent(intent.tile)
            is DiscoverIntent.DismissRejected    -> handleDismissRejected(intent.id)
            is DiscoverIntent.DismissCelebration -> _state.update { it.copy(showCelebration = false) }
            is DiscoverIntent.StartVoiceInput    -> Unit  // UI layer launches SpeechRecognizer
            is DiscoverIntent.VoiceInputResult   -> handleQueryUpdate(intent.text)

            is DiscoverIntent.OpenApprovalChoice -> _state.update { it.copy(
                pinFlowTile       = intent.tile,
                showApprovalChoice = true,
            )}
            is DiscoverIntent.SendRequestFromChoice -> {
                val tile = _state.value.pinFlowTile ?: return
                _state.update { it.copy(showApprovalChoice = false, pinFlowTile = null) }
                handleRequestContent(tile)
            }
            is DiscoverIntent.OpenPinPad -> _state.update { it.copy(
                showApprovalChoice  = false,
                showPinPad          = true,
                pinDigits           = "",
                pinError            = false,
                pinTooManyAttempts  = false,
            )}
            is DiscoverIntent.PinDigit    -> handlePinDigit(intent.digit)
            is DiscoverIntent.PinBackspace -> _state.update { it.copy(
                pinDigits = _state.value.pinDigits.dropLast(1),
                pinError  = false,
            )}
            is DiscoverIntent.PinCancel,
            is DiscoverIntent.PinTimeout  -> _state.update { it.copy(
                showPinPad         = false,
                showApprovalChoice = false,
                pinFlowTile        = null,
                pinDigits          = "",
                pinError           = false,
                pinSubmitting      = false,
                pinTooManyAttempts = false,
            )}
            is DiscoverIntent.RetryAsRequest -> {
                val tile = _state.value.pinFlowTile ?: return
                _state.update { it.copy(
                    showPinPad         = false,
                    showApprovalChoice = false,
                    pinFlowTile        = null,
                    pinDigits          = "",
                    pinTooManyAttempts = false,
                )}
                handleRequestContent(tile)
            }
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
            _state.update { it.copy(isSearching = true, searchError = false) }
            val result = discoverRepository.search(profileId, q)
            _state.update {
                it.copy(
                    searchResults = result.getOrDefault(emptyList()),
                    isSearching   = false,
                    searchError   = result.isFailure,
                )
            }
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
        _state.update { it.copy(pendingRequests = it.pendingRequests.filter { r -> r.id != id }) }
    }

    private fun handlePinDigit(digit: Char) {
        val current = _state.value.pinDigits
        if (current.length >= 4 || _state.value.pinSubmitting) return
        val newDigits = current + digit
        _state.update { it.copy(pinDigits = newDigits, pinError = false) }
        if (newDigits.length == 4) {
            submitPin(newDigits)
        }
    }

    private fun submitPin(pin: String) {
        val tile      = _state.value.pinFlowTile ?: return
        val profileId = prefs.boundProfileId ?: return
        _state.update { it.copy(pinSubmitting = true) }

        viewModelScope.launch {
            val result = discoverRepository.pinApproveContent(profileId, tile, pin)
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        showPinPad         = false,
                        showApprovalChoice = false,
                        pinFlowTile        = null,
                        pinDigits          = "",
                        pinError           = false,
                        pinSubmitting      = false,
                        showCelebration    = true,
                        // Remove from tile lists — the approved content will arrive via next sync
                        suggestions        = it.suggestions.filter { t -> t.spotifyUri != tile.spotifyUri },
                        searchResults      = it.searchResults.filter { t -> t.spotifyUri != tile.spotifyUri },
                        newlyApprovedUris  = it.newlyApprovedUris + tile.spotifyUri,
                    )
                }
            } else {
                val ex              = result.exceptionOrNull()
                val tooManyAttempts = ex is PinApproveException && ex.tooManyAttempts
                if (tooManyAttempts) {
                    _state.update { it.copy(
                        pinTooManyAttempts = true,
                        pinSubmitting      = false,
                        pinDigits          = "",
                    )}
                } else {
                    _state.update { it.copy(
                        pinError      = true,
                        pinSubmitting = false,
                        pinDigits     = "",
                    )}
                }
            }
        }
    }

    private fun loadSuggestions(profileId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSuggestions = true, suggestionsError = false) }
            val result = discoverRepository.fetchSuggestions(profileId)
            _state.update {
                it.copy(
                    suggestions          = result.getOrDefault(emptyList()),
                    isLoadingSuggestions = false,
                    suggestionsError     = result.isFailure,
                )
            }
        }
    }

    private fun observeRequests(profileId: String) {
        viewModelScope.launch {
            discoverRepository.getVisibleRequests(profileId).collect { requests ->
                val currentPending = requests
                    .filter { it.status == RequestStatus.PENDING }
                    .map { it.tile.spotifyUri }
                    .toSet()

                // Replace the snapshot with the current pending set. Using replace (not
                // addAll) prevents stale URIs from accumulating and triggering spurious
                // celebrations on unrelated content-count increases.
                pendingUrisSnapshot.clear()
                pendingUrisSnapshot.addAll(currentPending)

                _state.update {
                    it.copy(
                        pendingRequests = requests,
                        requestedUris   = currentPending,
                    )
                }
            }
        }
    }

    /**
     * Watches the Room content count. When it increases and we have pending requests,
     * at least one was approved → trigger celebration.
     */
    private fun observeContentCount(profileId: String) {
        viewModelScope.launch {
            contentDao.countAllFlow(profileId)
                .distinctUntilChanged()
                .collect { count ->
                    val prev = previousContentCount
                    if (prev != null && count > prev && pendingUrisSnapshot.isNotEmpty()) {
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
