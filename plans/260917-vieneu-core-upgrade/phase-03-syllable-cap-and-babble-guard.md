# Phase 03: Syllable-Aware Babble Guard & Engine Robustness

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase3SyllableCapAndBabbleGuardTest.kt`

---

## 1. Objective

Implement the upstream **Syllable-Aware Babble Guard** from **VieNeu-TTS v3.8.1** inside `VieNeuOnnxEngine.kt`:
1. Prevent runaway audio generation and end-of-speech hallucinated mumbling ("nói nhảm") on short phrases (1–4 syllables) like "Xin chào", "Dạ", "Cảm ơn", "Vâng ạ".
2. Add Vietnamese & English syllable counting logic (`syllableCount`) based on vowel nuclei and prosodic units.
3. Upgrade `maxExpectedFrames(phonemes)`:
   - For cue-only inputs (`<|emotion_k|>` with no text words): Cap at **13 frames** (~1.0s).
   - For short utterances ($\le 4$ syllables): Enforce tight cap:
     $$\text{Cap} = \min\left(\text{Cap}_{\text{linear}}, 13 + 5 \times (\text{syl} - 1)\right)$$
   - For longer sentences: Maintain standard linear frame cap:
     $$\text{Cap}_{\text{linear}} = 24 + \lceil 2.0 \times \text{effLen} \rceil$$

---

## 2. Requirements

### Functional
- [x] **Syllable Counting (`syllableCount`):**
  - Accurately count syllable nuclei in Vietnamese phoneme representations (ignoring markup tags `<|emotion_\d+|>` and `<en>...</en>`).
  - Empty or punctuation-only strings count as 1 syllable.
- [x] **Emotion Cue Only Check (`isCueOnly`):**
  - Return `true` if string contains `<|emotion_` and no alphabetical characters outside emotion tags.
- [x] **Syllable-Aware Frame Cap in `VieNeuOnnxEngine.kt`:**
  - `SINGLE_WORD_MAX_FRAMES = 13`
  - `SYLLABLE_CAP_PER_EXTRA = 5`
  - `SYLLABLE_CAP_MAX_SYL = 4`
  - `SINGLE_WORD_MAX_PHONES = 24`
  - 1 syllable $\rightarrow$ cap = 13 frames (~1.04s)
  - 2 syllables $\rightarrow$ cap = 18 frames (~1.44s)
  - 3 syllables $\rightarrow$ cap = 23 frames (~1.84s)
  - 4 syllables $\rightarrow$ cap = 28 frames (~2.24s)
  - $>4$ syllables $\rightarrow$ standard linear cap $\min(\text{maxNewFrames}, 24 + \lceil 2.0 \times \text{effLen} \rceil)$.

### Non-Functional
- [x] Zero regex allocation inside per-frame inference loops.
- [x] Deterministic cap calculation: Identical input phonemes produce identical frame limits.

---

## 3. Files to Create/Modify

1. `app/src/main/java/skul9x/example/makesound/engine/VieNeuOnnxEngine.kt` [MODIFY] - Implement `syllableCount`, `isCueOnly`, and upgrade `maxExpectedFrames`.
2. `app/src/test/java/skul9x/example/makesound/Phase3SyllableCapAndBabbleGuardTest.kt` [NEW] - Single verification test for Phase 03.

---

## 4. Verification Test Specification

`Phase3SyllableCapAndBabbleGuardTest.kt` will verify:
1. **Syllable Counting:**
   - Single syllable words ("Chào", "Dạ", "Ổn", "Hi") count as 1.
   - 2-syllable phrases ("Xin chào", "Cảm ơn", "Tạm biệt") count as 2.
   - 3-syllable phrases ("Tôi hiểu rồi", "Tuyệt vời quá") count as 3.
   - 4-syllable phrases ("Chúc bạn ngủ ngon", "Hẹn gặp lại nhé") count as 4.
2. **Cue-Only Detection:**
   - `"<|emotion_1|>"` and `"<|emotion_2|> <|emotion_3|>"` return `true`.
   - `"Xin chào <|emotion_1|>"` returns `false`.
3. **Frame Cap Verification:**
   - 1-syllable input returns exactly 13 frames.
   - 2-syllable input returns exactly 18 frames.
   - 3-syllable input returns exactly 23 frames.
   - 4-syllable input returns exactly 28 frames.
   - Cue-only input returns 13 frames.
   - Long paragraph input returns proper linear cap $\ge 50$ frames without being constrained by syllable cap.
