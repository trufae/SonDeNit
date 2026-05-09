package com.example.sondenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.NightSurfaceHigh
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.util.formatDateLong
import com.example.sondenit.util.formatDurationShort
import com.example.sondenit.util.formatTimeOfDay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SessionRow(
    session: SleepSession,
    durationMs: Long?,
    qualityScore: Int?,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = if (isActive) NightSurfaceHigh else NightSurface,
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
                    .background(MoonGlow.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MoonGlow,
                )
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
