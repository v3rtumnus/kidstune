package at.kidstune.kids.data.preferences

import at.kidstune.kids.domain.model.MockProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder for profiles received from the pairing response.
 * Populated by [PairingViewModel] after a successful pairing and consumed by
 * [ProfileSelectionViewModel] to show the real child profiles instead of
 * mock data.  Cleared after profile binding completes.
 */
@Singleton
class PendingProfilesHolder @Inject constructor() {
    var profiles: List<MockProfile>? = null
}
