package skul9x.example.makesound.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import skul9x.example.makesound.ui.components.AudioStudioCard
import skul9x.example.makesound.ui.components.DiagnosticsLogBottomSheet
import skul9x.example.makesound.ui.components.GenerateButton
import skul9x.example.makesound.ui.components.TextStudioCard
import skul9x.example.makesound.ui.components.VoicePickerBottomSheet
import skul9x.example.makesound.ui.components.VoiceSelectorCard
import skul9x.example.makesound.ui.components.verticalScrollbar
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.MidnightBlack
import skul9x.example.makesound.ui.theme.NeonGreen
import skul9x.example.makesound.ui.theme.NeonPurple
import skul9x.example.makesound.ui.theme.NeonViolet
import skul9x.example.makesound.ui.theme.SurfaceBorder
import skul9x.example.makesound.ui.theme.SurfaceCard
import skul9x.example.makesound.ui.theme.SurfaceDark
import skul9x.example.makesound.ui.theme.SurfaceElevated
import skul9x.example.makesound.ui.theme.TextPrimary
import skul9x.example.makesound.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainStudioScreen(
    viewModel: MakeAiSoundViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val voicePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load available voices once on start
    LaunchedEffect(Unit) {
        viewModel.loadVoices(context)
    }

    // Show status messages in Snackbar
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MidnightBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Studio Header Bar
            StudioTopBar(
                onOpenDiagnostics = { viewModel.showDiagnostics(true) }
            )

            // Main Scrollable Area with Custom Neon Scrollbar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(scrollState, width = 5.dp, thumbColor = ElectricCyan)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Text Studio Editor Card
                TextStudioCard(
                    textFieldValue = uiState.textFieldValue,
                    charCount = uiState.charCount,
                    estimatedDurationSec = uiState.estimatedDurationSeconds,
                    onTextFieldValueChange = { viewModel.onTextFieldValueChange(it) },
                    onPasteClick = { viewModel.readClipboardAndPaste(context) },
                    onCopyClick = { viewModel.copyTextToClipboard(context) },
                    onClearClick = { viewModel.onClearText() },
                    onEmotionTagClick = { viewModel.insertEmotionTag(it) }
                )

                // 2. Voice Selector Card
                VoiceSelectorCard(
                    selectedVoice = uiState.selectedVoice,
                    onOpenVoicePicker = { viewModel.showVoicePicker(true) }
                )

                // 3. Generate Audio Primary Button
                GenerateButton(
                    generationState = uiState.generationState,
                    canGenerate = uiState.canGenerate,
                    onGenerateClick = { viewModel.generateAudio(context) },
                    onCancelClick = { viewModel.cancelGeneration() }
                )

                // 4. Audio Player & Studio Card (if audio generated or loaded)
                if (uiState.generatedAudioFile != null || uiState.playerState.isPrepared || uiState.playerState.isPlaying) {
                    AudioStudioCard(
                        playerState = uiState.playerState,
                        waveformAmplitudes = uiState.waveformAmplitudes,
                        audioFile = uiState.generatedAudioFile,
                        onPlayPause = {
                            if (uiState.playerState.isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                        },
                        onReplay = { viewModel.replayAudio() },
                        onSeek = { fraction -> viewModel.seekToFraction(fraction) },
                        onSaveToStorage = { viewModel.exportToStorage(context) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Voice Picker Modal BottomSheet
    if (uiState.showVoicePicker) {
        VoicePickerBottomSheet(
            sheetState = voicePickerSheetState,
            voices = uiState.filteredVoices,
            selectedVoice = uiState.selectedVoice,
            selectedRegionFilter = uiState.selectedRegionFilter,
            selectedGenderFilter = uiState.selectedGenderFilter,
            selectedStyleFilter = uiState.selectedStyleFilter,
            onRegionFilterSelected = { viewModel.setRegionFilter(it) },
            onGenderFilterSelected = { viewModel.setGenderFilter(it) },
            onStyleFilterSelected = { viewModel.setStyleFilter(it) },
            onResetFilters = { viewModel.resetVoiceFilters() },
            onVoiceSelected = { viewModel.onVoiceSelected(it) },
            onDismiss = { viewModel.showVoicePicker(false) }
        )
    }

    // Diagnostics Log Modal BottomSheet
    if (uiState.showDiagnostics) {
        DiagnosticsLogBottomSheet(
            sheetState = diagnosticsSheetState,
            sessionMetrics = uiState.sessionMetrics,
            logs = uiState.filteredLogs,
            logFilter = uiState.logFilter,
            onFilterChange = { viewModel.setLogFilter(it) },
            onCopyLogs = { viewModel.copyLogsToClipboard(context) },
            onClearLogs = { viewModel.clearLogs() },
            onDismiss = { viewModel.showDiagnostics(false) }
        )
    }
}

@Composable
fun StudioTopBar(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Title with Glowing Icon
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricCyan, NeonViolet))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MidnightBlack,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "MakeAiSound Studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "VieNeu Neural TTS · 48kHz WAV",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        }

        // Diagnostics Button
        OutlinedButton(
            onClick = onOpenDiagnostics,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SurfaceCard,
                contentColor = ElectricCyan
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = Brush.horizontalGradient(listOf(ElectricCyan, NeonPurple))
            )
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
