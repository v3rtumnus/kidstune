package com.kidstune.parent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidstune.parent.data.local.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

// ── State ────────────────────────────────────────────────────────────────────

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Success(val familyId: String) : LoginState
    data class Error(val message: String) : LoginState
}

// ── Intents ──────────────────────────────────────────────────────────────────

sealed interface LoginIntent {
    /** User tapped the login button. */
    data object StartLogin : LoginIntent

    /** Deep link callback received with auth data from the backend. */
    data class HandleCallback(val familyId: String, val token: String) : LoginIntent

    /** Something went wrong (invalid deep link, network failure, etc.). */
    data class HandleError(val message: String) : LoginIntent
}

// ── One-shot effects (UI side effects) ───────────────────────────────────────

sealed interface LoginEffect {
    /** Open the given URL in the system browser (Custom Tab). */
    data class OpenBrowser(val url: String) : LoginEffect
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    @Named("backendBaseUrl") private val backendBaseUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            LoginIntent.StartLogin -> {
                _state.value = LoginState.Loading
                viewModelScope.launch {
                    _effects.send(LoginEffect.OpenBrowser("$backendBaseUrl/api/v1/auth/spotify/login"))
                }
            }

            is LoginIntent.HandleCallback -> {
                authPreferences.saveAuth(intent.familyId, intent.token)
                _state.value = LoginState.Success(intent.familyId)
            }

            is LoginIntent.HandleError -> {
                _state.value = LoginState.Error(intent.message)
            }
        }
    }
}