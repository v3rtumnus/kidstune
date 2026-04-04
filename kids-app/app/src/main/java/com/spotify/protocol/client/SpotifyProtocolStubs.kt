@file:Suppress("unused", "UNUSED_PARAMETER")

/**
 * COMPILE-TIME STUBS for the Spotify Protocol library (ships inside the App Remote AAR).
 * See com/spotify/android/appremote/api/SpotifyStubs.kt for deletion instructions.
 */

package com.spotify.protocol.client

import com.spotify.protocol.types.PlayerState

// ── Subscription ─────────────────────────────────────────────────────────────

class Subscription<T> {
    fun interface EventCallback<T> {
        fun onEvent(t: T)
    }

    fun setEventCallback(callback: EventCallback<T>): Subscription<T> = this
    fun setErrorCallback(errorCallback: Throwable.() -> Unit): Subscription<T> = this
    fun cancel() {}
    fun isCanceled(): Boolean = true
}

// ── CallResult ────────────────────────────────────────────────────────────────

class CallResult<T> {
    fun interface ResultCallback<T> {
        fun onResult(t: T)
    }

    fun setResultCallback(callback: ResultCallback<T>): CallResult<T> = this
    fun setErrorCallback(errorCallback: Throwable.() -> Unit): CallResult<T> = this
    fun cancel() {}
}
