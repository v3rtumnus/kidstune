package at.kidstune.kids.data.remote

import at.kidstune.kids.data.remote.dto.AddFavoriteRequestDto
import at.kidstune.kids.data.remote.dto.ApiErrorDto
import at.kidstune.kids.data.remote.dto.DeltaSyncPayloadDto
import at.kidstune.kids.data.remote.dto.FavoriteResponseDto
import at.kidstune.kids.data.remote.dto.PairingConfirmRequestDto
import at.kidstune.kids.data.remote.dto.PairingConfirmResponseDto
import at.kidstune.kids.data.remote.dto.SyncPayloadDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Ktor [HttpClient] that exposes typed methods for
 * each backend endpoint consumed by the kids app.
 *
 * The [baseUrl] and [httpClient] are injected via Hilt ([NetworkModule]);
 * tests pass a mock engine + an empty base URL directly.
 */
@Singleton
class KidstuneApiClient @Inject constructor(
    internal val httpClient: HttpClient,
    internal val baseUrl: String
) {

    /**
     * Fetches the full content tree for a profile.
     * Corresponds to `GET /api/v1/sync/{profileId}`.
     */
    suspend fun fetchFullSync(profileId: String): SyncPayloadDto =
        httpClient.get("$baseUrl/api/v1/sync/$profileId").body()

    /**
     * Fetches only changes since [since] (ISO-8601 string).
     * Corresponds to `GET /api/v1/sync/{profileId}/delta?since=…`.
     */
    suspend fun fetchDeltaSync(profileId: String, since: String): DeltaSyncPayloadDto =
        httpClient.get("$baseUrl/api/v1/sync/$profileId/delta") {
            parameter("since", since)
        }.body()

    /**
     * Uploads a new favorite to the backend.
     * Corresponds to `POST /api/v1/profiles/{profileId}/favorites`.
     */
    suspend fun addFavorite(profileId: String, req: AddFavoriteRequestDto): FavoriteResponseDto =
        httpClient.post("$baseUrl/api/v1/profiles/$profileId/favorites") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    /**
     * Removes a favorite from the backend.
     * Corresponds to `DELETE /api/v1/profiles/{profileId}/favorites/{trackUri}`.
     * The trackUri is URL-encoded before being placed in the path.
     */
    suspend fun deleteFavorite(profileId: String, trackUri: String) {
        val encoded = trackUri.replace(":", "%3A")
        val response = httpClient.delete("$baseUrl/api/v1/profiles/$profileId/favorites/$encoded")
        if (!response.status.isSuccess() && response.status.value != 404) {
            error("Delete favorite failed: ${response.status}")
        }
    }

    /**
     * Exchanges a 6-digit pairing code for a device JWT.
     * Corresponds to `POST /api/v1/auth/pair/confirm` (public – no auth required).
     *
     * @throws PairingApiException with [PairingApiException.apiCode] set to
     *   `"PAIRING_CODE_NOT_FOUND"` or `"PAIRING_CODE_EXPIRED"` on 410 responses.
     */
    suspend fun pair(code: String, deviceName: String): PairingConfirmResponseDto {
        // This endpoint is public (no auth required). At call time the device token
        // has not yet been stored, so defaultRequest will not add an Authorization header.
        val response = httpClient.post("$baseUrl/api/v1/auth/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairingConfirmRequestDto(code, deviceName))
        }
        if (response.status.isSuccess()) return response.body()
        val error = try { response.body<ApiErrorDto>() } catch (_: Exception) { null }
        throw PairingApiException(
            httpStatus = response.status.value,
            apiCode    = error?.code,
            message    = error?.error ?: "HTTP ${response.status.value}"
        )
    }
}

/** Thrown by [KidstuneApiClient.pair] when the backend rejects a pairing code. */
class PairingApiException(
    val httpStatus: Int,
    val apiCode: String?,
    message: String
) : Exception(message)
