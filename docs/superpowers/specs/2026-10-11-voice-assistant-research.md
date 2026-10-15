# Voice assistant — research & feasibility (2026-10-11)

Status: research only, nothing built. Goal: talk to the SpendSync assistant by voice in any
language the user is comfortable in, pick from several voices, and optionally let the
assistant answer in the user's **own** cloned voice.

## TL;DR

Feasible, and most of it is free. Don't build a second assistant: put a voice layer in
front of the one we already have (`POST /api/assistant/chat`). That way tools, permissions,
failover, the offline fallback and amount masking all carry over unchanged.

| Phase | What the user gets | Tech | Cost | Risk |
|---|---|---|---|---|
| 1 | Talk to the assistant and hear it answer; several voices per language | Android `SpeechRecognizer` + `TextToSpeech` (on-device) | ₹0 | Low |
| 2 | Better, more human voices (+ Hinglish-tolerant listening) | Gemini 3.8 Flash-Lite TTS prebuilt voices; Groq Whisper as STT fallback | Free tier, then about $0.8 per hour of audio | Low |
| 3 | "Speak in my voice" | Gemini voice replication (`POST /v1beta/voices`, needs a consent recording) | Same as phase 2 | Medium (consent, legal) |
| 4 (maybe) | Gemini-Live-style full duplex: interrupt mid-sentence, no turn taking | Gemini Live native audio over WebSocket with ephemeral tokens | Unclear free quota | High; breaks masking (see below) |

Recommendation: ship 1, then 2+3 together. Park 4.

## Free-forever rule (decided 2026-10-11)

The user wants the voice feature to be **free forever**. Only on-device processing can
guarantee that. Cloud free tiers are a gift that can shrink or disappear. So:

- **The floor is on-device.** `SpeechRecognizer` + `TextToSpeech` must give a complete,
  working voice assistant on their own. Everything cloud is a bonus layered on top.
- **Never attach billing** to the Gemini or Groq projects used for voice. A key without
  billing cannot be charged. When the quota runs out it returns 429/quota errors, and we
  silently drop to the device voice or recogniser. Worst case is a plainer voice, never a bill.
- **No paid-only providers.** No ElevenLabs, Sarvam (credits run out), GPU hosting
  (Chatterbox/Qwen3), or the Gemini Live paid tier.
- **"My voice" has two free routes:**
  1. Gemini voice replication on the free key (a bonus: best quality, but Google can change
     it). If the voice id stops working, speak in the chosen device voice and tell the user once.
  2. Later, **on-device cloning** with Kyutai Pocket TTS (MIT, runs on the phone CPU, about
     5 s reference). It is the only option that is free forever and never sends the voice
     sample anywhere. Today it is experimental (community Android port, Hindi only via
     community fine-tunes, 100+ MB optional download), so plan a spike, not a launch.
- **Privacy note for the free Gemini tier:** Google may use free-tier inputs to improve its
  products. For voice samples (biometric-ish) the consent screen must say this plainly. Route 2
  avoids it entirely.
- Per-user daily caps on `/api/assistant/speak` and voice creation so one user can't
  burn the shared free quota for everyone.

## What exists today (constraints)

- The assistant is text in, streamed SSE out (`backend/src/assistant/loop.ts`), with a
  provider-neutral **free** model chain (`llm/chain.ts`) and an offline fallback. A voice
  feature must keep the same "never visibly fail" behaviour.
- The backend is on Vercel (`maxDuration = 60` on the chat route). Vercel WebSockets are only
  a public beta (June 2026) and testers saw connections drop after about 5 minutes, so we
  must not host long audio sockets ourselves.
- **PIN masking**: `maskAmountsInText` must apply to everything shown, so it must apply to
  everything *spoken* too. This decides the architecture: we must speak **text we control**,
  never raw model audio.
- The manifest has no `RECORD_AUDIO` yet. Phase 1 needs it, along with the runtime permission flow.
- UI is translated into en/hi/es/fr/de (`tr()`); voice strings follow the same rule.

## Options researched

### Speech → text (listening)
- **Android `SpeechRecognizer`** (platform API). Free, streams partial results, `hi-IN` and
  every app language are supported through the Google app. `createOnDeviceSpeechRecognizer`
  (API 31+) works offline but must be guarded with `isOnDeviceRecognitionAvailable()` (it
  crashes on some OEMs otherwise). A developer report found Hindi–English code-switching only "mixed". **Pick for phase 1.**
- **Groq Whisper large-v3-turbo**. Free tier is about 8 h of audio per day and 25 MB per file
  (third-party figures; check the console). Handles Hinglish better. Fits as a server-side
  fallback: the phone posts a short clip and gets text back. Phase 2.
- **sherpa-onnx** (on-device, Kotlin API, streaming ASR models). Fully offline, but adds
  100+ MB of models per language. Not worth it while the platform recogniser works.

### Text → speech (speaking) and voices
- **Android `TextToSpeech`**: free and offline. `getVoices()` returns several voices per
  locale (male/female/network variants), so phase 1 already has "multiple voices". Quality
  depends on the device.
- **Gemini 3.8 Flash / Flash-Lite TTS**: about 30 prebuilt voices, style prompts ("warm,
  slow"), multilingual. Free tier in AI Studio (quota not published). Paid Flash costs $9 per
  1M audio tokens (about $0.81 per hour), promotional until 31 Dec 2026, then roughly double.
  It fits our router: on failure, fall back to the device voice.
- **Sarvam Bulbul v3**: best for Indian languages (11 languages, 30+ voices), ₹30 per 10k
  characters. Cloning is enterprise-only. A good later option if Hindi quality from Gemini is weak.
- **ElevenLabs**: best quality, 32 languages, instant cloning, but paid only. Consumer Reports
  found its cloning consent was a checkbox. Not a fit for a free-by-default app.

### Voice cloning ("my voice")
- **Gemini voice replication** (official, Sept 2026). It needs 10–30 s of clean speech **plus a consent clip of the same adult speaker** reading Google's fixed statement. Output is a `voice_…` ID (stored, 1-year TTL, **max 200 per project**) or a `voicekey_…` (stateless, encrypted, **7-day TTL**). It works with the TTS models only, **not the Live API**.
  → **Pick.** Same provider and key we already use, consent is enforced by Google, and no
  GPU for us to run.
- **Chatterbox Multilingual** (Resemble AI, MIT, has Hindi, 5 s reference, built-in PerTh
  watermark). The best self-hosted fallback if Gemini's 200-voice cap or price becomes a
  problem. It needs a GPU host (Modal/RunPod), so it's a paid line item.
- **Qwen3-TTS** (3 s cloning, about 100 ms first packet, 10+ languages). Strong, but its
  streaming is "simulated" per its own docs, and it also needs a GPU.
- **XTTS-v2 / F5-TTS / Fish Speech**: good quality, but **non-commercial licences**. Ruled out.
- **On-device: Kyutai Pocket TTS** (100M parameters, MIT, real-time on CPU, 5 s cloning; six
  official languages, Hindi only through community fine-tunes) and **NeuTTS Nano/Air**
  (Apache, watermark). Promising, but there are no phone benchmarks, the Android port is an
  "experimental" community build, and it adds 100+ MB. Revisit in 2027.

### Full-duplex "Gemini Live" style
- **Gemini Live native audio**: very natural, auto-detects language, supports barge-in and
  function calling. The phone connects straight to Google with a short-lived **ephemeral
  token** that our backend mints (`POST /v1beta/auth_tokens`, `uses: 1`, model locked). Sessions are limited to 15 min.
- Why it is parked:
  1. **Masking breaks.** The model speaks amounts itself, and we can't run
     `maskAmountsInText` over audio. A prompt instruction is not a guarantee.
  2. It bypasses our free failover chain, offline fallback, tool tiers and audit (tools
     would have to be re-declared for Live and relayed).
  3. Free-tier concurrency is undocumented. A forum thread from this month shows close code
     1011 "quota exceeded" on free projects.
  4. Replicated (own) voices aren't supported in Live.
- Frameworks (Pipecat, LiveKit Agents) solve barge-in well but need an always-on server. That is overkill for us.

## Proposed design (phases 1–3)

```
mic ─► SpeechRecognizer (partial text on screen)
        └─ final text ─► existing POST /api/assistant/chat (SSE, tools, failover)
                              └─ streamed text ─► reduceReply ─► maskAmountsInText
                                                    └─ split into sentences ─► Speaker
Speaker = DeviceSpeaker (TextToSpeech)          phase 1
        | CloudSpeaker  (POST /api/assistant/speak → Gemini TTS, voice id) phase 2/3,
                         on any error → DeviceSpeaker for the rest of the reply
```

- **Android**: a mic button and a "voice mode" toggle in `AssistantScreen`. Speak each
  sentence as soon as it is complete (low perceived latency). Tapping the mic or the screen
  stops speech (simple barge-in). Cards (bills, proposals, follow-ups) still show
  visually, and the voice says "I've put a card on screen". Listening language = app
  language, with an "auto" option.
- **Backend (phase 2)**: `POST /api/assistant/speak {text, voice}` wrapped in `withApi`. It
  re-masks nothing (the phone sends already-masked text), applies rate limits per user per
  day, returns `audio/l16` or `opus`, and caches short repeated phrases.
- **Voice choice**: a synced setting (`SettingField.Assistant` → `assistant_voice`), plus
  a preview button in Settings → Assistant. "Device voice" is always there as a free option.
- **My voice (phase 3)**, Settings → Assistant → "Use my voice":
  1. Explain what happens and that only *your own* voice is allowed. No uploads, mic only.
  2. Record 15–30 s reading a provided paragraph in their language, then record Google's
     consent statement live.
  3. Send to the backend, which calls `/v1beta/voices`. Keep **no raw audio** after the call;
     store only the `voice_…` id (one per user).
  4. Add "Delete my voice" (deletes on Google and clears the column) and do the same on
     account deletion.
  - Voice is biometric-ish personal data (DPDP Act): explicit consent screen, purpose
    limited to speaking to that same user, deletable.
- **Guardrails** (Google Play AI-content policy + India IT Amendment Rules 2026 on
  "synthetically generated information", in force since 20 Feb 2026):
  - Only the account owner's own voice. No celebrity or "other person" presets that imitate
    real people. Prebuilt voices are fine because they are synthetic.
  - Cloned audio is played **only inside the app, to that user**. No export, share or save.
    This keeps us out of "enables sharing of SGI" territory. If sharing is ever added, it
    needs an audible and metadata AI label.
  - Show a visible "AI voice" tag while speaking, and report via the existing support ticket flow.
  - Masking applies to speech exactly as to text, and spoken notifications never include amounts.

## Open questions (decide before writing a design spec)

1. Is the 200-stored-voices-per-project cap acceptable for launch? If not, use stateless keys
   with a weekly re-record, or self-host Chatterbox.
2. ~~Free tier or paid budget?~~ Decided: free forever (see "Free-forever rule").
3. Should voice mode also be reachable outside the chat (home-screen shortcut, "Hey
   SpendSync")? Hotword means an always-on mic service, which I recommend against.
4. Before committing, test Hindi/Hinglish quality on the user's own phones. That is a
   30-minute spike with `SpeechRecognizer` + `TextToSpeech`.

## Sources
- Gemini Live API capabilities & limits: https://ai.google.dev/gemini-api/docs/live-api/capabilities
- Gemini ephemeral tokens: https://ai.google.dev/gemini-api/docs/ephemeral-tokens
- Gemini voice replication: https://ai.google.dev/gemini-api/docs/generate-content/voice-replication
- Gemini speech generation limits: https://ai.google.dev/gemini-api/docs/generate-content/speech-generation
- Gemini 3.8 TTS launch: https://thenextweb.com/news/gemini-tts-3-8-flash-voice-design-cloning
- Gemini 3.8 TTS pricing (third-party): https://www.eesel.ai/blog/gemini-3-8-flash-tts , https://docs.litellm.ai/blog/gemini_3_8_flash_tts
- Live free-tier quota issue: https://discuss.ai.google.dev/t/native-audio-live-free-tier-setup-quota-rejection-below-visible-tpm-which-admission-metric-applies/186840
- Vercel WebSockets beta & 5-min drop: https://dev.classmethod.jp/en/articles/vercel-functions-websocket-public-beta-verification/ , https://ably.com/vercel/websockets-on-vercel
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer ; Hinglish test: https://hackernoon.com/testing-androids-on-device-speech-recognition-with-hindi-english-code-switching
- Groq Whisper free limits: https://www.mintlify.com/cheahjs/free-llm-api-resources/providers/free/groq
- sherpa-onnx: https://k2-fsa.github.io/sherpa/onnx/
- Sarvam Bulbul: https://www.sarvam.ai/text-to-speech , https://docs.sarvam.ai/api-reference-docs/pricing
- ElevenLabs cloning: https://elevenlabs.io/docs/eleven-api/concepts/voice-cloning
- Chatterbox: https://github.com/resemble-ai/chatterbox ; Qwen3-TTS: https://github.com/QwenLM/Qwen3-TTS
- Open-source TTS roundups: https://www.bentoml.com/blog/exploring-the-world-of-open-source-text-to-speech-models , https://pinggy.io/blog/best_open_source_self_hosted_text_to_speech_models/
- Pocket TTS: https://kyutai.org/tts/ , https://github.com/alucinax/PocketTTS-Android-Engine , https://huggingface.co/saryps-labs/pocket-tts-hindi ; on-device roundup: https://getstream.io/blog/best-on-device-tts-models/
- Consent gate pattern: https://huggingface.co/blog/voice-consent-gate ; Consumer Reports: https://www.consumerreports.org/electronics/identity-theft/voice-cloning-apps-let-criminals-easily-steal-your-voice-a6024784872
- Google Play AI-generated content policy: https://support.google.com/googleplay/android-developer/answer/14094294
- India IT Amendment Rules 2026 (SGI): https://www.legal500.com/intelligence/india/technology/it-rules-2026-deepfake-regulation-three-hour-takedowns-and-ai-labelling-obligations
- Pipecat / LiveKit comparison: https://www.plivo.com/blog/how-to-build-a-voice-ai-agent-livekit-pipecat-ten-or-native/
