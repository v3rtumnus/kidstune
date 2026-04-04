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
