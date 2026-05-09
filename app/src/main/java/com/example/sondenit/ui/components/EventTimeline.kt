package com.example.sondenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SoundClass
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import com.example.sondenit.util.formatDurationShort
import com.example.sondenit.util.formatTimeOfDayLong

data class TimelineRowSpec(
    val event: SessionEvent,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val accent: Color,
    val playable: Boolean = false,
    val playing: Boolean = false,
)

fun describe(event: SessionEvent, contextLabels: TimelineLabels): TimelineRowSpec = when (event) {
    is SessionEvent.SessionStart -> TimelineRowSpec(
        event, contextLabels.sessionStart, null,
        Icons.Filled.NightsStay, MoonGlow,
    )
    is SessionEvent.SessionEnd -> TimelineRowSpec(
        event, contextLabels.sessionEnd, null,
        Icons.Filled.WbSunny, MoonGlow,
    )
    is SessionEvent.Pause -> TimelineRowSpec(
        event, contextLabels.pause, null,
        Icons.Filled.PauseCircle, OnNightMuted,
    )
    is SessionEvent.Resume -> TimelineRowSpec(
        event, contextLabels.resume, null,
        Icons.Filled.PlayCircle, SkyTeal,
    )
    is SessionEvent.ScreenOn -> TimelineRowSpec(
        event, contextLabels.screenOn, null,
        Icons.Filled.PhoneAndroid, PinkDawn,
    )
    is SessionEvent.ScreenOff -> TimelineRowSpec(
        event, contextLabels.screenOff, null,
        Icons.Filled.PhoneAndroid, OnNightMuted,
    )
    is SessionEvent.AudioChunk -> {
        val (label, color) = describeSound(event.classification)
        TimelineRowSpec(
            event = event,
            title = label,
            subtitle = "${formatDurationShort(event.durationMs)} · " +
                "%.0f dB sobre ambient".format((event.peakDb - event.ambientDb).coerceAtLeast(0f)),
            icon = if (event.classification == SoundClass.SPEECH) Icons.Filled.RecordVoiceOver
                   else Icons.Filled.GraphicEq,
            accent = color,
            playable = true,
        )
    }
}

data class TimelineLabels(
    val sessionStart: String,
    val sessionEnd: String,
    val pause: String,
    val resume: String,
    val screenOn: String,
    val screenOff: String,
)

private fun describeSound(klass: SoundClass): Pair<String, Color> = when (klass) {
    SoundClass.SPEECH -> "Veu detectada" to Lavender
    SoundClass.COUGH -> "Possible tos" to PinkDawn
    SoundClass.MOVEMENT -> "Moviment al llit" to SkyTeal
    SoundClass.SNORE -> "Roncs / respiració" to MoonGlow
    SoundClass.NOISE -> "Soroll" to OnNightMuted
    SoundClass.UNKNOWN -> "So lleu" to OnNightMuted
}

@Composable
fun TimelineRow(
    spec: TimelineRowSpec,
    showLineAbove: Boolean,
    showLineBelow: Boolean,
    onPlay: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Connector top
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(10.dp)
                    .background(if (showLineAbove) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(spec.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Connector bottom (fills remaining height)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (showLineBelow) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatTimeOfDayLong(spec.event.timestamp),
                        color = OnNightMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = spec.title,
                        color = OnNight,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    spec.subtitle?.let {
                        Text(
                            text = it,
                            color = OnNightMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (spec.playable && onPlay != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(spec.accent.copy(alpha = 0.20f))
                            .clickable {
                                if (spec.playing) onStop?.invoke() else onPlay()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (spec.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = spec.accent,
                        )
                    }
                }
            }
        }
    }
}

