package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairingConfirmRequestDto(
    val code: String,
    val deviceName: String
)

@Serializable
data class PairingProfileDto(
    val id: String,
    val name: String,
    val avatarIcon: String? = null,
    val avatarColor: String? = null
)

@Serializable
data class PairingConfirmResponseDto(
    val deviceToken: String,
    val familyId: String,
    val profiles: List<PairingProfileDto> = emptyList()
)

/** Generic error body returned by the backend on 4xx/5xx responses. */
@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val code: String? = null
)
