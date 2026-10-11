# Voice assistant, phase 1 — design + plan (2026-10-11)

Research and options: `2026-10-11-voice-assistant-research.md`. Rule: **free forever**, so this phase
is on-device only (Android `SpeechRecognizer` + `TextToSpeech`). No backend, DB or synced-setting changes.

## What the user gets
1. **Dictation**: a mic button in the chat input (it takes Send's place while the box is empty).
   Spoken words fill the box live, and the user edits and sends as usual.
2. **Voice mode**: a headset button opens a panel in place of the input bar. The chat stays visible
   above it. The panel loops: Listening → Thinking → Speaking → Listening… Tap = interrupt or resume,
   End = close.
3. **Read aloud** on every answer (speaker icon in the answer actions).
4. **Settings → Assistant → Voice** (this phone only): listening language, voice, speed, keep listening.

## Behaviour rules
- The voice layer only calls `AssistantViewModel.send()` and reads `state`. Chat, tools, failover,
  history and the offline answer are unchanged.
- What gets spoken is `maskAmountsInText` → `markdownToPlain` → masked amounts replaced by the
  translated phrase "amount hidden". Unlocking amounts (PIN) unlocks them in speech too, because it is
  the same `AmountVisibilityState`.
- Streaming: only complete sentences are spoken (`. ? ! । …` or a newline; decimals like `1,250.50`
  never split). The rest is spoken when the reply finishes. If the streamed text no longer starts with
  what was already queued (`reset`), speech stops and restarts from the new text.
- A reply with a confirm / bill / email / follow-up card: after speaking, say "I've put it on screen"
  and **pause** (no auto-listen). The user taps the card, then taps the panel to talk again.
- Silence / no match: listen once more, then pause ("Tap to talk").
- Leaving the screen, the app going to background (ON_STOP), or the reply failing with nothing to say
  → stop speaking, turn the mic off, pause or end. The mic is never on unless the panel says so.
- `RECORD_AUDIO` is requested the first time a mic is tapped. If it is permanently denied, a dialog
  offers to open app settings.
- No recogniser on the device → mic and voice-mode buttons are hidden. No TTS → voice mode shows
  captions only. Missing voice for the language → default voice.
- Interrupting by voice is out of scope (needs echo cancellation), and so are cloud voices, cloning and wake words.

## Units
| File | Job | Test |
|---|---|---|
| `voice/SpokenText.kt` | pure: `toSpeech(text, masked, hiddenPhrase)`, `SentenceFeed` (incremental sentence cutter + reset detection) | `SpokenTextTest` (JVM) |
| `voice/VoiceSession.kt` | pure state machine `VoicePhase` + `reduce(phase, VoiceEvent)` | `VoiceSessionTest` (JVM) |
| `voice/SpeechInput.kt` | wraps `SpeechRecognizer` (main thread): partial, final, level, error | manual |
| `voice/SpeechOutput.kt` | wraps `TextToSpeech`: ready, voices(locale), speak(queue), stop, onIdle | manual |
| `voice/VoicePrefs.kt` | data class; stored in `SessionDataStore` (device-local) | — |
| `ui/assistant/VoicePanel.kt` | panel UI + controller wiring the above into `AssistantScreen` | manual |
| `ui/settings/AssistantPage.kt` | Voice group | manual |
| manifest | `RECORD_AUDIO`, `<queries>` for `RecognitionService` + `TTS_SERVICE` | build |
| strings (en/hi/es/fr/de) | `voice_*` in `strings_assistant.xml` | `I18nResourcesTest` |
| `AppSearchIndex` | "voice" row | — |

## Plan (test-first where logic is pure)
1. `SpokenTextTest` → `SpokenText.kt`.
2. `VoiceSessionTest` → `VoiceSession.kt`.
3. `VoicePrefs` + `SessionDataStore` flow/update.
4. `SpeechInput`, `SpeechOutput`.
5. Strings in all five languages, manifest.
6. `VoicePanel` + `AssistantScreen` (mic in input bar, voice mode, read aloud) + permission flow.
7. Settings Voice group + search index.
8. `./gradlew testDebugUnitTest assembleDebug`, review the diff, then a manual check on a phone
   (Hindi, Hinglish, English, offline, PIN-masked, a card reply, backgrounding mid-answer).
