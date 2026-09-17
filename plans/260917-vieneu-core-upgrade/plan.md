# Implementation Plan: VieNeu-TTS v3.8.1 Core Upgrade

**Created:** 2026-09-17  
**Status:** 🟡 In Progress  
**Target:** Android App (`/home/skul9x/Desktop/Code/MakeAISound-main`)  
**Upstream Source:** VieNeu-TTS v3.8.1 (`/home/skul9x/Desktop/Code/MakeAISound-main/VieNeu-TTS-main`)

---

## 1. Overview & Objectives

This plan upgrades the on-device AI synthesis core of the **MakeAiSound** Android TTS application using the latest upstream advancements in **VieNeu-TTS v3.8.1**:
1. **Full 25-Voice Catalog & Codec Frame Fix:** Synchronize 25 preset voices (adding 5 new voices: *Thiền Tâm Đức*, *Minh Quân Pro*, *Adam bựa*, *Mạnh Dũng*, *Anh Khôi*), add Editor's Pick ⭐ (`featured: 1..10`) ranking and strip trailing MOSS codec pad frame `455` (Issue #198).
2. **V3 Calibrated Gap Silence & Dynamic Padding:** Implement natural prosody pause calibration (`V3_GAP_SILENCE`: 700ms paragraph, 500ms sentence, 300ms clause) and `pausePadSamples` (measuring previous chunk tail and next chunk lead silence to dynamically pad zeros).
3. **Syllable-Aware Babble Guard:** Introduce Vietnamese/English syllable counting into `maxExpectedFrames` to enforce tight generation caps on short phrases (1–4 syllables), preventing end-of-speech hallucinated babble.

---

## 2. Implementation Phases

| Phase | Phase Name | Status | Single Verification Test File |
|---|---|---|---|
| **01** | [Voice Catalog & MOSS Codec Pad Frame Fix](./phase-01-voice-catalog-and-codec-fix.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase1VoiceCatalogUpgradeTest.kt` |
| **02** | [V3 Gap Silence & Dynamic Padding](./phase-02-gap-silence-and-dynamic-pad.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase2GapSilenceAndDynamicPadTest.kt` |
| **03** | [Syllable-Aware Babble Guard](./phase-03-syllable-cap-and-babble-guard.md) | ⬜ Pending | `app/src/test/java/skul9x/example/makesound/Phase3SyllableCapAndBabbleGuardTest.kt` |

---

## 3. Workflow Protocol

- All phase documentation files are written in English in the `plans/260917-vieneu-core-upgrade/` directory.
- Each phase defines **exactly one** comprehensive file-based test for verification.
- After implementing each phase, only that single test will be executed for verification.
- Once verified, execution will stop for user review before proceeding to the next phase.
