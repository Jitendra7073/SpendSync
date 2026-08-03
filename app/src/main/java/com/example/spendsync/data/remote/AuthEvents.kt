package com.example.spendsync.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Application-wide authentication event bus.
 *
 * Emit to [unauthorizedFlow] whenever a 401 Unauthorized response is received.
 * Collectors (e.g. AppNavigation) listen for these events to clear the local
 * session and redirect the user to the Login screen.
 *
 * [MutableSharedFlow] with default replay = 0 ensures only active collectors
 * receive the event — no stale 401s are replayed to new collectors.
 */
object AuthEvents {
    val unauthorizedFlow: MutableSharedFlow<Unit> = MutableSharedFlow()
}
