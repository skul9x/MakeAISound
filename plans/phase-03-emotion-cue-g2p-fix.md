# Phase 03: Emotion Cue G2P Preservation Engine Bug Fix

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Previous Phase:** [Phase 02 — Main Screen UI Cleanup & Cursor UX](./phase-02-ui-cleanup-and-cursor-ux.md)

---

## 1. Objective

Fix the bug where non-verbal emotion tags (e.g. `[cười]` / `[chuckle]`, `[thở dài]` / `[sigh]`, `[hắng giọng]` / `[clear throat]`) were spoken aloud as literal words ("cười", "thở dài") instead of synthesizing natural acoustic non-verbal sounds (laughter, sighs, throat clearing).

---

## 2. Root Cause Analysis

1. When text contains `[cười]`, passing the raw chunk text directly to the native `SeaG2P.phonemize(chunkText)` caused the underlying sea-g2p phonemizer to treat the brackets and letters as regular text. It stripped the brackets and converted `"cười"` into Vietnamese phonemes (`"k ư ờ j"`).
2. By the time `VieNeuOnnxEngine.inferWithTiming(phonemes)` was executed, the phonemes string contained `"k ư ờ j"` instead of the special token `<|emotion_1|>`.
3. Consequently, the tokenizer generated phoneme token IDs representing the spoken word `"cười"`, rather than Token ID 9 (`<|emotion_1|>`), preventing the VieNeu neural model from generating the expressive laughter acoustic codes.

---

## 3. Requirements

### Functional Requirements
- **Emotion Preservation in Phonemization:**
  - Implement `SeaG2P.phonemizeWithEmotions(text: String): String` (mirroring VieNeu Python upstream `phonemize_text_with_emotions`).
  - Regex-detect all emotion cues:
    - Laughter: `[cười]`, `[cuoi]`, `[chuckle]`, `[laugh]`, `[laughter]`, `<|emotion_1|>` -> `<|emotion_1|>`.
    - Sigh: `[thở dài]`, `[tho dai]`, `[sigh]`, `<|emotion_2|>` -> `<|emotion_2|>`.
    - Clear Throat: `[hắng giọng]`, `[hang giong]`, `[clear throat]`, `[clearthroat]`, `<|emotion_3|>` -> `<|emotion_3|>`.
  - Phonemize only the non-emotion text segments via `SeaG2P.phonemize()`.
  - Reassemble phonemes with preserved `<|emotion_k|>` special tokens and proper punctuation binding (`.,!?;:…)]}\"'’”`).
- **Synthesizer Pipeline Update:**
  - In [VieNeuStudioSynthesizer.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/engine/VieNeuStudioSynthesizer.kt), invoke `SeaG2P.phonemizeWithEmotions(chunkText)` during synthesis.
  - In [SmartTextSegmenter.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/engine/SmartTextSegmenter.kt), ensure emotion tags inside sentences or at chunk ends remain uncorrupted during punctuation normalization.

### Non-Functional Requirements
- Fast G2P throughput with minimal overhead (< 1ms overhead per emotion tag).
- Exact parity with the upstream `VieNeu-TTS-1000h` training data phoneme stream format.

---

## 4. Implementation Steps

1. **Implement `phonemizeWithEmotions` Helper:**
   - In `SeaG2P.kt` (or an engine companion object), create:
     ```kotlin
     fun phonemizeWithEmotions(
         text: String,
         phonemizer: (String) -> String = { phonemize(it) }
     ): String
     ```
   - Split input text into alternating spans of normal text and emotion tags.
   - For each emotion tag, resolve to `<|emotion_k|>`.
   - For normal text spans, phonemize using `phonemizer()`.
   - Join pieces maintaining proper spacing and attaching following punctuation to preceding emotion tokens.

2. **Integrate with `VieNeuStudioSynthesizer`:**
   - Update line 124 in `VieNeuStudioSynthesizer.kt`:
     ```kotlin
     val phonemes = SeaG2P.phonemizeWithEmotions(chunkText)
     ```
   - Verify `tokenizer.encode(phonemes)` parses `<|emotion_1|>` into Token ID 9, `<|emotion_2|>` into Token ID 10, and `<|emotion_3|>` into Token ID 11.

---

## 5. Files to Create/Modify

- **Modify:**
  - `app/src/main/java/skul9x/example/makesound/engine/SeaG2P.kt`
  - `app/src/main/java/skul9x/example/makesound/engine/VieNeuStudioSynthesizer.kt`
  - `app/src/main/java/skul9x/example/makesound/engine/SmartTextSegmenter.kt`
- **Create:**
  - `app/src/test/java/skul9x/example/makesound/Phase3EmotionCueG2pFixTest.kt`

---

## 6. Verification Plan (Single Comprehensive Test)

Run the single dedicated unit test:
```bash
./gradlew testDebugUnitTest --tests "skul9x.example.makesound.Phase3EmotionCueG2pFixTest"
```

### Test Scope
- Verify `phonemizeWithEmotions` on text with `[cười]` preserves `<|emotion_1|>` in phonemes and DOES NOT contain phonemes of the word "cười".
- Verify `phonemizeWithEmotions` on text with `[thở dài]` preserves `<|emotion_2|>`.
- Verify `phonemizeWithEmotions` on text with `[hắng giọng]` preserves `<|emotion_3|>`.
- Verify English emotion aliases (`[chuckle]`, `[sigh]`, `[clear throat]`).
- Verify tokenizer encodes `<|emotion_1|>`, `<|emotion_2|>`, `<|emotion_3|>` to their expected token IDs (e.g. 9, 10, 11).
- Verify punctuation attachment (`"... [cười]."` -> `"... <|emotion_1|>."`).
