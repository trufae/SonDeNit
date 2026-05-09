package com.example.sondenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

data class PieSlice(val label: String, val value: Float, val color: Color)

@Composable
fun PieChart(
    slices: List<PieSlice>,
    centerLabel: String? = null,
    centerSubLabel: String? = null,
    modifier: Modifier = Modifier,
    strokeWidthDp: Int = 28,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val stroke = strokeWidthDp.dp.toPx()
            val pad = stroke / 2f + 4f
            val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
            val topLeft = Offset(pad, pad)
            // Background ring (subtle).
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            var start = -90f
            for (slice in slices) {
                if (slice.value <= 0f) continue
                val sweep = (slice.value / total) * 360f
                drawArc(
                    color = slice.color,
                    startAngle = start + 1f, // tiny gap between slices
                    sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerLabel != null) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (centerSubLabel != null) {
                Text(
                    text = centerSubLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PieLegend(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = slice.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "%.0f%%".format(slice.value * 100f / slices.sumOf { it.value.toDouble() }
                        .toFloat().coerceAtLeast(0.0001f)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun PieChartWithLegend(
    slices: List<PieSlice>,
    centerLabel: String? = null,
    centerSubLabel: String? = null,
    maxChartSize: androidx.compose.ui.unit.Dp = 280.dp,
    legendBelow: Boolean = true,
) {
    if (legendBelow) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxChartSize)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PieChart(
                    slices = slices,
                    centerLabel = centerLabel,
                    centerSubLabel = centerSubLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            PieLegend(slices, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxChartSize)
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PieChart(
                    slices = slices,
                    centerLabel = centerLabel,
                    centerSubLabel = centerSubLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(16.dp))
            PieLegend(slices, Modifier.weight(1f))
        }
    }
}
