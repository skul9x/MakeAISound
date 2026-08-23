# Implementation Plan: MakeAiSound Studio Enhancements & Bug Fixes

**Created:** 2026-08-23  
**Status:** 🟡 In Progress  
**Target:** Android App (`d:/skul9x/MakeAiSound`)

---

## 1. Overview & Objectives

This plan addresses four critical functional enhancements and bug fixes requested for the **MakeAiSound** Android TTS Studio application:
1. **Default Voice Setup:** Set the default selected voice to **"Ngọc Huyền"** (`Nữ · Bắc · Giọng đọc tự nhiên`).
2. **Multi-Layer Voice Filtering:** Upgrade the single flat voice filter into a composable multi-tier filter system supporting **Region** (North/Central/South), **Gender** (Female/Male), and **Style** (Natural/Storytelling/News) simultaneously without filter collisions.
3. **Main Studio Screen Cleanup:** Remove the "Share" (Chia sẻ) button from the main studio audio player card.
4. **Emotion Cue Fix & Cursor Jump UX:**
   - **G2P Engine Fix:** Resolve the bug where emotion tags like `[cười]` or `[thở dài]` were phonemized by SeaG2P as literal Vietnamese words rather than converted to neural emotion tokens (`<|emotion_1|>`, `<|emotion_2|>`, `<|emotion_3|>`).
   - **Cursor Jump UX:** Upgrade text editing state to track cursor selection and ensure tapping emotion tag helper chips inserts the tag at the cursor position and places the cursor immediately after the inserted tag.

---

## 2. Implementation Phases

| Phase | Phase Name | Status | Verification Test File |
|---|---|---|---|
| **01** | [Multi-Tier Voice Filter & Default "Ngọc Huyền" Voice](./phase-01-voice-filter-and-default.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase1VoiceFilterAndDefaultTest.kt` |
| **02** | [Main Screen UI Cleanup & Cursor-Aware Emotion Insertion](./phase-02-ui-cleanup-and-cursor-ux.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase2UiCleanupAndCursorUxTest.kt` |
| **03** | [Emotion Cue G2P Preservation Engine Bug Fix](./phase-03-emotion-cue-g2p-fix.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase3EmotionCueG2pFixTest.kt` |

---

## 3. Workflow Protocol

- All phase documentation files are written in English in the `plans/` directory.
- Each phase defines **exactly one** comprehensive file-based test for verification.
- After implementing each phase, only that single test will be executed for verification.
