package at.kidstune.kids.data.mock

import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Hard-coded mock data for the Discover screen.
 * Never used in production code paths.
 */
object MockDiscoverData {

    val mockSuggestions: List<DiscoverTile> = listOf(
        DiscoverTile(
            spotifyUri = "spotify:artist:bibi-tina",
            title      = "Bibi & Tina",
            artistName = "Bibi & Tina",
            imageUrl   = "https://picsum.photos/seed/bibitina/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:pumuckl",
            title      = "Pumuckl",
            artistName = "Pumuckl",
            imageUrl   = "https://picsum.photos/seed/pumuckl/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:die-drei-fragezeichen",
            title      = "Die drei ???",
            artistName = "Die drei ???",
            imageUrl   = "https://picsum.photos/seed/drei999/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:pippi-langstrump",
            title      = "Pippi Långstrump",
            artistName = "Pippi Långstrump",
            imageUrl   = "https://picsum.photos/seed/pippi/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:tkkg",
            title      = "TKKG",
            artistName = "TKKG",
            imageUrl   = "https://picsum.photos/seed/tkkg/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:benjamin-blumchen",
            title      = "Benjamin Blümchen",
            artistName = "Benjamin Blümchen",
            imageUrl   = "https://picsum.photos/seed/benjamin/400/400",
            type       = ContentType.AUDIOBOOK
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:lillifee",
            title      = "Prinzessin Lillifee",
            artistName = "Prinzessin Lillifee",
            imageUrl   = "https://picsum.photos/seed/lillifee/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:artist:yakari",
            title      = "Yakari",
            artistName = "Yakari",
            imageUrl   = "https://picsum.photos/seed/yakari/400/400",
            type       = ContentType.AUDIOBOOK
        ),
    )

    val mockSearchResults: List<DiscoverTile> = listOf(
        DiscoverTile(
            spotifyUri = "spotify:album:frozen-ost",
            title      = "Frozen – Die Eiskönigin (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/frozen/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:album:frozen2-ost",
            title      = "Frozen 2 – Die Eiskönigin 2 (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/frozen2/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:album:encanto-ost",
            title      = "Encanto (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/encanto/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:album:moana-ost",
            title      = "Vaiana (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/vaiana/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:album:lion-king-ost",
            title      = "König der Löwen (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/lionking/400/400",
            type       = ContentType.MUSIC
        ),
        DiscoverTile(
            spotifyUri = "spotify:album:tangled-ost",
            title      = "Rapunzel – Neu verföhnt (OST)",
            artistName = "Various Artists",
            imageUrl   = "https://picsum.photos/seed/rapunzel/400/400",
            type       = ContentType.MUSIC
        ),
    )

    val mockPendingRequests: List<PendingRequest> = listOf(
        PendingRequest(
            id          = "req-mock-1",
            tile        = mockSuggestions[0], // Bibi & Tina – PENDING < 1 h
            status      = RequestStatus.PENDING,
            requestedAt = Instant.now().minus(30, ChronoUnit.MINUTES)
        ),
        PendingRequest(
            id          = "req-mock-2",
            tile        = mockSearchResults[0], // Frozen – REJECTED with note
            status      = RequestStatus.REJECTED,
            requestedAt = Instant.now().minus(2, ChronoUnit.DAYS),
            parentNote  = "Das ist eher was für ältere Kinder."
        ),
    )
}
