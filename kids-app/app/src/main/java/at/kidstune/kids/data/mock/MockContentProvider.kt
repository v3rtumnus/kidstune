package at.kidstune.kids.data.mock

import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.domain.model.BrowseTile
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import java.time.Instant

/**
 * Hard-coded mock data used for Compose Previews and in-memory Room tests.
 * Never used in production code paths.
 */
object MockContentProvider {

    const val PROFILE_EMMA = "profile-emma"
    const val PROFILE_MAX  = "profile-max"

    // ── Content entries (top-level tiles) ─────────────────────────────────

    val contentEntries: List<LocalContentEntry> = listOf(
        LocalContentEntry(
            id          = "entry-bibi-artist",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:artist:4vGrte8FDu062Ntj0RsPiZ",
            scope       = ContentScope.ARTIST,
            contentType = ContentType.MUSIC,
            title       = "Bibi & Tina",
            imageUrl    = null,
            artistName  = "Bibi & Tina",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-tkkg-artist",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:artist:1kxEMZBYVVFd9k72jCavsb",
            scope       = ContentScope.ARTIST,
            contentType = ContentType.AUDIOBOOK,
            title       = "TKKG",
            imageUrl    = null,
            artistName  = "TKKG",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-yakari-album",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:album:3vLaOYouUEZEKBHDOJmMAk",
            scope       = ContentScope.ALBUM,
            contentType = ContentType.AUDIOBOOK,
            title       = "Yakari – Folge 1–10",
            imageUrl    = null,
            artistName  = "Yakari",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-pumuckl-playlist",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:playlist:5FjFbyCTuXPxN0sAFJFWFZ",
            scope       = ContentScope.PLAYLIST,
            contentType = ContentType.AUDIOBOOK,
            title       = "Pumuckl – Klassiker",
            imageUrl    = null,
            artistName  = "Pumuckl",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-die-drei-artist",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:artist:3qupBB9LKQM5Lh3MsNy5B1",
            scope       = ContentScope.ARTIST,
            contentType = ContentType.AUDIOBOOK,
            title       = "Die drei ???",
            imageUrl    = null,
            artistName  = "Die drei ???",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-filly-track",
            profileId   = PROFILE_EMMA,
            spotifyUri  = "spotify:track:1a2b3c4d5e6f7g8h9i0j",
            scope       = ContentScope.TRACK,
            contentType = ContentType.MUSIC,
            title       = "Ich bin Filly Witchy",
            imageUrl    = null,
            artistName  = "Filly",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        // Max's profile entries
        LocalContentEntry(
            id          = "entry-max-schlager",
            profileId   = PROFILE_MAX,
            spotifyUri  = "spotify:artist:5Rl15oVamLq7FbSX0XtRxy",
            scope       = ContentScope.ARTIST,
            contentType = ContentType.MUSIC,
            title       = "Kinderlieder Medley",
            imageUrl    = null,
            artistName  = "Various",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-max-biene-maja",
            profileId   = PROFILE_MAX,
            spotifyUri  = "spotify:album:0xXt5k8bW0Yxdnq7pZAiMt",
            scope       = ContentScope.ALBUM,
            contentType = ContentType.AUDIOBOOK,
            title       = "Biene Maja",
            imageUrl    = null,
            artistName  = "Biene Maja",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
        LocalContentEntry(
            id          = "entry-benjamin-blumchen",
            profileId   = PROFILE_MAX,
            spotifyUri  = "spotify:artist:4mUBpqSFbAH2x38KpoW0Kv",
            scope       = ContentScope.ARTIST,
            contentType = ContentType.AUDIOBOOK,
            title       = "Benjamin Blümchen",
            imageUrl    = null,
            artistName  = "Benjamin Blümchen",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        ),
    )

    // ── Albums ────────────────────────────────────────────────────────────

    val albums: List<LocalAlbum> = listOf(
        LocalAlbum(
            id               = "album-bibi-01",
            contentEntryId   = "entry-bibi-artist",
            spotifyAlbumUri  = "spotify:album:bibi01",
            title            = "Bibi & Tina – Folge 1",
            imageUrl         = null,
            releaseDate      = "2010-03-15",
            totalTracks      = 1,
            contentType      = ContentType.MUSIC
        ),
        LocalAlbum(
            id               = "album-bibi-02",
            contentEntryId   = "entry-bibi-artist",
            spotifyAlbumUri  = "spotify:album:bibi02",
            title            = "Bibi & Tina – Folge 2",
            imageUrl         = null,
            releaseDate      = "2010-04-15",
            totalTracks      = 1,
            contentType      = ContentType.MUSIC
        ),
        LocalAlbum(
            id               = "album-tkkg-200",
            contentEntryId   = "entry-tkkg-artist",
            spotifyAlbumUri  = "spotify:album:tkkg200",
            title            = "TKKG – Folge 200",
            imageUrl         = null,
            releaseDate      = "2023-09-01",
            totalTracks      = 3,
            contentType      = ContentType.AUDIOBOOK
        ),
        LocalAlbum(
            id               = "album-yakari-1",
            contentEntryId   = "entry-yakari-album",
            spotifyAlbumUri  = "spotify:album:3vLaOYouUEZEKBHDOJmMAk",
            title            = "Yakari – Folge 1",
            imageUrl         = null,
            releaseDate      = "2005-06-01",
            totalTracks      = 2,
            contentType      = ContentType.AUDIOBOOK
        ),
        LocalAlbum(
            id               = "album-drei-fragezeichen-1",
            contentEntryId   = "entry-die-drei-artist",
            spotifyAlbumUri  = "spotify:album:drei01",
            title            = "Die drei ??? – Folge 1",
            imageUrl         = null,
            releaseDate      = "1979-01-01",
            totalTracks      = 4,
            contentType      = ContentType.AUDIOBOOK
        ),
    )

    // ── Tracks ────────────────────────────────────────────────────────────

    val tracks: List<LocalTrack> = listOf(
        LocalTrack(
            id               = "track-bibi-01-1",
            albumId          = "album-bibi-01",
            spotifyTrackUri  = "spotify:track:bibi01t1",
            title            = "Bibi & Tina – Folge 1",
            artistName       = "Bibi & Tina",
            durationMs       = 3_600_000,
            trackNumber      = 1,
            discNumber       = 1,
            imageUrl         = null
        ),
        LocalTrack(
            id               = "track-bibi-02-1",
            albumId          = "album-bibi-02",
            spotifyTrackUri  = "spotify:track:bibi02t1",
            title            = "Bibi & Tina – Folge 2",
            artistName       = "Bibi & Tina",
            durationMs       = 3_300_000,
            trackNumber      = 1,
            discNumber       = 1,
            imageUrl         = null
        ),
        LocalTrack(
            id               = "track-tkkg-200-1",
            albumId          = "album-tkkg-200",
            spotifyTrackUri  = "spotify:track:tkkg200t1",
            title            = "TKKG 200 – Teil 1",
            artistName       = "TKKG",
            durationMs       = 1_200_000,
            trackNumber      = 1,
            discNumber       = 1,
            imageUrl         = null
        ),
        LocalTrack(
            id               = "track-tkkg-200-2",
            albumId          = "album-tkkg-200",
            spotifyTrackUri  = "spotify:track:tkkg200t2",
            title            = "TKKG 200 – Teil 2",
            artistName       = "TKKG",
            durationMs       = 1_200_000,
            trackNumber      = 2,
            discNumber       = 1,
            imageUrl         = null
        ),
        LocalTrack(
            id               = "track-tkkg-200-3",
            albumId          = "album-tkkg-200",
            spotifyTrackUri  = "spotify:track:tkkg200t3",
            title            = "TKKG 200 – Teil 3",
            artistName       = "TKKG",
            durationMs       = 1_200_000,
            trackNumber      = 3,
            discNumber       = 1,
            imageUrl         = null
        ),
        LocalTrack(
            id               = "track-yakari-1-1",
            albumId          = "album-yakari-1",
            spotifyTrackUri  = "spotify:track:yakari1t1",
            title            = "Yakari – Folge 1, Teil 1",
            artistName       = "Yakari",
            durationMs       = 900_000,
            trackNumber      = 1,
            discNumber       = 1,
            imageUrl         = null
        ),
    )

    // ── Browse tiles (UI mock, not Room entities) ──────────────────────────

    val mockMusicTiles: List<BrowseTile> = listOf(
        BrowseTile(
            id = "tile-bibi1",
            title = "Bibi & Tina – Folge 1",
            artistName = "Bibi & Tina",
            imageUrl = "https://picsum.photos/seed/bibitina1/400/400",
            spotifyTrackUri = "spotify:track:bibi01t1"
        ),
        BrowseTile(
            id = "tile-bibi2",
            title = "Bibi & Tina – Folge 2",
            artistName = "Bibi & Tina",
            imageUrl = "https://picsum.photos/seed/bibitina2/400/400",
            spotifyTrackUri = "spotify:track:bibi02t1"
        ),
        BrowseTile(
            id = "tile-bibi3",
            title = "Bibi & Tina – Folge 3",
            artistName = "Bibi & Tina",
            imageUrl = "https://picsum.photos/seed/bibitina3/400/400",
            spotifyTrackUri = "spotify:track:bibi03t1"
        ),
        BrowseTile(
            id = "tile-bibi-blocksberg",
            title = "Bibi Blocksberg – Folge 1",
            artistName = "Bibi Blocksberg",
            imageUrl = "https://picsum.photos/seed/bibiblocks/400/400",
            spotifyTrackUri = "spotify:track:blocks01t1"
        ),
        BrowseTile(
            id = "tile-filly",
            title = "Filly Witchy – Das Lied",
            artistName = "Filly",
            imageUrl = "https://picsum.photos/seed/fillyw/400/400",
            spotifyTrackUri = "spotify:track:filly01t1"
        ),
        BrowseTile(
            id = "tile-kinderlieder",
            title = "Kinderlieder Medley",
            artistName = "Various",
            imageUrl = "https://picsum.photos/seed/kinderlied/400/400",
            spotifyTrackUri = "spotify:track:kinderlied01"
        ),
    )

    val mockAudiobookTiles: List<BrowseTile> = listOf(
        BrowseTile(
            id = "tile-tkkg200",
            title = "TKKG – Folge 200",
            artistName = "TKKG",
            imageUrl = "https://picsum.photos/seed/tkkg200/400/400",
            spotifyTrackUri = "spotify:track:tkkg200t1"
        ),
        BrowseTile(
            id = "tile-yakari1",
            title = "Yakari – Folge 1",
            artistName = "Yakari",
            imageUrl = "https://picsum.photos/seed/yakari1/400/400",
            spotifyTrackUri = "spotify:track:yakari1t1"
        ),
        BrowseTile(
            id = "tile-drei1",
            title = "Die drei ??? – Folge 1",
            artistName = "Die drei ???",
            imageUrl = "https://picsum.photos/seed/dreifrage1/400/400",
            spotifyTrackUri = "spotify:track:drei01t1"
        ),
        BrowseTile(
            id = "tile-benjamin",
            title = "Benjamin Blümchen",
            artistName = "Benjamin Blümchen",
            imageUrl = "https://picsum.photos/seed/benjamin1/400/400",
            spotifyTrackUri = "spotify:track:benjamin01"
        ),
        BrowseTile(
            id = "tile-pumuckl",
            title = "Pumuckl – Klassiker",
            artistName = "Pumuckl",
            imageUrl = "https://picsum.photos/seed/pumuckl1/400/400",
            spotifyTrackUri = "spotify:track:pumuckl01"
        ),
        BrowseTile(
            id = "tile-bienemaja",
            title = "Biene Maja – Folge 1",
            artistName = "Biene Maja",
            imageUrl = "https://picsum.photos/seed/bienemaja1/400/400",
            spotifyTrackUri = "spotify:track:biene01t1"
        ),
    )

    // ── Favorites ─────────────────────────────────────────────────────────

    val favorites: List<LocalFavorite> = listOf(
        LocalFavorite(
            id               = "fav-1",
            profileId        = PROFILE_EMMA,
            spotifyTrackUri  = "spotify:track:bibi01t1",
            title            = "Bibi & Tina – Folge 1",
            artistName       = "Bibi & Tina",
            imageUrl         = null,
            addedAt          = Instant.parse("2025-01-15T10:00:00Z"),
            synced           = true
        ),
        LocalFavorite(
            id               = "fav-2",
            profileId        = PROFILE_EMMA,
            spotifyTrackUri  = "spotify:track:tkkg200t1",
            title            = "TKKG 200 – Teil 1",
            artistName       = "TKKG",
            imageUrl         = null,
            addedAt          = Instant.parse("2025-01-20T14:00:00Z"),
            synced           = false
        ),
    )
}
