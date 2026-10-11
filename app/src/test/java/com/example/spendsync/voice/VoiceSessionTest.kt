package com.example.spendsync.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionTest {
    private fun run(vararg events: VoiceEvent, keepListening: Boolean = true): VoiceState =
        events.fold(VoiceState(keepListening = keepListening)) { s, e -> reduceVoice(s, e) }

    @Test
    fun aNormalTurnLoopsBackToListening() {
        val s = run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Speaking, VoiceEvent.Finished(pause = false))
        assertEquals(VoicePhase.Listening, s.phase)
    }

    @Test
    fun cardsFailuresAndKeepListeningOffPause() {
        assertEquals(VoicePhase.Paused, run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Finished(pause = true)).phase)
        assertEquals(VoicePhase.Paused, run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Finished(pause = false), keepListening = false).phase)
    }

    @Test
    fun oneMissIsRetriedTwoMissesPause() {
        assertEquals(VoicePhase.Listening, run(VoiceEvent.Open, VoiceEvent.Missed).phase)
        assertEquals(VoicePhase.Paused, run(VoiceEvent.Open, VoiceEvent.Missed, VoiceEvent.Missed).phase)
        // Hearing something resets the count.
        assertEquals(VoicePhase.Listening, run(VoiceEvent.Open, VoiceEvent.Missed, VoiceEvent.Heard, VoiceEvent.Finished(false), VoiceEvent.Missed).phase)
    }

    @Test
    fun tapInterruptsSpeechAndTogglesPause() {
        assertEquals(VoicePhase.Listening, run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Speaking, VoiceEvent.Tap).phase)
        assertEquals(VoicePhase.Paused, run(VoiceEvent.Open, VoiceEvent.Tap).phase)
        assertEquals(VoicePhase.Listening, run(VoiceEvent.Open, VoiceEvent.Tap, VoiceEvent.Tap).phase)
        // Waiting for the answer: a tap does nothing (Stop in the panel cancels instead).
        assertEquals(VoicePhase.Thinking, run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Tap).phase)
    }

    @Test
    fun backgroundPausesAndEndCloses() {
        assertEquals(VoicePhase.Paused, run(VoiceEvent.Open, VoiceEvent.Heard, VoiceEvent.Speaking, VoiceEvent.Background).phase)
        assertEquals(VoicePhase.Off, run(VoiceEvent.Open, VoiceEvent.End).phase)
        // Late callbacks after closing never reopen the mic.
        assertEquals(VoicePhase.Off, run(VoiceEvent.Open, VoiceEvent.End, VoiceEvent.Missed, VoiceEvent.Finished(false), VoiceEvent.Tap).phase)
    }
}
