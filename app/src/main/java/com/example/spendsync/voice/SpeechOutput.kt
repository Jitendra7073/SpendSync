package com.example.spendsync.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * The phone's text-to-speech (free, usually offline). Sentences are queued as they arrive; [onIdle]
 * fires on the main thread when the queue has been fully spoken (not after [stop]).
 * ponytail: no audio focus request; leaving the screen or the app already stops speech.
 */
class SpeechOutput(context: Context, private val onReady: (Boolean) -> Unit = {}) {
    private val main = Handler(Looper.getMainLooper())
    private var pending = 0
    private var nextId = 0
    private var ready = false
    /** Bumped by [stop] so late callbacks from cancelled sentences are ignored. */
    private var generation = 0
    var onIdle: () -> Unit = {}
    /** Called once per language the phone has no voice for (speech falls back to the default voice). */
    var onMissingLanguage: (Locale) -> Unit = {}
    private val reportedMissing = mutableSetOf<String>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) setUp()
            onReady(ready)
        }
    }

    val speaking: Boolean get() = pending > 0

    private fun setUp() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { main.post { finished(utteranceId) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { finished(utteranceId) } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { finished(utteranceId) } }
        })
    }

    private fun finished(id: String?) {
        // Utterances from before the last stop() are not counted.
        if (id?.substringBefore(':') != generation.toString() || pending == 0) return
        pending--
        if (pending == 0) onIdle()
    }

    /** Installed voices for [language] (ISO 639 code), offline ones first. */
    fun voices(language: String): List<Voice> {
        if (!ready) return emptyList()
        return runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
    }

    /** Queues [text]. Returns false when speech is not available at all. */
    fun speak(text: String, locale: Locale, prefs: VoicePrefs): Boolean {
        if (!ready || text.isBlank()) return false
        val chosen = prefs.voice.takeIf { it.isNotBlank() }
            ?.let { name -> runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name } }
            ?.takeIf { it.locale.language == locale.language }
        if (chosen != null) {
            tts.voice = chosen
        } else {
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (reportedMissing.add(locale.language)) onMissingLanguage(locale)
                tts.language = Locale.getDefault()
            }
        }
        tts.setSpeechRate(prefs.speed / 100f)
        val queued = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "$generation:${nextId++}") == TextToSpeech.SUCCESS
        if (queued) pending++
        return queued
    }

    fun stop() {
        generation++
        pending = 0
        if (ready) tts.stop()
    }

    fun release() {
        stop()
        tts.shutdown()
    }
}
