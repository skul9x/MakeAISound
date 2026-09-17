# Phase 01: Voice Catalog Upgrade & MOSS Codec Pad Frame Fix

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase1VoiceCatalogUpgradeTest.kt`

---

## 1. Objective

Upgrade the voice preset definitions in `MakeAiSound` to match **VieNeu-TTS v3.8.1**:
1. Synchronize `voices_v3_turbo.json` from `VieNeu-TTS-main/src/vieneu/assets/voices_v3_turbo.json` into `app/src/main/assets/vieneu/voices_v3_turbo.json` to support all **25 preset voices** (including 5 new voices: *Thiền Tâm Đức*, *Minh Quân Pro*, *Adam bựa*, *Mạnh Dũng*, *Anh Khôi*).
2. Upgrade `VoicePreset` data class to support `featured: Int? = null` ranking (1..10) for Editor's Pick voices (⭐ Tuyển chọn).
3. Implement `stripEncoderPadFrame` (Issue #198 upstream fix) to strip trailing frames with codebook-0 = `455` (caused by MOSS audio tokenizer padding clips not aligned to 3840 samples).
4. Update `VoicePresets.kt` to expose `getFeaturedVoices()` sorted by `featured` rank, filter by new styles, and update UI selector components to highlight featured voices.

---

## 2. Requirements

### Functional
- [x] **Asset Update:** Replace `app/src/main/assets/vieneu/voices_v3_turbo.json` with the 25-voice catalog from `VieNeu-TTS-main/src/vieneu/assets/voices_v3_turbo.json`.
- [x] **Featured Ranking Metadata:**
  - `VoicePreset`: Add `val featured: Int? = null`.
  - In `VoicePresets.loadFromJson()`: Parse `featured` as integer if present and > 0, else `null`.
  - Expose `fun getFeaturedVoices(): List<VoicePreset>` sorted ascending by `featured` rank.
- [x] **Codec Pad Frame Fix (Issue #198):**
  - Implement `stripEncoderPadFrame(codes: Array<IntArray>?): Array<IntArray>?` in `VoicePresets.kt`:
    If `codes` has $\ge 2$ frames and `codes.last()[0] == 455`, strip the last frame.
- [x] **Voice Selection UI Enhancements:**
  - In `VoiceSelectorBottomSheet.kt` / `VoiceSelectionSheet.kt`: Display ⭐ icon/badge for featured voices and allow grouping or prioritizing Editor's Pick.

### Non-Functional
- [x] Backward compatibility: Default voice remains **"Ngọc Huyền"** (`default_voice` in JSON).
- [x] Thread safety: All preset registries remain thread-safe (`ConcurrentHashMap`).

---

## 3. Files to Create/Modify

1. `app/src/main/assets/vieneu/voices_v3_turbo.json` [MODIFY] - Synchronize 25 presets from upstream.
2. `app/src/main/java/skul9x/example/makesound/engine/VoicePresets.kt` [MODIFY] - Add `featured`, `stripEncoderPadFrame`, `getFeaturedVoices()`.
3. `app/src/main/java/skul9x/example/makesound/ui/VoiceSelectorBottomSheet.kt` [MODIFY] - Show ⭐ tag on featured voices.
4. `app/src/test/java/skul9x/example/makesound/Phase1VoiceCatalogUpgradeTest.kt` [NEW] - Single verification test for Phase 01.

---

## 4. Verification Test Specification

`Phase1VoiceCatalogUpgradeTest.kt` will verify:
1. **Preset Count:** Loading `voices_v3_turbo.json` yields exactly 25 voices.
2. **New Voices Registered:** Asserts presence of "Thiền Tâm Đức", "Minh Quân Pro", "Adam bựa", "Mạnh Dũng", "Anh Khôi".
3. **Featured Voices Sorting:** Exactly 10 featured voices with ranks 1..10, sorted properly.
4. **Pad Frame Stripping:**
   - A code array ending with `[455, 12, ...]` has its trailing frame stripped.
   - A code array ending with normal code (e.g. `[482, ...]`) is preserved intact.
   - Single frame arrays are preserved intact.
