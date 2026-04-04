@file:Suppress("unused")

/**
 * COMPILE-TIME STUBS for Spotify protocol types (ships inside the App Remote AAR).
 * See com/spotify/android/appremote/api/SpotifyStubs.kt for deletion instructions.
 */

package com.spotify.protocol.types

// ── PlayerState ───────────────────────────────────────────────────────────────

data class PlayerState(
    val track: Track? = null,
    val isPaused: Boolean = true,
    val playbackPosition: Long = 0L,
    val playbackSpeed: Float = 0f,
    val playbackOptions: PlaybackOptions? = null
)

// ── Track ─────────────────────────────────────────────────────────────────────

data class Track(
    val name: String = "",
    val uri: String = "",
    val duration: Long = 0L,
    val artist: Artist = Artist(),
    val album: Album = Album(),
    val imageUri: ImageUri = ImageUri(""),
    val isEpisode: Boolean = false,
    val isPodcast: Boolean = false
)

// ── Artist ────────────────────────────────────────────────────────────────────

data class Artist(
    val name: String = "",
    val uri: String = ""
)

// ── Album ─────────────────────────────────────────────────────────────────────

data class Album(
    val name: String = "",
    val uri: String = ""
)

// ── ImageUri ──────────────────────────────────────────────────────────────────

data class ImageUri(val raw: String)

// ── PlaybackOptions ───────────────────────────────────────────────────────────

data class PlaybackOptions(
    val isShuffling: Boolean = false,
    val repeatMode: Int = 0
)
