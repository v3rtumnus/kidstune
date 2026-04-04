package at.kidstune.kids.data.remote

import at.kidstune.kids.data.remote.dto.AddFavoriteRequestDto
import at.kidstune.kids.data.remote.dto.FavoriteResponseDto
import at.kidstune.kids.data.remote.dto.SyncPayloadDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
     * Uploads a new favorite to the backend.
     * Corresponds to `POST /api/v1/profiles/{profileId}/favorites`.
     */
    suspend fun addFavorite(profileId: String, req: AddFavoriteRequestDto): FavoriteResponseDto =
        httpClient.post("$baseUrl/api/v1/profiles/$profileId/favorites") {
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
}
