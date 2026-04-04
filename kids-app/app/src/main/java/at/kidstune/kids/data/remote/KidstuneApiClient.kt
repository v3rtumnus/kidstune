package at.kidstune.kids.data.remote

import at.kidstune.kids.data.remote.dto.SyncPayloadDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
}
