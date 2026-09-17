# Phase 01: Voice Sample Assets & Repository Manager

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase1VoiceSampleManagerTest.kt`

---

## 1. Objective

Set up the voice sample asset repository and create `VoiceSampleManager` to provide instantaneous 0ms audio retrieval for all 25 preset voices:
1. Package audio sample files into `app/src/main/assets/vieneu/samples/` covering all 25 voices defined in `voices_v3_turbo.json`.
2. Implement `VoiceSampleManager` responsible for resolving, loading, caching, and serving preview audio data.
3. Provide robust fallback harmonic tone synthesis ensuring every voice can be audited without errors even if an asset is missing.

---

## 2. Requirements

### Functional Requirements
- **Asset Directory:** Store voice sample audio clips in `assets/vieneu/samples/` mapped by voice name (e.g., `Ngọc Huyền.wav`, `Đoan (nữ miền Nam).wav`, `Thiền Tâm Đức.wav`, etc.).
- **Asset Resolution (`findBundledAssetPath`):**
  - Exact match (case-insensitive, ignoring extension).
  - Prefix match (matching base name before parenthetical region/gender tags).
- **Multi-Tier Resolution (`getOrResolveSample`):**
  1. Memory Cache (`ConcurrentHashMap<String, ByteArray>`).
  2. Pre-bundled Asset in `assets/vieneu/samples/`.
  3. Persistent Disk Cache (`cacheDir/voice_samples/`).
  4. Fallback Tone Generator (`generateHarmonicPreviewPcm`).
- **Harmonic Benchmark Tone:** Generates pleasant 440Hz/880Hz envelope-shaped acoustic PCM audio if an asset file cannot be resolved offline.

### Non-Functional Requirements
- Instantaneous asset retrieval (< 5ms) from Android Assets or memory.
- Thread-safe caching and memory management.

---

## 3. Implementation Steps

1. **Populate Voice Sample Assets:**
   - Copy curated WAV voice references into `app/src/main/assets/vieneu/samples/`.
   - Ensure all 25 voices have valid audio references or calibrated clips.
2. **Implement `VoiceSampleManager.kt`:**
   - Add `VoiceSampleManager` in package `skul9x.example.makesound.engine`.
   - Implement `findBundledAssetPath(voiceName: String): String?`.
   - Implement `getBundledSampleBytes(voiceName: String): ByteArray?`.
   - Implement disk cache and memory cache lookup.
   - Implement `generateHarmonicTone(durationMs: Int, sampleRate: Int): ShortArray`.
3. **Write Single Verification Test:**
   - Create `app/src/test/java/skul9x/example/makesound/Phase1VoiceSampleManagerTest.kt`.
   - Test asset path matching across exact and prefix voice names.
   - Test memory and disk caching behaviors.
   - Test harmonic fallback tone generation and WAV header validation.

---

## 4. Files to Create/Modify

- `app/src/main/assets/vieneu/samples/*` [NEW]
- `app/src/main/java/skul9x/example/makesound/engine/VoiceSampleManager.kt` [NEW]
- `app/src/test/java/skul9x/example/makesound/Phase1VoiceSampleManagerTest.kt` [NEW]

---

## 5. Test Criteria

- Exactly one unit test file: `Phase1VoiceSampleManagerTest.kt`.
- Must verify:
  - Resolution of bundled assets for presets.
  - Correct WAV header creation and audio payload length.
  - Memory caching efficiency.
  - Fallback tone synthesis when a voice asset is absent.
