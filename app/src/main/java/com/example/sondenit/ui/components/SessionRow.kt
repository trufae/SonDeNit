package com.example.sondenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sondenit.data.SleepPhase
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.NightSurfaceHigh
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import com.example.sondenit.util.formatDateLong
import com.example.sondenit.util.formatDurationShort
import com.example.sondenit.util.formatTimeOfDay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SessionRow(
    session: SleepSession,
    durationMs: Long?,
    qualityScore: Int?,
    phaseDurations: Map<SleepPhase, Long>?,
    isActive: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = if (isActive || isSelected) NightSurfaceHigh else NightSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MoonGlow.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PhasePieIcon(phaseDurations)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.displayName,
                    color = OnNight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(2.dp))
                val date = formatDateLong(session.startedAt)
                val timeRange = if (session.endedAt != null) {
                    "${formatTimeOfDay(session.startedAt)} – ${formatTimeOfDay(session.endedAt)}"
                } else {
                    formatTimeOfDay(session.startedAt)
                }
                Text(
                    text = "$date · $timeRange",
                    color = OnNightMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (durationMs != null) {
                    Text(
                        text = formatDurationShort(durationMs),
                        color = OnNightMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val notes = session.notes.trim()
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = notes,
                        color = OnNight,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isActive) {
                LiveDot()
            } else if (qualityScore != null) {
                QualityBadge(qualityScore)
            }
        }
    }
}

@Composable
private fun PhasePieIcon(phaseDurations: Map<SleepPhase, Long>?) {
    val slices = listOf(
        phaseDurations.orEmpty()[SleepPhase.DEEP].orZero() to Lavender,
        phaseDurations.orEmpty()[SleepPhase.REM].orZero() to MoonGlow,
        phaseDurations.orEmpty()[SleepPhase.LIGHT].orZero() to SkyTeal,
        phaseDurations.orEmpty()[SleepPhase.AWAKE].orZero() to PinkDawn,
    )
    val total = slices.sumOf { it.first }.coerceAtLeast(0L)

    Canvas(modifier = Modifier.size(36.dp)) {
        drawCircle(color = Color.White.copy(alpha = 0.08f))
        if (total <= 0L) {
            return@Canvas
        }

        var startAngle = -90f
        slices.forEach { (duration, color) ->
            if (duration <= 0L) return@forEach
            val sweep = duration.toFloat() / total.toFloat() * 360f
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
            )
            startAngle += sweep
        }
        drawCircle(color = Color.White.copy(alpha = 0.14f), radius = 1.5.dp.toPx())
    }
}

private fun Long?.orZero(): Long = this ?: 0L

@Composable
private fun QualityBadge(score: Int) {
    val (color, label) = qualityColorAndLabel(score)
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LiveDot() {
    Surface(
        color = Color(0xFFFF6B6B).copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B))
            )
            Text(
                text = "EN CURS",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun qualityColorAndLabel(score: Int): Pair<Color, String> = when {
    score >= 70 -> Color(0xFF7DD9C8) to "Bona"
    score >= 45 -> Color(0xFFF6C97A) to "Acceptable"
    else -> Color(0xFFFF99B6) to "Pobra"
}
