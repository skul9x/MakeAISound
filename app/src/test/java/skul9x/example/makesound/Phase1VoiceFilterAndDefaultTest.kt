package skul9x.example.makesound

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.engine.VoicePresets
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.ui.GenderFilter
import skul9x.example.makesound.ui.MakeAiSoundViewModel
import skul9x.example.makesound.ui.RegionFilter
import skul9x.example.makesound.ui.StyleFilter
import java.io.File

/**
 * Verification test for Phase 01: Multi-Tier Voice Filter & Default "Ngọc Huyền" Voice.
 *
 * Test Scope:
 * 1. Verify VoicePresets.defaultVoiceName and JSON default voice resolve to "Ngọc Huyền".
 * 2. Verify "Ngọc Huyền" properties: Gender = "Nữ", Region = "Bắc", Style = "tu_nhien".
 * 3. Verify ViewModel initialization loads "Ngọc Huyền" as the default voice.
 * 4. Verify multi-layer filtering combinations:
 *    - North + Female -> Returns only Northern Females (Trúc Ly, Ngọc Linh, Đoan Trang, Mai Anh, Quỳnh Anh, Ngọc Huyền; excludes Southern/Central Females).
 *    - North + Female + Story -> Returns only Northern Female storytellers (Ngọc Linh, Quỳnh Anh).
 *    - South + Male + Story -> Returns only Southern Male storytellers (Thái Sơn, Đức Trí).
 *    - Central + Male -> Returns only Central Male (Quang Sơn).
 *    - Central + Female -> Returns only Central Female (Ngọc Trân).
 * 5. Verify filter reset and active filter counter badge properties.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase1VoiceFilterAndDefaultTest {

    @Test
    fun verifyPhase1VoiceFilterAndDefault() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        val testScope = TestScope(testDispatcher)
        val playerManager = AudioPlayerManager(coroutineScope = testScope)

        try {
            // =========================================================================
            // 1. JSON Asset & VoicePresets Default Voice Verification
            // =========================================================================
            val jsonFile = File("src/main/assets/vieneu/voices_v3_turbo.json").takeIf { it.exists() }
                ?: File("app/src/main/assets/vieneu/voices_v3_turbo.json")
            assertTrue("voices_v3_turbo.json must exist in assets", jsonFile.exists())

            VoicePresets.loadFromInputStream(jsonFile.inputStream())

            assertEquals("Default voice in VoicePresets must be Ngọc Huyền", "Ngọc Huyền", VoicePresets.defaultVoiceName)

            val ngocHuyen = VoicePresets.getVoice("Ngọc Huyền")
            assertNotNull("Voice preset 'Ngọc Huyền' must exist", ngocHuyen)
            assertEquals("Ngọc Huyền", ngocHuyen?.name)
            assertEquals("female", ngocHuyen?.gender)
            assertEquals("Nữ", ngocHuyen?.genderDisplay)
            assertEquals("Bắc", ngocHuyen?.region)
            assertEquals("Giọng Miền Bắc", ngocHuyen?.regionDisplay)
            assertEquals("tu_nhien", ngocHuyen?.style)
            assertEquals("Nữ · Bắc · Giọng đọc tự nhiên", ngocHuyen?.description)

            val allVoices = VoicePresets.getVoicePresets()
            assertEquals("Total curated preset voices must be 25", 25, allVoices.size)

            // =========================================================================
            // 2. ViewModel Initialization with Default Voice "Ngọc Huyền"
            // =========================================================================
            val viewModel = MakeAiSoundViewModel(
                synthesizer = null,
                playerManager = playerManager,
                ioDispatcher = testDispatcher,
                customScope = testScope
            )

            viewModel.loadVoices(null)
            var uiState = viewModel.uiState.value
            assertEquals("ViewModel available voices count must be 25", 25, uiState.availableVoices.size)
            assertNotNull("ViewModel selectedVoice must not be null", uiState.selectedVoice)
            assertEquals("ViewModel initial selectedVoice must be Ngọc Huyền", "Ngọc Huyền", uiState.selectedVoice?.name)

            // Verify explicit setAvailableVoices preserves/defaults to Ngọc Huyền
            viewModel.setAvailableVoices(allVoices)
            assertEquals("Ngọc Huyền", viewModel.uiState.value.selectedVoice?.name)

            // =========================================================================
            // 3. Multi-Layer Filter Dimensions & Active Filter State
            // =========================================================================
            // Initial state: no filters active
            assertFalse("Initial filter must be inactive", uiState.isVoiceFilterActive)
            assertEquals(0, uiState.activeVoiceFilterCount)
            assertEquals(25, uiState.filteredVoices.size)

            // 3.1 Combination: North + Female
            viewModel.setRegionFilter(RegionFilter.NORTH)
            viewModel.setGenderFilter(GenderFilter.FEMALE)
            viewModel.setStyleFilter(StyleFilter.ALL)

            uiState = viewModel.uiState.value
            assertTrue("Filter should be active", uiState.isVoiceFilterActive)
            assertEquals(2, uiState.activeVoiceFilterCount)

            val northFemaleVoices = uiState.filteredVoices
            val northFemaleNames = northFemaleVoices.map { it.name }.toSet()
            val expectedNorthFemales = setOf("Trúc Ly", "Ngọc Linh", "Đoan Trang", "Mai Anh", "Quỳnh Anh", "Ngọc Huyền")
            assertEquals("North + Female count must be 6", 6, northFemaleVoices.size)
            assertEquals(expectedNorthFemales, northFemaleNames)
            assertTrue("Must include Ngọc Huyền", northFemaleNames.contains("Ngọc Huyền"))
            assertFalse("Must exclude Southern Female Thục Đoan", northFemaleNames.contains("Thục Đoan"))
            assertFalse("Must exclude Southern Female Thùy Dung", northFemaleNames.contains("Thùy Dung"))
            assertFalse("Must exclude Central Female Ngọc Trân", northFemaleNames.contains("Ngọc Trân"))

            // 3.2 Combination: North + Female + Story
            viewModel.setStyleFilter(StyleFilter.STORY)
            uiState = viewModel.uiState.value
            assertEquals(3, uiState.activeVoiceFilterCount)

            val northFemaleStoryVoices = uiState.filteredVoices
            val northFemaleStoryNames = northFemaleStoryVoices.map { it.name }.toSet()
            val expectedNorthFemaleStory = setOf("Ngọc Linh", "Quỳnh Anh")
            assertEquals("North + Female + Story count must be 2", 2, northFemaleStoryVoices.size)
            assertEquals(expectedNorthFemaleStory, northFemaleStoryNames)

            // 3.3 Combination: South + Male + Story
            viewModel.setRegionFilter(RegionFilter.SOUTH)
            viewModel.setGenderFilter(GenderFilter.MALE)
            viewModel.setStyleFilter(StyleFilter.STORY)

            uiState = viewModel.uiState.value
            val southMaleStoryVoices = uiState.filteredVoices
            val southMaleStoryNames = southMaleStoryVoices.map { it.name }.toSet()
            val expectedSouthMaleStory = setOf("Thái Sơn", "Đức Trí")
            assertEquals("South + Male + Story count must be 2", 2, southMaleStoryVoices.size)
            assertEquals(expectedSouthMaleStory, southMaleStoryNames)

            // 3.4 Combination: Central + Male (Any style)
            viewModel.setRegionFilter(RegionFilter.CENTRAL)
            viewModel.setGenderFilter(GenderFilter.MALE)
            viewModel.setStyleFilter(StyleFilter.ALL)

            uiState = viewModel.uiState.value
            val centralMaleVoices = uiState.filteredVoices
            assertEquals(1, centralMaleVoices.size)
            assertEquals("Quang Sơn", centralMaleVoices[0].name)

            // 3.5 Combination: Central + Female (Any style)
            viewModel.setRegionFilter(RegionFilter.CENTRAL)
            viewModel.setGenderFilter(GenderFilter.FEMALE)
            viewModel.setStyleFilter(StyleFilter.ALL)

            uiState = viewModel.uiState.value
            val centralFemaleVoices = uiState.filteredVoices
            assertEquals(1, centralFemaleVoices.size)
            assertEquals("Ngọc Trân", centralFemaleVoices[0].name)

            // 3.6 Combination: North + Male + News
            viewModel.setRegionFilter(RegionFilter.NORTH)
            viewModel.setGenderFilter(GenderFilter.MALE)
            viewModel.setStyleFilter(StyleFilter.NEWS)

            uiState = viewModel.uiState.value
            val northMaleNewsVoices = uiState.filteredVoices
            assertEquals(1, northMaleNewsVoices.size)
            assertEquals("Minh Đức", northMaleNewsVoices[0].name)

            // =========================================================================
            // 4. Filter Reset Verification
            // =========================================================================
            viewModel.resetVoiceFilters()
            uiState = viewModel.uiState.value
            assertEquals(RegionFilter.ALL, uiState.selectedRegionFilter)
            assertEquals(GenderFilter.ALL, uiState.selectedGenderFilter)
            assertEquals(StyleFilter.ALL, uiState.selectedStyleFilter)
            assertFalse("Filter must be inactive after reset", uiState.isVoiceFilterActive)
            assertEquals(0, uiState.activeVoiceFilterCount)
            assertEquals("Reset must return all 25 voices", 25, uiState.filteredVoices.size)

        } finally {
            playerManager.release()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}
