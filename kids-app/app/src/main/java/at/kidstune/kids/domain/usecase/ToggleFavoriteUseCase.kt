package at.kidstune.kids.domain.usecase

import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.repository.FavoriteRepository
import javax.inject.Inject

/**
 * Toggles the favorite status for a track.
 *
 * Delegates to [FavoriteRepository.toggleFavorite]; this use case exists to give
 * ViewModels a single named entry-point and to make the intent in unit tests clear.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) {
    suspend operator fun invoke(track: LocalTrack) {
        favoriteRepository.toggleFavorite(track)
    }
}
