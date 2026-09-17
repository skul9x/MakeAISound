# Phase 02: V3 Gap Silence Calibration & Dynamic Silence Padding

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase2GapSilenceAndDynamicPadTest.kt`

---

## 1. Objective

Bring the natural speech pacing calibration and dynamic silence padding algorithm from **VieNeu-TTS v3.8.1** into `MakeAiSound`:
1. Calibrate default pauses in `SmartTextSegmenter.kt` to match `V3_GAP_SILENCE`:
   - Paragraph break: **700ms** (0.70s)
   - Sentence break: **500ms** (0.50s)
   - Intra-sentence clause break: **300ms** (0.30s)
2. Implement **`edgeSilence`** in `PcmUtils.kt`:
   - Detects duration of leading and trailing silence on 16-bit PCM waveforms using RMS envelope threshold (-45 dB).
3. Implement **`pausePadSamples`** in `PcmUtils.kt`:
   - Computes remaining zeros needed between `prevChunk` and `nextChunk` such that `(tailSilence + zeros + leadSilence) == targetPause`. If `(tailSilence + leadSilence) >= targetPause`, pads 0 zeros.
4. Update `VieNeuStudioSynthesizer.kt` to assemble audio chunks using `pausePadSamples`, preventing double-pause gaps while preserving natural prosodic rhythm.

---

## 2. Requirements

### Functional
- [x] **V3 Pause Calibration in `SmartTextSegmenter.kt`:**
  - `PauseType.PARAGRAPH_BREAK.defaultDurationMs = 700`
  - `PauseType.SENTENCE_BREAK.defaultDurationMs = 500`
  - `PauseType.CLAUSE_BREAK.defaultDurationMs = 300`
- [x] **Silence Measurement (`edgeSilence` in `PcmUtils.kt`):**
  - Calculate 10ms window RMS envelope.
  - Return `Pair<Int, Int>`: `(leadingSilenceSamples, trailingSilenceSamples)`.
- [x] **Dynamic Padding (`pausePadSamples` in `PcmUtils.kt`):**
  - Target samples = `(pauseMs * sampleRate) / 1000`.
  - Needed zeros = `max(0, targetSamples - tailSilence - leadSilence)`.
- [x] **Synthesis Pipeline Integration (`VieNeuStudioSynthesizer.kt`):**
  - When joining chunk audio buffers, insert `pausePadSamples` zeros between chunk $i-1$ and chunk $i$.

### Non-Functional
- [x] Zero allocation during envelope calculation: use primitive loops and pre-allocated arrays where applicable.
- [x] Memory safety: No memory leaks or buffer overflow on empty or 0-sample chunks.

---

## 3. Files to Create/Modify

1. `app/src/main/java/skul9x/example/makesound/engine/SmartTextSegmenter.kt` [MODIFY] - Calibrate V3 pause durations.
2. `app/src/main/java/skul9x/example/makesound/engine/PcmUtils.kt` [MODIFY] - Add `edgeSilence` and `pausePadSamples`.
3. `app/src/main/java/skul9x/example/makesound/engine/VieNeuStudioSynthesizer.kt` [MODIFY] - Integrate dynamic padding into chunk joining.
4. `app/src/test/java/skul9x/example/makesound/Phase2GapSilenceAndDynamicPadTest.kt` [NEW] - Single verification test for Phase 02.

---

## 4. Verification Test Specification

`Phase2GapSilenceAndDynamicPadTest.kt` will verify:
1. **Pause Durations:** `SmartTextSegmenter.segment()` produces 700ms for paragraphs, 500ms for sentences, and 300ms for clauses.
2. **Edge Silence Detection:**
   - Synthetic buffer with 100ms leading silence + tone + 200ms trailing silence correctly measures ~100ms lead and ~200ms tail.
   - All-silence buffer returns full length.
3. **Dynamic Padding Computation:**
   - When tail + lead is 300ms and target is 500ms, `pausePadSamples` returns exactly 200ms worth of samples.
   - When tail + lead exceeds target (e.g. 600ms vs 500ms), `pausePadSamples` returns 0.
4. **Synthesis Joining:** End-to-end joining of multi-chunk audio results in clean concatenation without discontinuity.
