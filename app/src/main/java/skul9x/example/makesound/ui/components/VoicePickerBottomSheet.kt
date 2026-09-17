package skul9x.example.makesound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import skul9x.example.makesound.engine.VoicePreset
import skul9x.example.makesound.ui.GenderFilter
import skul9x.example.makesound.ui.RegionFilter
import skul9x.example.makesound.ui.StyleFilter
import skul9x.example.makesound.ui.VoiceFilter
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.MidnightBlack
import skul9x.example.makesound.ui.theme.NeonPurple
import skul9x.example.makesound.ui.theme.NeonViolet
import skul9x.example.makesound.ui.theme.SurfaceBorder
import skul9x.example.makesound.ui.theme.SurfaceCard
import skul9x.example.makesound.ui.theme.SurfaceDark
import skul9x.example.makesound.ui.theme.SurfaceElevated
import skul9x.example.makesound.ui.theme.TextMuted
import skul9x.example.makesound.ui.theme.TextPrimary
import skul9x.example.makesound.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoicePickerBottomSheet(
    sheetState: SheetState,
    voices: List<VoicePreset>,
    selectedVoice: VoicePreset?,
    selectedRegionFilter: RegionFilter = RegionFilter.ALL,
    selectedGenderFilter: GenderFilter = GenderFilter.ALL,
    selectedStyleFilter: StyleFilter = StyleFilter.ALL,
    onRegionFilterSelected: (RegionFilter) -> Unit = {},
    onGenderFilterSelected: (GenderFilter) -> Unit = {},
    onStyleFilterSelected: (StyleFilter) -> Unit = {},
    onResetFilters: () -> Unit = {},
    selectedFilter: VoiceFilter = VoiceFilter.ALL,
    onFilterSelected: ((VoiceFilter) -> Unit)? = null,
    onVoiceSelected: (VoicePreset) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chọn giọng đọc VieNeu AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isFilterActive = selectedRegionFilter != RegionFilter.ALL ||
                    selectedGenderFilter != GenderFilter.ALL ||
                    selectedStyleFilter != StyleFilter.ALL ||
                    selectedFilter != VoiceFilter.ALL
            val activeFilterCount = (if (selectedRegionFilter != RegionFilter.ALL) 1 else 0) +
                    (if (selectedGenderFilter != GenderFilter.ALL) 1 else 0) +
                    (if (selectedStyleFilter != StyleFilter.ALL) 1 else 0) +
                    (if (selectedFilter != VoiceFilter.ALL && selectedRegionFilter == RegionFilter.ALL && selectedGenderFilter == GenderFilter.ALL && selectedStyleFilter == StyleFilter.ALL) 1 else 0)

            // Filter Section Header with Active Badge & Reset Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bộ lọc giọng đọc",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (activeFilterCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.2f))
                                .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$activeFilterCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                    }
                }

                if (isFilterActive) {
                    Text(
                        text = "Đặt lại",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onResetFilters() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dimension 1: Vùng miền
            Text(
                text = "Vùng miền",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RegionFilter.values().forEach { filter ->
                    val isSelected = filter == selectedRegionFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onRegionFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceCard,
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            labelColor = TextSecondary,
                            selectedLabelColor = ElectricCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SurfaceBorder,
                            selectedBorderColor = ElectricCyan,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dimension 2: Giới tính
            Text(
                text = "Giới tính",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GenderFilter.values().forEach { filter ->
                    val isSelected = filter == selectedGenderFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGenderFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceCard,
                            selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                            labelColor = TextSecondary,
                            selectedLabelColor = NeonPurple
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SurfaceBorder,
                            selectedBorderColor = NeonPurple,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dimension 3: Phong cách
            Text(
                text = "Phong cách",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StyleFilter.values().forEach { filter ->
                    val isSelected = filter == selectedStyleFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStyleFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceCard,
                            selectedContainerColor = NeonViolet.copy(alpha = 0.2f),
                            labelColor = TextSecondary,
                            selectedLabelColor = NeonViolet
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SurfaceBorder,
                            selectedBorderColor = NeonViolet,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voices List with Custom Scrollbar
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .verticalScrollbar(listState, width = 5.dp, thumbColor = ElectricCyan),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (voices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Không tìm thấy giọng đọc phù hợp",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(voices, key = { it.name }) { voice ->
                        val isSelected = voice.name == selectedVoice?.name
                        VoicePickerItem(
                            voice = voice,
                            isSelected = isSelected,
                            onSelect = { onVoiceSelected(voice) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoicePickerItem(
    voice: VoicePreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) ElectricCyan else SurfaceBorder
    val bgColor = if (isSelected) SurfaceElevated else SurfaceCard

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                Brush.linearGradient(listOf(ElectricCyan, NeonViolet))
                            } else {
                                Brush.linearGradient(listOf(SurfaceElevated, SurfaceBorder))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = voice.name.take(1),
                        color = if (isSelected) Color.White else TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) ElectricCyan else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = voice.description.ifEmpty { voice.styleDisplay },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (voice.featured != null) {
                            VoiceTagBadge(
                                text = "⭐ #${voice.featured}",
                                color = ElectricCyan
                            )
                        }
                        if (voice.genderDisplay.isNotEmpty()) {
                            VoiceTagBadge(
                                text = voice.genderDisplay,
                                color = if (voice.genderDisplay == "Nữ") NeonPurple else ElectricCyan
                            )
                        }
                        if (voice.regionDisplay.isNotEmpty()) {
                            VoiceTagBadge(
                                text = voice.regionDisplay,
                                color = NeonViolet
                            )
                        }
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Đang chọn",
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
