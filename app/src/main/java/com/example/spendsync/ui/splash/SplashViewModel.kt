package com.example.spendsync.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendsync.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** One-shot navigation events emitted by the ViewModel. */
sealed class SplashNavEvent {
    object GoHome  : SplashNavEvent()
    // No local session — send the user straight to Login.
    object GoLogin : SplashNavEvent()
}

class SplashViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    // SharedFlow with replay=0 — each event consumed exactly once.
    // StateFlow would re-emit on recomposition causing duplicate navigations.
    private val _navEvent = MutableSharedFlow<SplashNavEvent>(replay = 0)
    val navEvent = _navEvent.asSharedFlow()

    private var authJob: Job? = null

    /**
     * Checks the local session in the background while the animation plays.
     * Sends [GoHome] or [GoLogin] once the minimum display time has elapsed.
     */
    fun checkAuth(animationDurationMs: Long = 2_800L) {
        if (authJob?.isActive == true) return

        authJob = viewModelScope.launch {
            val start      = System.currentTimeMillis()
            val hasSession = repository.hasLocalSession()

            val elapsed   = System.currentTimeMillis() - start
            val remaining = animationDurationMs - elapsed
            if (remaining > 0) delay(remaining)

            _navEvent.emit(
                if (hasSession) SplashNavEvent.GoHome else SplashNavEvent.GoLogin
            )
        }
    }
}
