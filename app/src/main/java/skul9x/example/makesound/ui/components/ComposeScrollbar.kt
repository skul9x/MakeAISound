package skul9x.example.makesound.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.ScrollbarTrack

/**
 * Custom Compose scrollbar modifier with neon-accented indicator and smooth fade-in/out.
 * Attached to scrollable Column with [ScrollState].
 */
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 4.dp,
    thumbColor: Color = ElectricCyan,
    trackColor: Color = ScrollbarTrack,
    minThumbHeight: Dp = 32.dp,
    autoHide: Boolean = true
): Modifier = composed {
    val isScrolling = scrollState.isScrollInProgress
    val targetAlpha = if (isScrolling || !autoHide) 1f else 0.25f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    drawWithContent {
        drawContent()

        val viewportHeight = size.height
        val totalHeight = scrollState.maxValue + viewportHeight
        if (totalHeight <= viewportHeight || viewportHeight <= 0f) return@drawWithContent

        val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val rawThumbHeight = (viewportHeight / totalHeight) * viewportHeight
        val minHeightPx = minThumbHeight.toPx()
        val thumbHeight = rawThumbHeight.coerceIn(minHeightPx, viewportHeight)

        val scrollableTrackHeight = viewportHeight - thumbHeight
        val thumbOffsetY = scrollFraction * scrollableTrackHeight

        val widthPx = width.toPx()
        val xOffset = size.width - widthPx

        // Draw track
        if (trackColor != Color.Transparent) {
            drawRoundRect(
                color = trackColor.copy(alpha = trackColor.alpha * alpha),
                topLeft = Offset(xOffset, 0f),
                size = Size(widthPx, viewportHeight),
                cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
            )
        }

        // Draw thumb
        drawRoundRect(
            color = thumbColor.copy(alpha = alpha),
            topLeft = Offset(xOffset, thumbOffsetY),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
        )
    }
}

/**
 * Custom Compose scrollbar modifier for [LazyListState].
 */
fun Modifier.verticalScrollbar(
    lazyListState: LazyListState,
    width: Dp = 4.dp,
    thumbColor: Color = ElectricCyan,
    trackColor: Color = ScrollbarTrack,
    minThumbHeight: Dp = 32.dp,
    autoHide: Boolean = true
): Modifier = composed {
    val isScrolling = lazyListState.isScrollInProgress
    val targetAlpha = if (isScrolling || !autoHide) 1f else 0.25f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "LazyScrollbarAlpha"
    )

    drawWithContent {
        drawContent()

        val layoutInfo = lazyListState.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo

        if (totalItemsCount == 0 || visibleItems.isEmpty()) return@drawWithContent

        val viewportHeight = size.height
        val firstVisibleItem = visibleItems.first()
        val lastVisibleItem = visibleItems.last()

        val estimatedItemHeight = viewportHeight / visibleItems.size.toFloat()
        val estimatedTotalHeight = totalItemsCount * estimatedItemHeight

        if (estimatedTotalHeight <= viewportHeight) return@drawWithContent

        val minHeightPx = minThumbHeight.toPx()
        val rawThumbHeight = (viewportHeight / estimatedTotalHeight) * viewportHeight
        val thumbHeight = rawThumbHeight.coerceIn(minHeightPx, viewportHeight)

        val firstItemIndex = firstVisibleItem.index
        val firstItemOffset = firstVisibleItem.offset.toFloat()
        val itemProgress = if (estimatedItemHeight > 0) (-firstItemOffset / estimatedItemHeight).coerceIn(0f, 1f) else 0f
        val exactIndex = firstItemIndex + itemProgress

        val maxIndex = (totalItemsCount - visibleItems.size).coerceAtLeast(1)
        val scrollFraction = (exactIndex / maxIndex.toFloat()).coerceIn(0f, 1f)

        val scrollableTrackHeight = viewportHeight - thumbHeight
        val thumbOffsetY = scrollFraction * scrollableTrackHeight

        val widthPx = width.toPx()
        val xOffset = size.width - widthPx

        // Draw track
        if (trackColor != Color.Transparent) {
            drawRoundRect(
                color = trackColor.copy(alpha = trackColor.alpha * alpha),
                topLeft = Offset(xOffset, 0f),
                size = Size(widthPx, viewportHeight),
                cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
            )
        }

        // Draw thumb
        drawRoundRect(
            color = thumbColor.copy(alpha = alpha),
            topLeft = Offset(xOffset, thumbOffsetY),
            size = Size(widthPx, thumbHeight),
            cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
        )
    }
}
