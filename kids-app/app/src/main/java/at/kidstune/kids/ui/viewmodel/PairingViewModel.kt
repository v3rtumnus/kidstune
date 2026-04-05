package at.kidstune.kids.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.preferences.DeviceTokenPreferences
import at.kidstune.kids.data.preferences.PendingProfilesHolder
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.PairingApiException
import at.kidstune.kids.data.remote.dto.PairingProfileDto
import at.kidstune.kids.domain.model.MockProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

sealed interface PairingState {
    /** User is entering digits; [digits] is the current list (0–6 elements). */
    data class EnteringCode(val digits: List<Int> = emptyList()) : PairingState

    /** API call in flight. */
    data object Confirming : PairingState

    /** Pairing succeeded; token has been stored; [profiles] are ready for selection. */
    data class Success(val profiles: List<MockProfile>) : PairingState

    /** Pairing failed; [message] is user-visible; user may edit [digits] and retry. */
    data class Error(val message: String, val digits: List<Int>) : PairingState
}

// ── Intent ────────────────────────────────────────────────────────────────────

sealed interface PairingIntent {
    data class DigitEntered(val digit: Int) : PairingIntent
    data object BackspacePressed : PairingIntent
    data object ConnectPressed : PairingIntent
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val apiClient: KidstuneApiClient,
    private val tokenPrefs: DeviceTokenPreferences,
    private val pendingProfilesHolder: PendingProfilesHolder
) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.EnteringCode())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun onIntent(intent: PairingIntent) {
        when (intent) {
            is PairingIntent.DigitEntered -> addDigit(intent.digit)
            PairingIntent.BackspacePressed -> removeLastDigit()
            PairingIntent.ConnectPressed -> confirmPairing()
        }
    }

    private fun currentDigits(): List<Int>? = when (val s = _state.value) {
        is PairingState.EnteringCode -> s.digits
        is PairingState.Error        -> s.digits
        else                         -> null
    }

    private fun addDigit(digit: Int) {
        val digits = currentDigits() ?: return
        if (digits.size < 6) _state.value = PairingState.EnteringCode(digits + digit)
    }

    private fun removeLastDigit() {
        val digits = currentDigits() ?: return
        _state.value = PairingState.EnteringCode(digits.dropLast(1))
    }

    private fun confirmPairing() {
        val digits = currentDigits()?.takeIf { it.size == 6 } ?: return
        val code = digits.joinToString("")
        _state.value = PairingState.Confirming

        viewModelScope.launch {
            try {
                val response = apiClient.pair(code, Build.MODEL)
                val profiles = response.profiles.map { it.toMockProfile() }
                tokenPrefs.token = response.deviceToken
                pendingProfilesHolder.profiles = profiles
                _state.value = PairingState.Success(profiles)
            } catch (e: PairingApiException) {
                val message = when (e.apiCode) {
                    "PAIRING_CODE_EXPIRED" ->
                        "Code abgelaufen – bitte einen neuen Code anfordern"
                    else ->
                        "Ungültiger Code"
                }
                _state.value = PairingState.Error(message, digits)
            } catch (_: Exception) {
                _state.value = PairingState.Error("Verbindung fehlgeschlagen", digits)
            }
        }
    }
}

// ── Mapping ───────────────────────────────────────────────────────────────────

private fun PairingProfileDto.toMockProfile(): MockProfile = MockProfile(
    id    = id,
    name  = name,
    emoji = when (avatarIcon?.uppercase()) {
        "BEAR"    -> "🐻"
        "FOX"     -> "🦊"
        "BUNNY"   -> "🐰"
        "OWL"     -> "🦉"
        "CAT"     -> "🐱"
        "PENGUIN" -> "🐧"
        else      -> "🎵"
    }
)
