package com.example.sondenit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.SkyTeal

/**
 * Live oscilloscope-style waveform driven by the rolling RMS samples
 * published by [RecordingController]. Values are normalised in 0..1.
 */
@Composable
fun Waveform(
    samples: FloatArray,
    capturing: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(
        if (capturing) MoonGlow else SkyTeal,
        label = "waveform-accent",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        if (samples.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val barWidth = (w / samples.size).coerceAtLeast(1f)
        val gradient = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = 0.6f), Lavender.copy(alpha = 0.9f), accent.copy(alpha = 0.6f))
        )
        for (i in samples.indices) {
            val v = samples[i].coerceIn(0f, 1f)
            // Boost mid-range so quiet ambient is still visible but not noisy.
            val amp = (v * 1.4f - 0.05f).coerceIn(0f, 1f)
            val barHeight = amp * (h * 0.9f)
            val x = i * barWidth + barWidth / 2f
            drawLine(
                brush = gradient,
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = (barWidth * 0.5f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
        // Subtle baseline.
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1f,
        )
    }
}
