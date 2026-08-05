package com.example.spendsync.ui.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant

/**
 * App-wide "amount visibility" session — one PIN unlock reveals every
 * masked amount everywhere for a configured duration, then everything
 * re-masks automatically. In-memory only; deliberately does not survive
 * app restart (reopening the app always starts re-masked).
 */
class AmountVisibilityState {
    var isVisible by mutableStateOf(false)
        private set
    var unlockedUntil: Instant? = null
        private set
    var showUnlockPrompt by mutableStateOf(false)
        private set

    fun requestUnlock() {
        if (!isVisible) showUnlockPrompt = true
    }

    fun dismissUnlockPrompt() {
        showUnlockPrompt = false
    }

    fun unlock(durationSeconds: Int) {
        isVisible = true
        unlockedUntil = Instant.now().plusSeconds(durationSeconds.toLong())
        showUnlockPrompt = false
    }

    fun lock() {
        isVisible = false
        unlockedUntil = null
    }
}
