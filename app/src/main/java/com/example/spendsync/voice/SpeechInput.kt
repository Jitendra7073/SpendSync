package com.example.spendsync.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Why listening stopped without words. [Missed] is silence or "didn't catch that"; the rest need the user. */
enum class ListenError { Missed, Permission, Language, Network, Other }

/**
 * The phone's speech recogniser (free, on-device where the phone supports it). Main thread only, like
 * [SpeechRecognizer]. One instance per screen; call [release] when the screen goes away.
 */
class SpeechInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    /** Callbacks that arrive after [stop] (some phones send ERROR_CLIENT on cancel) are dropped. */
    private var active = false

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        locale: Locale,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (ListenError) -> Unit,
        onLevel: (Float) -> Unit = {},
    ) {
        stop()
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        active = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                if (active) best(partialResults)?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                if (!active) return
                active = false
                val text = best(results)
                if (text.isNullOrBlank()) onError(ListenError.Missed) else onFinal(text)
            }

            override fun onError(error: Int) {
                if (!active) return
                active = false
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.Missed
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.Permission
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> ListenError.Network
                        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> ListenError.Language
                        else -> ListenError.Other
                    },
                )
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (active) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Offline the phone's own model is the only option; online, let it pick the better one.
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online())
        r.startListening(intent)
    }

    /** Stops listening and drops whatever was heard. */
    fun stop() {
        if (!active) return
        active = false
        recognizer?.cancel()
    }

    fun release() {
        active = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.activeNetwork != null
    }

    private companion object {
        // SpeechRecognizer constants added in API 31; the values are stable, and older phones never send them.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
