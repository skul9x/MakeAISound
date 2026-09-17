# Phase 03: Voice Picker UI Preview Integration

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)  
**Verification Test:** `app/src/test/java/skul9x/example/makesound/Phase3VoicePickerUiPreviewTest.kt`

---

## 1. Objective

Integrate the voice sample preview capabilities into the `VoicePickerBottomSheet` user interface:
1. Add an intuitive Play/Stop audition button to each voice card item in the bottom sheet.
2. Coordinate sample audio playback states (`currentVoiceName`, `isPlaying`) so the user gets real-time visual feedback (active indicator, color tint, icon toggle).
3. Ensure strict user interaction boundaries: tapping the preview button only toggles audio sample playback without triggering voice selection or closing the sheet, while tapping the rest of the card selects the voice.
4. Guarantee automatic audio termination whenever the bottom sheet is closed or dismissed.

---

## 2. Requirements

### Functional Requirements
- **UI Components:**
  - Update `VoicePickerItem` in `VoicePickerBottomSheet.kt`:
    - Add a dedicated circular preview button next to the voice title and metadata.
    - Idle state: Outlined/subtle background with `PlayArrow` icon.
    - Playing state: Glowing `ElectricCyan` highlight with `Stop` icon and active visual pulse.
  - Update `VoicePickerBottomSheet` parameters to accept:
    - `previewVoiceName: String? = null`
    - `isPreviewPlaying: Boolean = false`
    - `onTogglePreview: (VoicePreset) -> Unit`
- **ViewModel / State Integration:**
  - Expose preview trigger methods in `MainViewModel` (or dedicated UI state holder):
    - `toggleVoicePreview(voice: VoicePreset)`: Reads audio sample via `VoiceSampleManager` and plays/stops via `VoiceSamplePlayer`.
    - `stopVoicePreview()`: Immediately stops playback on bottom sheet dismissal.
- **Dismissal Safety:**
  - Invoking `onDismiss` automatically stops any active voice sample preview.

### Non-Functional Requirements
- 60/120fps smooth scrolling performance inside `VoicePickerBottomSheet` during playback.
- Adheres strictly to the Studio Dark OLED design language.

---

## 3. Implementation Steps

1. **Update `MainViewModel`:**
   - Inject/instantiate `VoiceSampleManager` and `VoiceSamplePlayer`.
   - Expose `voicePlayerState: StateFlow<VoicePlayerState>`.
   - Provide `toggleVoicePreview(voice: VoicePreset)` and `stopVoicePreview()`.
2. **Update `VoicePickerBottomSheet.kt`:**
   - Add preview button with distinct click target in `VoicePickerItem`.
   - Bind preview state (`previewVoiceName`, `isPreviewPlaying`, `onTogglePreview`).
   - Hook `stopVoicePreview()` into sheet dismissal and voice selection callbacks.
3. **Write Single Verification Test:**
   - Create `app/src/test/java/skul9x/example/makesound/Phase3VoicePickerUiPreviewTest.kt`.
   - Test toggle preview logic (playing a new voice, toggling off the same voice).
   - Test sheet dismissal event triggering player stop.
   - Test selection separation (previewing voice does not alter currently selected preset).

---

## 4. Files to Create/Modify

- `app/src/main/java/skul9x/example/makesound/ui/components/VoicePickerBottomSheet.kt` [MODIFY]
- `app/src/main/java/skul9x/example/makesound/ui/MainViewModel.kt` [MODIFY]
- `app/src/test/java/skul9x/example/makesound/Phase3VoicePickerUiPreviewTest.kt` [NEW]

---

## 5. Test Criteria

- Exactly one unit test file: `Phase3VoicePickerUiPreviewTest.kt`.
- Must verify:
  - Preview state transitions when toggling audio for different voices.
  - Sheet dismissal cleanly invoking `stopVoicePreview()`.
  - Preview operations remaining isolated from active `selectedVoice` state.
