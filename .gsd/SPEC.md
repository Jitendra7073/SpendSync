# Specification: ChatGPT-Style Real-Time Voice Assistant & Conversation Strategy

Status: FINALIZED
Date: 2026-10-16
Author: UI Expert Agent

## 1. Problem Statement
SpendSync currently provides microphone dictation and headset voice mode. However, the voice mode was implemented as an inline 40dp bottom bar that leaves the cluttered text chat behind, featuring only a simple scaling circle and basic buttons.
Users expect a modern, immersive real-time conversation experience modeled after ChatGPT Voice Assistant:
- An organic, fluid motion animation visualizer (pulsing glowing orb / waveforms) in the center of the screen.
- Real-time reactivity to audio input levels (`voice.level`) when listening.
- Mesmerizing fluid motion during thinking (processing).
- Dynamic, rhythmic multi-wave undulating animation while the assistant is speaking.
- Clear real-time status transitions (Listening → Thinking → Speaking → Paused).
- Live transcription captions with smooth typography transitions.
- Fluid full-screen or immersive modal overlay with seamless entrance/exit transitions.
- Tactile, modern controls (Tap orb to interrupt or resume, Mute/Pause, End session).
- Full compliance with SpendSync motion preferences (`LocalMotion.current`).

## 2. Conversation Strategy Architecture
1. **Immersive Voice Experience:**
   - Tapping the Headphone button opens the `VoiceConversationOverlay` which gracefully covers the screen with a subtle blur/gradient backdrop.
   - The user feels present in a live audio call with SpendSync Assistant.
2. **Real-time Orb Visualizer (`VoiceVisualizer`):**
   - **Listening Phase:** Center orb with layered radiant glow rings that expand and contract smoothly based on the microphone RMS audio level (`voice.level`) with spring physics.
   - **Thinking Phase:** Gradient aura rotating and breathing gently (infinite transition), signaling neural processing and awaiting streamed tokens.
   - **Speaking Phase:** Multi-layered rhythmic harmonic waveform / fluid undulating pulse synchronized with speech output.
   - **Paused Phase:** Calm, dimmed resting state with a subtle "Tap to speak" invitation.
3. **Live Captions & Feedback:**
   - Live partial speech recognizer output displayed cleanly above or below the orb with smooth fade and slide motion.
   - When cards (transactions, bills, proposals) are returned, the assistant announces "I've put it on screen for you" and displays a sleek card preview without breaking the voice flow.
4. **Motion System Compliance:**
   - All animations query `LocalMotion.current.enabled(MotionKind.Transitions)` / `MotionKind.Entrance`.
   - If animations are toggled off by user or system accessibility settings, static high-contrast visual indicators are rendered without motion lag.

## 3. Scope of Implementation
1. **New Component:** `app/src/main/java/com/example/spendsync/ui/assistant/VoiceVisualizer.kt`:
   - Fluid Canvas / Graphics layer drawing the animated multi-layer ChatGPT-style orb with radial gradients, outer glow, and dynamic audio-reactive geometry.
2. **Updated Component:** `app/src/main/java/com/example/spendsync/ui/assistant/VoicePanel.kt`:
   - Re-architect `VoicePanel` into the immersive `VoiceConversationOverlay` with header (back/minimize/end), central animated visualizer, live captions, floating card preview, and tactile action controls.
3. **Integration in `AssistantScreen.kt`:**
   - Seamless animated transition (`AnimatedVisibility` with fadeIn/slideIn) between standard text mode and real-time voice mode.
4. **Verification:**
   - Unit tests run and pass without regressions.
   - Android compilation check passes.
