package skul9x.example.makesound

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import skul9x.example.makesound.player.AudioPlayerManager
import skul9x.example.makesound.player.MediaPlayerAdapter
import skul9x.example.makesound.player.PlaybackStatus
import skul9x.example.makesound.player.PlayerState
import skul9x.example.makesound.ui.MakeAiSoundViewModel
import skul9x.example.makesound.ui.StudioUiState
import java.io.File

/**
 * Single comprehensive verification test for Phase 02: Main Screen UI Cleanup & Cursor-Aware Emotion Insertion.
 *
 * Core functionality verified:
 * 1. Emotion tag insertion at empty text sets cursor immediately after tag.
 * 2. Emotion tag insertion at mid-sentence cursor position preserves preceding/following text and sets cursor position.
 * 3. Emotion tag insertion with a selected text range replaces selection and sets cursor immediately after inserted tag.
 * 4. Tag formatting handles both raw tags (e.g. "thở dài" -> "[thở dài] ") and bracketed tags (e.g. "[hắng giọng]" -> "[hắng giọng] ").
 * 5. TextFieldValue editing, selection clipping on limit, character count, and clipboard integration.
 * 6. UI state and Audio studio layout properties verification without Share button callback dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase2UiCleanupAndCursorUxTest {

    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 30000
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null
        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) { storedDataSource = path }
        override fun prepare() { isPrepared = true }
        override fun start() { isPlayingInternal = true }
        override fun pause() { isPlayingInternal = false }
        override fun stop() { isPlayingInternal = false }
        override fun seekTo(msec: Int) { currentPositionInternal = msec.coerceIn(0, durationInternal) }
        override fun isPlaying(): Boolean = isPlayingInternal
        override fun getCurrentPosition(): Int = currentPositionInternal
        override fun getDuration(): Int = durationInternal
        override fun reset() {
            isPlayingInternal = false
            currentPositionInternal = 0
            isPrepared = false
            storedDataSource = null
        }
        override fun release() {
            reset()
            isReleased = true
        }
        override fun setOnCompletionListener(listener: (() -> Unit)?) { completionCallback = listener }
        override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?) { errorCallback = listener }
    }

    @Test
    fun verifyPhase2UiCleanupAndCursorUx() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        val testScope = TestScope(testDispatcher)

        val fakeAdapter = FakeMediaPlayerAdapter()
        val playerManager = AudioPlayerManager(coroutineScope = testScope, playerAdapter = fakeAdapter)

        try {
            val viewModel = MakeAiSoundViewModel(
                synthesizer = null,
                playerManager = playerManager,
                ioDispatcher = testDispatcher,
                customScope = testScope
            )

            // =========================================================================
            // 1. Initial State Verification
            // =========================================================================
            var uiState = viewModel.uiState.value
            assertEquals("", uiState.textFieldValue.text)
            assertEquals(TextRange.Zero, uiState.textFieldValue.selection)
            assertEquals("", uiState.text)
            assertEquals(0, uiState.charCount)

            // =========================================================================
            // 2. Emotion Tag Insertion on Empty Text
            // =========================================================================
            viewModel.insertEmotionTag("[cười]")
            uiState = viewModel.uiState.value
            assertEquals("[cười] ", uiState.textFieldValue.text)
            assertEquals(TextRange("[cười] ".length), uiState.textFieldValue.selection)
            assertEquals("[cười] ".length, uiState.charCount)

            // =========================================================================
            // 3. Emotion Tag Insertion at Mid-Sentence Cursor Position
            // =========================================================================
            // Set text: "Hôm nay rất vui." and cursor at index 7 (after "Hôm nay")
            val sentence = "Hôm nay rất vui."
            viewModel.onTextFieldValueChange(
                TextFieldValue(text = sentence, selection = TextRange(7))
            )
            uiState = viewModel.uiState.value
            assertEquals("Hôm nay rất vui.", uiState.textFieldValue.text)
            assertEquals(TextRange(7), uiState.textFieldValue.selection)

            // Insert "[cười]"
            viewModel.insertEmotionTag("[cười]")
            uiState = viewModel.uiState.value
            val expectedMidText = "Hôm nay[cười]  rất vui."
            val expectedMidCursor = 7 + "[cười] ".length
            assertEquals(expectedMidText, uiState.textFieldValue.text)
            assertEquals(TextRange(expectedMidCursor), uiState.textFieldValue.selection)
            assertEquals(expectedMidCursor, uiState.textFieldValue.selection.start)
            assertEquals(expectedMidCursor, uiState.textFieldValue.selection.end)

            // =========================================================================
            // 4. Emotion Tag Insertion with Selection Range Replacement
            // =========================================================================
            // Set text "Hôm nay rất vui." with selection over "rất " (indices 8 to 12)
            viewModel.onTextFieldValueChange(
                TextFieldValue(text = "Hôm nay rất vui.", selection = TextRange(8, 12))
            )
            uiState = viewModel.uiState.value
            assertEquals("Hôm nay rất vui.", uiState.textFieldValue.text)
            assertEquals(TextRange(8, 12), uiState.textFieldValue.selection)

            // Insert tag with auto bracket wrapping: "thở dài" -> "[thở dài] "
            viewModel.insertEmotionTag("thở dài")
            uiState = viewModel.uiState.value
            val expectedReplacedText = "Hôm nay [thở dài] vui."
            val expectedReplacedCursor = 8 + "[thở dài] ".length
            assertEquals(expectedReplacedText, uiState.textFieldValue.text)
            assertEquals(TextRange(expectedReplacedCursor), uiState.textFieldValue.selection)

            // =========================================================================
            // 5. Successive Tag Insertions & Formatting
            // =========================================================================
            viewModel.insertEmotionTag("hắng giọng")
            uiState = viewModel.uiState.value
            val expectedSuccessive = "Hôm nay [thở dài] [hắng giọng] vui."
            val expectedSuccessiveCursor = expectedReplacedCursor + "[hắng giọng] ".length
            assertEquals(expectedSuccessive, uiState.textFieldValue.text)
            assertEquals(TextRange(expectedSuccessiveCursor), uiState.textFieldValue.selection)

            // =========================================================================
            // 6. Text Manipulation & Clipboard Paste UX
            // =========================================================================
            viewModel.onClearText()
            uiState = viewModel.uiState.value
            assertEquals("", uiState.textFieldValue.text)
            assertEquals(0, uiState.charCount)

            viewModel.onPasteFromClipboard("VieNeu TTS Studio")
            uiState = viewModel.uiState.value
            assertEquals("VieNeu TTS Studio", uiState.textFieldValue.text)
            assertEquals(TextRange("VieNeu TTS Studio".length), uiState.textFieldValue.selection)

            // Paste at mid-position
            viewModel.onTextFieldValueChange(
                TextFieldValue(text = "VieNeu Studio", selection = TextRange(7)) // after "VieNeu "
            )
            viewModel.onPasteFromClipboard("AI ")
            uiState = viewModel.uiState.value
            assertEquals("VieNeu AI Studio", uiState.textFieldValue.text)
            assertEquals(TextRange(10), uiState.textFieldValue.selection)

            // =========================================================================
            // 7. Max Character Limit (5,000 chars) & Selection Bounds
            // =========================================================================
            val longText = "a".repeat(5050)
            viewModel.onTextChanged(longText)
            uiState = viewModel.uiState.value
            assertEquals(5000, uiState.textFieldValue.text.length)
            assertEquals(5000, uiState.charCount)
            assertEquals(TextRange(5000), uiState.textFieldValue.selection)

            // =========================================================================
            // 8. AudioStudioCard & Studio Action Controls (Without Share Button)
            // =========================================================================
            playerManager.load("/storage/emulated/0/preview_test.wav")
            testScheduler.runCurrent()

            val pState = viewModel.uiState.value.playerState
            assertEquals(PlaybackStatus.PREPARED, pState.status)

            viewModel.playAudio()
            testScheduler.runCurrent()
            assertTrue(viewModel.uiState.value.playerState.isPlaying)

            viewModel.replayAudio()
            testScheduler.runCurrent()
            assertEquals(PlaybackStatus.PLAYING, viewModel.uiState.value.playerState.status)

            viewModel.pauseAudio()
            testScheduler.runCurrent()
            assertEquals(PlaybackStatus.PAUSED, viewModel.uiState.value.playerState.status)

            // Verify status notification
            viewModel.setStatusMessage("💾 Đã lưu vào Thư viện Nhạc")
            assertEquals("💾 Đã lưu vào Thư viện Nhạc", viewModel.uiState.value.statusMessage)
            viewModel.clearStatusMessage()
            assertEquals(null, viewModel.uiState.value.statusMessage)

        } finally {
            playerManager.release()
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}
