# Phase 02: Independent Voice Sample Player Subsystem

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase2VoiceSamplePlayerTest.kt`

---

## 1. Objective

Develop an independent, lightweight audio player subsystem (`VoiceSamplePlayer`) dedicated to instantaneous preview playback inside the Voice Picker Studio:
1. Provide single-active preview audio playback that automatically halts any active sample when a new voice is previewed.
2. Maintain independent state management via `StateFlow<VoicePlayerState>` without interfering with or resetting the main studio audio player (`AudioPlayerManager`).
3. Support audio playback directly from asset file descriptors, disk files, or in-memory byte buffers with comprehensive error handling and safe resource release.

---

## 2. Requirements

### Functional Requirements
- **Player State (`VoicePlayerState`):**
  - `currentVoiceName: String? = null`
  - `isPlaying: Boolean = false`
  - `error: String? = null`
- **Playback Control:**
  - `playVoiceSample(voiceName: String, audioPath: String): Boolean`
  - `playVoiceBytes(voiceName: String, wavBytes: ByteArray, cacheDir: File): Boolean`
  - `stopVoiceSample()`
  - `release()`
- **Auto-Stop & Single-Active Mechanism:**
  - When a sample for voice B is triggered while voice A is currently playing, voice A must stop immediately and voice B must start cleanly.
  - On natural completion of audio playback, state resets to `isPlaying = false` and `currentVoiceName = null`.
- **Decoupled Architecture:**
  - Uses `MediaPlayerAdapter` abstraction (with `AndroidMediaPlayerAdapter` for runtime) to allow 100% deterministic JVM unit testing.

### Non-Functional Requirements
- Negligible memory footprint (< 500KB overhead).
- Zero native heap leaks on rapid play/stop cycles.

---

## 3. Implementation Steps

1. **Define `VoicePlayerState`:**
   - Create data class in package `skul9x.example.makesound.player`.
2. **Implement `VoiceSamplePlayer.kt`:**
   - Implement `VoiceSamplePlayer` accepting `MediaPlayerAdapter` dependency.
   - Implement event listeners for `onCompletion` and `onError`.
   - Implement `playVoiceSample`, `playVoiceBytes`, `stopVoiceSample`, and `release`.
3. **Write Single Verification Test:**
   - Create `app/src/test/java/skul9x/example/makesound/Phase2VoiceSamplePlayerTest.kt`.
   - Test playback lifecycle (start, complete, stop).
   - Test switching active voice sample on the fly.
   - Test error handling when invalid audio data is supplied.
   - Test resource release and state cleanup.

---

## 4. Files to Create/Modify

- `app/src/main/java/skul9x/example/makesound/player/VoiceSamplePlayer.kt` [NEW]
- `app/src/test/java/skul9x/example/makesound/Phase2VoiceSamplePlayerTest.kt` [NEW]

---

## 5. Test Criteria

- Exactly one unit test file: `Phase2VoiceSamplePlayerTest.kt`.
- Must verify:
  - Transition of `VoicePlayerState` upon play, pause, completion, and error.
  - Single-active sample constraint (stopping prior sample on new voice invocation).
  - Safe byte buffer playback and temporary file management.
  - Correct lifecycle teardown.
