package at.kidstune.kids.playback

/** Reason codes returned when the Spotify App Remote SDK cannot connect. */
enum class SpotifyConnectionError {
    /** Spotify app is not installed on this device. */
    NOT_INSTALLED,

    /** User is not logged in to the Spotify app. */
    NOT_LOGGED_IN,

    /** User does not have a Spotify Premium account (required for App Remote). */
    PREMIUM_REQUIRED,

    /** An unexpected error occurred during connection. */
    OTHER
}

/**
 * Maps a connection exception thrown by the Spotify App Remote SDK to a
 * [SpotifyConnectionError] code.
 *
 * Marked `internal` so unit tests in the same module can call it directly
 * without leaking it as a public API.
 */
internal fun Throwable.toSpotifyConnectionError(): SpotifyConnectionError {
    val msg = message?.lowercase() ?: ""
    return when {
        "not installed" in msg || "notinstalled" in msg -> SpotifyConnectionError.NOT_INSTALLED
        "not logged"    in msg || "notloggedin"  in msg -> SpotifyConnectionError.NOT_LOGGED_IN
        "premium"       in msg                          -> SpotifyConnectionError.PREMIUM_REQUIRED
        else                                            -> SpotifyConnectionError.OTHER
    }
}
