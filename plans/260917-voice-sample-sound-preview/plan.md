# Implementation Plan: Voice Sample Sound Preview

**Created:** 2026-09-17  
**Status:** 🟡 In Progress  
**Target:** Android App (`/home/skul9x/Desktop/Code/MakeAISound-main`)  
**Feature:** Instant Voice Sample Auditory Preview in Voice Picker Studio  

---

## 1. Overview & Objectives

This plan implements an instant **Voice Sample Sound Preview** subsystem in **MakeAiSound** so users can audition any of the 25 VieNeu-TTS voices directly inside `VoicePickerBottomSheet` before synthesizing speech:
1. **Voice Sample Assets & Manager:** Package audio sample clips in `app/src/main/assets/vieneu/samples/` and implement `VoiceSampleManager` with multi-tier resolution (Memory Cache -> Bundled Asset -> Disk Cache -> Acoustic Tone Fallback).
2. **Independent Sample Player Subsystem:** Build `VoiceSamplePlayer` with dedicated `MediaPlayer` lifecycle, single-active preview state tracking (`StateFlow<VoicePlayerState>`), asset file descriptor support, and conflict-free coexistence with `AudioPlayerManager`.
3. **Voice Picker UI Integration:** Embed an interactive preview play/stop button on each `VoicePickerItem` in `VoicePickerBottomSheet` with playing states, audio indicator styling, and automatic stop on sheet dismissal.

---

## 2. Implementation Phases

| Phase | Phase Name | Status | Single Verification Test File |
|---|---|---|---|
| **01** | [Voice Sample Assets & Manager](./phase-01-voice-sample-assets-and-manager.md) | ✅ Completed | `app/src/test/java/skul9x/example/makesound/Phase1VoiceSampleManagerTest.kt` |
| **02** | [Independent Voice Sample Player Subsystem](./phase-02-voice-sample-player-subsystem.md) | ✅ Completed | `app/src/test/java/skul9x/example/makesound/Phase2VoiceSamplePlayerTest.kt` |
| **03** | [Voice Picker UI Preview Integration](./phase-03-voice-picker-ui-preview-integration.md) | ✅ Completed | `app/src/test/java/skul9x/example/makesound/Phase3VoicePickerUiPreviewTest.kt` |

---

## 3. Workflow Protocol

- All phase documentation files are written in English in the `plans/260917-voice-sample-sound-preview/` directory.
- Each phase defines **exactly one** comprehensive file-based test for verification.
- After implementing each phase, only that single test will be executed for verification.
- Once verified, execution will stop for user review before proceeding to the next phase.
