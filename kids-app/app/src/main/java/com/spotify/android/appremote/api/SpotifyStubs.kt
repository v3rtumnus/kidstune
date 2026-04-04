@file:Suppress("unused", "UNUSED_PARAMETER")

/**
 * COMPILE-TIME STUBS for the Spotify App Remote SDK.
 *
 * PURPOSE: Allows the module to compile and unit tests to pass without the real SDK AAR.
 *
 * WHEN TO DELETE THIS FILE:
 *   1. Download the real SDK AAR from https://developer.spotify.com/documentation/android/
 *   2. Place it at  kids-app/libs/spotify-app-remote-release-0.8.0.aar
 *   3. Delete THIS file (com/spotify/android/appremote/api/SpotifyStubs.kt)
 *      because the real AAR provides the same class names and the two will conflict.
 *
 * These stubs mirror only the subset of the SDK surface used by SpotifyRemoteManager.
 */

package com.spotify.android.appremote.api

import android.content.Context
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState

// ── ConnectionParams ──────────────────────────────────────────────────────────

class ConnectionParams private constructor(
    val clientId: String,
    val redirectUri: String,
    val showAuthView: Boolean
) {
    class Builder(private val clientId: String) {
        private var redirectUri: String = ""
        private var showAuthView: Boolean = false

        fun setRedirectUri(uri: String): Builder { redirectUri = uri; return this }
        fun showAuthView(show: Boolean): Builder { showAuthView = show; return this }
        fun build(): ConnectionParams = ConnectionParams(clientId, redirectUri, showAuthView)
    }
}

// ── Connector ─────────────────────────────────────────────────────────────────

object Connector {
    interface ConnectionListener {
        fun onConnected(spotifyAppRemote: SpotifyAppRemote)
        fun onFailure(throwable: Throwable)
    }
}

// ── SpotifyAppRemote ──────────────────────────────────────────────────────────

class SpotifyAppRemote {
    val playerApi: PlayerApi get() = PlayerApi()
    val isConnected: Boolean get() = false

    companion object {
        @JvmStatic
        fun connect(
            context: Context,
            connectionParams: ConnectionParams,
            connectionListener: Connector.ConnectionListener
        ) {
            // Stub: real SDK connects asynchronously and calls connectionListener
            connectionListener.onFailure(UnsupportedOperationException("Spotify SDK stub – add real AAR"))
        }

        @JvmStatic
        fun disconnect(spotifyAppRemote: SpotifyAppRemote) {
            // Stub: real SDK disconnects
        }
    }
}

// ── PlayerApi ─────────────────────────────────────────────────────────────────

class PlayerApi {
    fun play(contextUri: String): CallResult<Empty> = CallResult()
    fun skipToIndex(contextUri: String, index: Int): CallResult<Empty> = CallResult()
    fun pause(): CallResult<Empty> = CallResult()
    fun resume(): CallResult<Empty> = CallResult()
    fun skipNext(): CallResult<Empty> = CallResult()
    fun skipPrevious(): CallResult<Empty> = CallResult()
    fun seekTo(positionMs: Long): CallResult<Empty> = CallResult()
    fun subscribeToPlayerState(): Subscription<PlayerState> = Subscription()
}

// ── Empty ─────────────────────────────────────────────────────────────────────

class Empty
