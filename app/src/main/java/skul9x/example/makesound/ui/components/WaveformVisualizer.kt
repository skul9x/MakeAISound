package skul9x.example.makesound.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import skul9x.example.makesound.ui.theme.ElectricCyan
import skul9x.example.makesound.ui.theme.NeonViolet
import skul9x.example.makesound.ui.theme.SurfaceBorder
import skul9x.example.makesound.ui.theme.SurfaceElevated

@Composable
fun WaveformVisualizer(
    amplitudes: FloatArray,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = ElectricCyan,
    inactiveColor: Color = SurfaceElevated,
    barSpacing: Float = 4f,
    minBarHeightFraction: Float = 0.08f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val totalBars = amplitudes.size.coerceAtLeast(1)
        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalSpacing = (totalBars - 1) * barSpacing
        val barWidth = ((canvasWidth - totalSpacing) / totalBars).coerceAtLeast(2f)

        for (i in 0 until totalBars) {
            val amplitude = if (i < amplitudes.size) amplitudes[i].coerceIn(0f, 1f) else 0.1f
            val effectiveHeight = (canvasHeight * (minBarHeightFraction + amplitude * (1f - minBarHeightFraction)))
                .coerceIn(minBarHeightFraction * canvasHeight, canvasHeight)

            val x = i * (barWidth + barSpacing)
            val y = (canvasHeight - effectiveHeight) / 2f

            val barFraction = i.toFloat() / totalBars.toFloat()
            val isPlayed = barFraction <= progressFraction

            val color = if (isPlayed) {
                activeColor
            } else {
                inactiveColor
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, effectiveHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
