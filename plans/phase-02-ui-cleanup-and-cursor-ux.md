# Phase 02: Main Screen UI Cleanup & Cursor-Aware Emotion Insertion

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Previous Phase:** [Phase 01 — Voice Filter & Default Voice](./phase-01-voice-filter-and-default.md)

---

## 1. Objective

Clean up the main screen audio playback card by removing the redundant "Share" (Chia sẻ) button, and upgrade the text editing state to support cursor-aware emotion tag insertion so that tapping an emotion helper chip automatically inserts the tag at the cursor position and positions the cursor immediately after the inserted tag.

---

## 2. Requirements

### Functional Requirements
- **Remove Share Button from Main Screen:**
  - Remove the "Chia sẻ" (Share) button from [AudioStudioCard.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/components/AudioStudioCard.kt).
  - Audio studio action row will retain only: **Play/Pause**, **Replay**, and **Lưu WAV** (Save to device storage).
  - Remove unnecessary `onShareAudio` callbacks from [MainStudioScreen.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/MainStudioScreen.kt) where applicable.
- **Cursor-Aware Emotion Tag Insertion UX:**
  - In [TextStudioCard.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/components/TextStudioCard.kt) and [MakeAiSoundViewModel.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/MakeAiSoundViewModel.kt), maintain `TextFieldValue` (composed of text content and `TextRange` cursor selection).
  - When the user taps an emotion cue chip (e.g. `[Cười]`, `[Thở dài]`, `[Hắng giọng]`):
    - Insert the formatted tag at the current cursor index (or append at the end if the text is empty).
    - Automatically add a trailing space after the tag if needed.
    - Set the new selection cursor position `selection = TextRange(insertPosition + tagWithSpace.length)` so that the cursor immediately jumps right behind the inserted tag.

### Non-Functional Requirements
- Seamless typing performance with no flickering or cursor jumping during standard text input.
- Keep the dark OLED studio aesthetic consistent across all screen resolutions.

---

## 3. Implementation Steps

1. **Remove Share Button:**
   - In `AudioStudioCard.kt`, remove the `OutlinedButton` for `onShareAudio`.
   - Update action row layout and button alignments for a clean 3-button configuration (Play/Pause, Replay, Save WAV).

2. **Upgrade ViewModel & UI State to Support Cursor Selection:**
   - Add `textFieldValue: TextFieldValue` (or text + selection index) management in `MakeAiSoundViewModel.kt`.
   - Update `insertEmotionTag(tag: String)` logic:
     - Determine cursor start and end from selection.
     - Replace selected range (or insert at cursor) with `"$formatted "`.
     - Update cursor position to `start + formatted.length + 1`.
   - Update `TextStudioCard.kt` to bind `TextFieldValue` to `OutlinedTextField`.

---

## 4. Files to Create/Modify

- **Modify:**
  - `app/src/main/java/skul9x/example/makesound/ui/components/AudioStudioCard.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/components/TextStudioCard.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/MainStudioScreen.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/MakeAiSoundViewModel.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/StudioUiState.kt`
- **Create:**
  - `app/src/test/java/skul9x/example/makesound/Phase2UiCleanupAndCursorUxTest.kt`

---

## 5. Verification Plan (Single Comprehensive Test)

Run the single dedicated unit test:
```bash
./gradlew testDebugUnitTest --tests "skul9x.example.makesound.Phase2UiCleanupAndCursorUxTest"
```

### Test Scope
- Verify `insertEmotionTag` at empty text inserts tag and sets cursor immediately after the tag.
- Verify `insertEmotionTag` at mid-sentence cursor position (e.g., text `"Hôm nay rất vui."`, cursor at index 7) correctly inserts `"[cười] "` and places cursor at `7 + "[cười] ".length`.
- Verify `insertEmotionTag` with selected text range replaces the selection and places the cursor right after the newly inserted tag.
- Verify `AudioStudioCard` state and layout properties without the Share button.
