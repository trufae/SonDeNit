package com.example.sondenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.audio.NoiseGroup
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.util.formatTimeOfDay

/**
 * Horizontal "tape" that condenses an entire night into one bar. The full
 * width corresponds to the recording's total span. Audio groups appear as
 * coloured blocks (dominant class colour, width proportional to span);
 * screen-on events appear as thin vertical markers; paused windows are
 * shown with a darker overlay.
 */
@Composable
fun EventRibbon(
    sessionStart: Long,
    sessionEnd: Long,
    groups: List<NoiseGroup>,
    screenOnTimestamps: List<Long>,
    pausedRanges: List<LongRange>,
    modifier: Modifier = Modifier,
    title: String,
) {
    val total = (sessionEnd - sessionStart).coerceAtLeast(1L)

    Column(modifier = modifier) {
        Text(
            text = title,
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val radius = CornerRadius(8f, 8f)
                drawRoundRect(
                    color = NightSurface,
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = radius,
                )
                // Paused ranges: dim overlay.
                pausedRanges.forEach { range ->
                    val a = ((range.first - sessionStart).coerceIn(0, total)).toFloat() / total * w
                    val b = ((range.last - sessionStart).coerceIn(0, total)).toFloat() / total * w
                    if (b > a) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(a, 0f),
                            size = Size(b - a, h),
                        )
                    }
                }
                // Audio groups.
                groups.forEach { g ->
                    val a = ((g.startTimestamp - sessionStart).coerceIn(0, total)).toFloat() / total * w
                    val b = ((g.endTimestamp - sessionStart).coerceIn(0, total)).toFloat() / total * w
                    val width = (b - a).coerceAtLeast(2.5f)
                    val color = colorForClass(g.dominantClass)
                    drawRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(a, 4f),
                        size = Size(width, h - 8f),
                    )
                }
                // Screen-on markers (drawn on top so they're always visible).
                screenOnTimestamps.forEach { ts ->
                    val x = ((ts - sessionStart).coerceIn(0, total)).toFloat() / total * w
                    drawLine(
                        color = PinkDawn,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.5f,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTimeOfDay(sessionStart),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTimeOfDay(sessionEnd),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
