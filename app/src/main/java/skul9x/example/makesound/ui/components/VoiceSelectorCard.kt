package skul9x.example.makesound.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.NeonPurple
import skul9x.example.makesound.ui.theme.NeonViolet
import skul9x.example.makesound.ui.theme.SurfaceBorder
import skul9x.example.makesound.ui.theme.SurfaceCard
import skul9x.example.makesound.ui.theme.SurfaceElevated
import skul9x.example.makesound.ui.theme.TextMuted
import skul9x.example.makesound.ui.theme.TextPrimary
import skul9x.example.makesound.ui.theme.TextSecondary

@Composable
fun VoiceSelectorCard(
    selectedVoice: VoicePreset?,
    onOpenVoicePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable { onOpenVoicePicker() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Avatar + Name + Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar Circle with Neon Gradient
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCyan, NeonViolet)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = selectedVoice?.name ?: "Chọn giọng đọc AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = selectedVoice?.styleDisplay ?: selectedVoice?.description ?: "VieNeu-TTS Neural Voice",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Change Voice Action Button
                OutlinedButton(
                    onClick = onOpenVoicePicker,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceElevated,
                        contentColor = ElectricCyan
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.horizontalGradient(listOf(ElectricCyan, NeonPurple))
                    )
                ) {
                    Text(
                        text = "Đổi giọng",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Tags Row (Gender, Region, Quality)
            if (selectedVoice != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedVoice.featured != null) {
                        VoiceTagBadge(
                            text = "⭐ #${selectedVoice.featured}",
                            color = ElectricCyan
                        )
                    }

                    if (selectedVoice.genderDisplay.isNotEmpty()) {
                        VoiceTagBadge(
                            text = selectedVoice.genderDisplay,
                            color = if (selectedVoice.genderDisplay == "Nữ") NeonPurple else ElectricCyan
                        )
                    }

                    if (selectedVoice.regionDisplay.isNotEmpty()) {
                        VoiceTagBadge(
                            text = selectedVoice.regionDisplay,
                            color = NeonViolet
                        )
                    }

                    VoiceTagBadge(
                        text = "48kHz WAV",
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceTagBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (color == TextMuted) TextSecondary else color
        )
    }
}
