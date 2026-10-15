package com.example.spendsync.voice

enum class VoicePhase { Off, Listening, Thinking, Speaking, Paused }

sealed interface VoiceEvent {
    data object Open : VoiceEvent
    /** The user said something; it was sent to the assistant. */
    data object Heard : VoiceEvent
    /** Silence or nothing recognised. */
    data object Missed : VoiceEvent
    /** The first sentence of the answer started playing. */
    data object Speaking : VoiceEvent
    /** The answer is complete and fully spoken. [pause] = it had a card to tap, or it failed. */
    data class Finished(val pause: Boolean) : VoiceEvent
    data object Tap : VoiceEvent
    /** The app or screen went away: the mic must go off. */
    data object Background : VoiceEvent
    data object End : VoiceEvent
}

data class VoiceState(val phase: VoicePhase = VoicePhase.Off, val misses: Int = 0, val keepListening: Boolean = true)

private const val MAX_MISSES = 2

/**
 * Voice mode as a pure state machine: Listening → Thinking → Speaking → Listening … and Paused whenever
 * it is unsure (two silences, a card to tap, a failure, the app in the background). Events that arrive
 * late for a phase that has moved on are ignored, so a stale callback can never reopen the mic.
 */
fun reduceVoice(s: VoiceState, e: VoiceEvent): VoiceState = when {
    e == VoiceEvent.End -> s.copy(phase = VoicePhase.Off, misses = 0)
    s.phase == VoicePhase.Off -> if (e == VoiceEvent.Open) s.copy(phase = VoicePhase.Listening, misses = 0) else s
    else -> when (e) {
        VoiceEvent.Heard -> s.copy(phase = VoicePhase.Thinking, misses = 0)
        VoiceEvent.Missed -> when {
            s.phase != VoicePhase.Listening -> s
            s.misses + 1 >= MAX_MISSES -> s.copy(phase = VoicePhase.Paused, misses = 0)
            else -> s.copy(misses = s.misses + 1)
        }
        VoiceEvent.Speaking -> if (s.phase == VoicePhase.Thinking) s.copy(phase = VoicePhase.Speaking) else s
        is VoiceEvent.Finished ->
            if (s.phase == VoicePhase.Thinking || s.phase == VoicePhase.Speaking) {
                s.copy(phase = if (e.pause || !s.keepListening) VoicePhase.Paused else VoicePhase.Listening)
            } else s
        VoiceEvent.Tap -> when (s.phase) {
            VoicePhase.Speaking, VoicePhase.Paused -> s.copy(phase = VoicePhase.Listening, misses = 0)
            VoicePhase.Listening -> s.copy(phase = VoicePhase.Paused)
            else -> s
        }
        VoiceEvent.Background -> s.copy(phase = VoicePhase.Paused)
        VoiceEvent.Open, VoiceEvent.End -> s
    }
}
