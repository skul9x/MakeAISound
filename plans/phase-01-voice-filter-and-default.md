# Phase 01: Multi-Tier Voice Filter & Default "Ngọc Huyền" Voice

**Status:** ✅ Completed  
**Master Plan:** [Plan Overview](./plan.md)

---

## 1. Objective

Set the application default voice to **"Ngọc Huyền"** (`Nữ · Bắc · Giọng đọc tự nhiên`) and replace the single-flat voice filter with a composable multi-layer filtering system (Region, Gender, and Style) so users can filter voices across multiple dimensions simultaneously (e.g. Region: North + Gender: Female + Style: Storytelling).

---

## 2. Requirements

### Functional Requirements
- **Default Voice:**
  - Set `defaultVoiceName = "Ngọc Huyền"` in [VoicePresets.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/engine/VoicePresets.kt).
  - Update `default_voice: "Ngọc Huyền"` in [voices_v3_turbo.json](file:///d:/skul9x/MakeAiSound/app/src/main/assets/vieneu/voices_v3_turbo.json).
  - Ensure [MakeAiSoundViewModel.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/MakeAiSoundViewModel.kt) initializes `selectedVoice` to "Ngọc Huyền" on startup.
- **Multi-Dimensional Voice Filter Model:**
  - Support independent filter dimensions:
    - **Region:** `ALL` ("Tất cả"), `NORTH` ("Bắc"), `CENTRAL` ("Trung"), `SOUTH` ("Nam").
    - **Gender:** `ALL` ("Tất cả"), `FEMALE` ("Nữ"), `MALE` ("Nam").
    - **Style:** `ALL` ("Tất cả"), `NATURAL` ("Tự nhiên"), `STORY` ("Kể chuyện / Đọc truyện"), `NEWS` ("Tin tức").
  - Filter logic in [StudioUiState.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/StudioUiState.kt) matches `(regionMatch && genderMatch && styleMatch)`.
- **UI Bottom Sheet Filtering:**
  - Update [VoicePickerBottomSheet.kt](file:///d:/skul9x/MakeAiSound/app/src/main/java/skul9x/example/makesound/ui/components/VoicePickerBottomSheet.kt) to display categorized filter chip rows (Vùng miền, Giới tính, Phong cách).
  - Include an active filter counter badge and a "Đặt lại" (Reset) button when any filter is active.

### Non-Functional Requirements
- Filtering must be instantaneous with zero UI stutter.
- Maintain backward compatibility with existing `VoicePreset` properties and display names.

---

## 3. Implementation Steps

1. **Update Default Voice Configuration:**
   - Update `defaultVoiceName` in `VoicePresets.kt` to `"Ngọc Huyền"`.
   - Update `"default_voice": "Ngọc Huyền"` in `assets/vieneu/voices_v3_turbo.json`.
   - Ensure ViewModel loads "Ngọc Huyền" as the initial selected voice.

2. **Refactor Voice Filter State Hierarchy:**
   - Create multi-layer filter enums in `StudioUiState.kt`: `RegionFilter`, `GenderFilter`, `StyleFilter`, or composite `VoiceFilterCriteria`.
   - Implement composable `filteredVoices` getter:
     - Check region matching (`isAll` or matching `preset.regionDisplay` / `preset.region`).
     - Check gender matching (`isAll` or matching `preset.genderDisplay` / `preset.gender`).
     - Check style matching (`isAll` or matching `preset.styleDisplay` / `preset.style`).

3. **Update UI Modal & ViewModel:**
   - Add methods in `MakeAiSoundViewModel.kt`: `setRegionFilter()`, `setGenderFilter()`, `setStyleFilter()`, `resetVoiceFilters()`.
   - Redesign `VoicePickerBottomSheet.kt` layout to present clean, organized chip rows for Region, Gender, and Style with visual selection states.

---

## 4. Files to Create/Modify

- **Modify:**
  - `app/src/main/assets/vieneu/voices_v3_turbo.json`
  - `app/src/main/java/skul9x/example/makesound/engine/VoicePresets.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/StudioUiState.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/MakeAiSoundViewModel.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/components/VoicePickerBottomSheet.kt`
  - `app/src/main/java/skul9x/example/makesound/ui/components/VoiceSelectorCard.kt`
- **Create:**
  - `app/src/test/java/skul9x/example/makesound/Phase1VoiceFilterAndDefaultTest.kt`

---

## 5. Verification Plan (Single Comprehensive Test)

Run the single dedicated unit test:
```bash
./gradlew testDebugUnitTest --tests "skul9x.example.makesound.Phase1VoiceFilterAndDefaultTest"
```

### Test Scope
- Verify `VoicePresets.defaultVoiceName` and JSON default voice resolve to `"Ngọc Huyền"`.
- Verify "Ngọc Huyền" properties: Gender = "Nữ", Region = "Bắc", Style = "tu_nhien".
- Verify multi-layer filtering combinations:
  - North + Female -> Returns only Northern Females (e.g. Trúc Ly, Ngọc Linh, Đoan Trang, Mai Anh, Quỳnh Anh, Ngọc Huyền; excludes Southern Females like Thục Đoan, Thùy Dung, etc.).
  - North + Female + Story -> Returns only Northern Female storytellers (Ngọc Linh, Quỳnh Anh).
  - South + Male + Story -> Returns only Southern Male storytellers (Thái Sơn, Đức Trí).
- Verify filter reset returns all 20 voices.
