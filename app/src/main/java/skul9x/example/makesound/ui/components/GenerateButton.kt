package skul9x.example.makesound.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import skul9x.example.makesound.ui.GenerationState
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.ErrorRed
import skul9x.example.makesound.ui.theme.MidnightBlack
import skul9x.example.makesound.ui.theme.NeonPurple
import skul9x.example.makesound.ui.theme.NeonViolet
import skul9x.example.makesound.ui.theme.SurfaceBorder
import skul9x.example.makesound.ui.theme.SurfaceElevated
import skul9x.example.makesound.ui.theme.TextDisabled
import skul9x.example.makesound.ui.theme.TextMuted
import skul9x.example.makesound.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun GenerateButton(
    generationState: GenerationState,
    canGenerate: Boolean,
    onGenerateClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGenerating = generationState is GenerationState.Generating

    Column(modifier = modifier.fillMaxWidth()) {
        if (!isGenerating) {
            // Idle State: Gradient Primary Action Button
            val gradientBrush = if (canGenerate) {
                Brush.horizontalGradient(listOf(ElectricCyan, NeonViolet, NeonPurple))
            } else {
                Brush.horizontalGradient(listOf(SurfaceElevated, SurfaceElevated))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(gradientBrush)
                    .then(
                        if (canGenerate) {
                            Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { onGenerateClick() }
                        } else {
                            Modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (canGenerate) MidnightBlack else TextDisabled,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✨ Tạo âm thanh (WAV 48kHz)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canGenerate) MidnightBlack else TextDisabled
                    )
                }
            }
        } else {
            // Generating State: Progress Bar & Cancellation Action
            val gen = generationState as GenerationState.Generating
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Đang tổng hợp: Chunk ${gen.chunkIndex}/${gen.totalChunks} (${String.format(Locale.US, "%.0f", gen.percent)}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan
                            )
                        }

                        // Cancel Button
                        IconButton(
                            onClick = onCancelClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hủy tạo",
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (gen.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ElectricCyan,
                        trackColor = MidnightBlack
                    )

                    if (gen.currentMetrics != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "RTF: ${String.format(Locale.US, "%.3fx", gen.currentMetrics.rtf)} | Codec: ${gen.currentMetrics.codecDurationMs}ms | RAM: ${String.format(Locale.US, "%.1f MB", gen.currentMetrics.peakMemoryMb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
