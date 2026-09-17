package skul9x.example.makesound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.engine.VoicePresets
import java.io.File

/**
 * Verification test for Phase 01: Voice Catalog Upgrade & MOSS Codec Pad Frame Fix.
 *
 * Verifies:
 * 1. Preset Count: Loading `voices_v3_turbo.json` yields exactly 25 voices.
 * 2. New Voices Registered: Asserts presence of "Thiền Tâm Đức", "Minh Quân Pro", "Adam bựa", "Mạnh Dũng", "Anh Khôi".
 * 3. Featured Voices Sorting: Exactly 10 featured voices with ranks 1..10, sorted properly in ascending order.
 * 4. Pad Frame Stripping:
 *    - Trailing frame with codebook-0 = 455 is stripped when total frames >= 2.
 *    - Codes ending with non-455 code are preserved intact.
 *    - Single frame arrays (even if codebook-0 = 455) are preserved intact.
 *    - Null codes input returns null safely.
 */
class Phase1VoiceCatalogUpgradeTest {

    @Test
    fun verifyPhase1VoiceCatalogUpgradeAndCodecPadFix() {
        // =========================================================================
        // 1. JSON Asset & Preset Count Verification
        // =========================================================================
        val jsonFile = File("src/main/assets/vieneu/voices_v3_turbo.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/vieneu/voices_v3_turbo.json")
        assertTrue("voices_v3_turbo.json must exist in assets", jsonFile.exists())

        VoicePresets.loadFromInputStream(jsonFile.inputStream())

        val allVoices = VoicePresets.getVoicePresets()
        assertEquals("Preset catalog must contain exactly 25 voices", 25, allVoices.size)
        assertEquals("Default voice must remain Ngọc Huyền", "Ngọc Huyền", VoicePresets.defaultVoiceName)

        // =========================================================================
        // 2. 5 New Voices Presence & Attribute Verification
        // =========================================================================
        val newVoiceNames = listOf(
            "Thiền Tâm Đức",
            "Minh Quân Pro",
            "Adam bựa",
            "Mạnh Dũng",
            "Anh Khôi"
        )

        for (name in newVoiceNames) {
            val voice = VoicePresets.getVoice(name)
            assertNotNull("Voice '$name' must be registered in VoicePresets", voice)
            assertEquals(name, voice?.name)
            assertTrue("Voice '$name' speaker embedding must not be empty", (voice?.speakerEmb?.size ?: 0) > 0)
        }

        // Detailed check on specific new voices
        val thienTamDuc = VoicePresets.getVoice("Thiền Tâm Đức")
        assertNotNull(thienTamDuc)
        assertEquals("doc_truyen", thienTamDuc?.style)
        assertEquals(7, thienTamDuc?.featured)

        val minhQuanPro = VoicePresets.getVoice("Minh Quân Pro")
        assertNotNull(minhQuanPro)
        assertEquals(5, minhQuanPro?.featured)

        val adamBua = VoicePresets.getVoice("Adam bựa")
        assertNotNull(adamBua)
        assertEquals(1, adamBua?.featured)

        val anhKhoi = VoicePresets.getVoice("Anh Khôi")
        assertNotNull(anhKhoi)
        assertEquals(3, anhKhoi?.featured)

        val manhDung = VoicePresets.getVoice("Mạnh Dũng")
        assertNotNull(manhDung)
        assertNull("Mạnh Dũng should not have featured rank", manhDung?.featured)

        // =========================================================================
        // 3. Featured Voices Ranking & Sorting Verification
        // =========================================================================
        val featuredVoices = VoicePresets.getFeaturedVoices()
        assertEquals("There must be exactly 10 featured voices", 10, featuredVoices.size)

        // Check ascending ordering 1..10
        for (i in 0 until 10) {
            val expectedRank = i + 1
            val voice = featuredVoices[i]
            assertEquals(
                "Featured voice at index $i must have rank $expectedRank",
                expectedRank,
                voice.featured
            )
        }

        val expectedFeaturedNames = listOf(
            "Adam bựa",
            "Trúc Ly",
            "Anh Khôi",
            "Mai Anh",
            "Minh Quân Pro",
            "Thùy Dung",
            "Thiền Tâm Đức",
            "Ngọc Huyền",
            "Quang Sơn",
            "Ngọc Trân"
        )
        val actualFeaturedNames = featuredVoices.map { it.name }
        assertEquals("Featured voices order must match expected editor picks", expectedFeaturedNames, actualFeaturedNames)

        // =========================================================================
        // 4. Codec Pad Frame Fix (Issue #198) Unit Tests
        // =========================================================================
        // Case A: Trailing frame has codebook-0 == 455 with >= 2 frames -> Last frame stripped
        val paddedCodes = arrayOf(
            intArrayOf(10, 20, 30),
            intArrayOf(40, 50, 60),
            intArrayOf(455, 12, 99)
        )
        val strippedCodes = VoicePresets.stripEncoderPadFrame(paddedCodes)
        assertNotNull(strippedCodes)
        assertEquals("Stripped codes must have size 2", 2, strippedCodes?.size)
        assertArrayEquals(intArrayOf(10, 20, 30), strippedCodes?.get(0))
        assertArrayEquals(intArrayOf(40, 50, 60), strippedCodes?.get(1))

        // Case B: Trailing frame has normal code (e.g. 482) -> Preserved intact
        val normalCodes = arrayOf(
            intArrayOf(10, 20, 30),
            intArrayOf(482, 12, 99)
        )
        val preservedNormal = VoicePresets.stripEncoderPadFrame(normalCodes)
        assertNotNull(preservedNormal)
        assertEquals(2, preservedNormal?.size)
        assertArrayEquals(intArrayOf(10, 20, 30), preservedNormal?.get(0))
        assertArrayEquals(intArrayOf(482, 12, 99), preservedNormal?.get(1))

        // Case C: Single frame array -> Preserved intact (even if code is 455)
        val singleFrameCodes = arrayOf(
            intArrayOf(455, 12, 99)
        )
        val preservedSingle = VoicePresets.stripEncoderPadFrame(singleFrameCodes)
        assertNotNull(preservedSingle)
        assertEquals(1, preservedSingle?.size)
        assertArrayEquals(intArrayOf(455, 12, 99), preservedSingle?.get(0))

        // Case D: Null codes input -> Returns null safely
        val nullResult = VoicePresets.stripEncoderPadFrame(null)
        assertNull(nullResult)
    }
}
