# Roadmap: Real-Time Voice Assistant Modernization

## Phases

- [x] **Phase 1: ChatGPT-Style Voice Visualizer Component**
  - Implement `VoiceVisualizer.kt` with fluid multi-layer motion animation.
  - Support Listening (reactive to microphone volume level), Thinking (fluid rotation and breathing), Speaking (rhythmic harmonic undulating waveforms), and Paused (resting glow).
  - Integrate with `LocalMotion.current` to respect device motion settings.

- [x] **Phase 2: Modern Real-Time Voice Conversation Screen/Overlay**
  - Modernize `VoicePanel.kt` into full-screen `VoicePanel` with ChatGPT-style presentation.
  - Design top bar (close, audio status indicator), central visualizer with live transcript captions, and tactile bottom action controls.
  - Implement tap-to-interrupt / tap-to-talk gesture directly on the visualizer canvas.

- [x] **Phase 3: Assistant Screen Integration & Transition Animations**
  - Integrate immersive voice mode in `AssistantScreen.kt` with smooth `AnimatedVisibility` transitions.
  - Wire up mic button and headset button for fluid transitions.

- [x] **Phase 4: Verification & Empirical Proof**
  - Run JVM unit tests (`./gradlew testDebugUnitTest`).
  - Run voice unit tests (`./gradlew testDebugUnitTest --tests "com.example.spendsync.voice.*"`).
  - Run i18n tests (`./gradlew testDebugUnitTest --tests "com.example.spendsync.I18nResourcesTest"`).
  - Full build successful in 10s with zero regressions.
