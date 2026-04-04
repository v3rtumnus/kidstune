package at.kidstune.kids.domain

import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.repository.FavoriteRepository
import at.kidstune.kids.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val repository = mockk<FavoriteRepository>(relaxed = true)
    private val useCase    = ToggleFavoriteUseCase(repository)

    private val track = LocalTrack(
        id              = "track-1",
        albumId         = "album-1",
        spotifyTrackUri = "spotify:track:abc123",
        title           = "Test Track",
        artistName      = "Test Artist",
        durationMs      = 180_000L,
        trackNumber     = 1,
        discNumber      = 1,
        imageUrl        = null
    )

    @Test
    fun `invoke delegates to FavoriteRepository toggleFavorite`() = runTest {
        useCase(track)
        coVerify(exactly = 1) { repository.toggleFavorite(track) }
    }
}
