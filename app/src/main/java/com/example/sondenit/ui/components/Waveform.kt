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
import androidx.compose.ui.graphics.lerp
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import kotlin.math.pow

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
        val stroke = (barWidth * 0.48f).coerceIn(2f, 7f)
        drawLine(
            color = Color.White.copy(alpha = if (capturing) 0.1f else 0.06f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1f,
        )

        for (i in samples.indices) {
            val v = samples[i].coerceIn(0f, 1f)
            val amp = shapedAmplitude(v)
            val barHeight = (h * 0.025f) + amp * (h * 0.84f)
            val x = i * barWidth + barWidth / 2f
            val color = waveformColor(v, accent)
            val alpha = if (capturing) 0.9f else 0.62f
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.28f + v * 0.26f),
                    Lavender.copy(alpha = 0.38f + v * 0.42f),
                    color.copy(alpha = alpha),
                ),
                startY = midY - barHeight / 2f,
                endY = midY + barHeight / 2f,
            )
            if (v > 0.08f) {
                drawLine(
                    color = color.copy(alpha = 0.08f + v * 0.16f),
                    start = Offset(x, midY - barHeight * 0.58f),
                    end = Offset(x, midY + barHeight * 0.58f),
                    strokeWidth = stroke * (2.1f + v),
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                brush = gradient,
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = stroke * (0.72f + v * 0.32f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun shapedAmplitude(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x.pow(0.72f)
}

private fun waveformColor(value: Float, accent: Color): Color {
    val cool = lerp(SkyTeal, Lavender, 0.34f)
    val active = lerp(cool, accent, 0.42f)
    val warm = lerp(active, MoonGlow, (value * 1.15f).coerceIn(0f, 1f))
    return lerp(warm, PinkDawn, ((value - 0.68f) / 0.32f).coerceIn(0f, 1f))
}
