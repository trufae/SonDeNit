package com.example.sondenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import androidx.compose.foundation.layout.IntrinsicSize
import com.example.sondenit.audio.NoiseGroup
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
    val countBadge: Int? = null,
    val favorite: Boolean = false,
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
    is SessionEvent.Motion -> TimelineRowSpec(
        event = event,
        title = if (event.wakeEvent) contextLabels.phoneMoved else contextLabels.movement,
        subtitle = "${formatDurationShort(event.durationMs)} · " +
            "%.2f m/s2 · %.0f°".format(
                event.peakAcceleration,
                event.orientationChangeDeg,
            ),
        icon = Icons.Filled.Vibration,
        accent = if (event.wakeEvent) PinkDawn else SkyTeal,
        favorite = event.favorite,
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
            favorite = event.favorite,
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
    val movement: String,
    val phoneMoved: String,
)

fun describeGroup(group: NoiseGroup): TimelineRowSpec {
    val (label, color) = describeSound(group.dominantClass)
    val first = group.chunks.first()
    val countSuffix = if (group.chunks.size > 1) " · ${group.chunks.size}" else ""
    return TimelineRowSpec(
        event = first,
        title = label + countSuffix,
        subtitle = "${com.example.sondenit.util.formatDurationShort(group.totalDurationMs)} · " +
            "%.0f dB sobre ambient".format(
                (group.peakDb - group.ambientDb).coerceAtLeast(0f)
            ),
        icon = if (group.dominantClass == SoundClass.SPEECH)
            androidx.compose.material.icons.Icons.Filled.RecordVoiceOver
        else androidx.compose.material.icons.Icons.Filled.GraphicEq,
        accent = color,
        playable = true,
        countBadge = if (group.chunks.size > 1) group.chunks.size else null,
        favorite = group.chunks.any { it.favorite },
    )
}

fun describeSound(klass: SoundClass): Pair<String, Color> = when (klass) {
    SoundClass.SPEECH -> "Veu detectada" to Lavender
    SoundClass.COUGH -> "Possible tos" to PinkDawn
    SoundClass.MOVEMENT -> "Moviment al llit" to SkyTeal
    SoundClass.SNORE -> "Roncs / respiració" to MoonGlow
    SoundClass.DOG_BARK -> "Lladruc de gos" to PinkDawn
    SoundClass.CAT_MEOW -> "Miol de gat" to Lavender
    SoundClass.ESBUFEGAR -> "Respiració forta" to PinkDawn
    SoundClass.APNEA_GASP -> "Recuperació d'apnea" to PinkDawn
    SoundClass.NOISE -> "Soroll" to OnNightMuted
    SoundClass.UNKNOWN -> "So lleu" to OnNightMuted
}

fun colorForClass(klass: SoundClass): Color = describeSound(klass).second

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineRow(
    spec: TimelineRowSpec,
    showLineAbove: Boolean,
    showLineBelow: Boolean,
    selected: Boolean = false,
    onPlay: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
            Box(contentAlignment = Alignment.TopEnd) {
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
                spec.countBadge?.let { n ->
                    Surface(
                        color = spec.accent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(start = 2.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (n > 99) "99+" else n.toString(),
                                color = Color(0xFF0B0E25),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
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
        val surfaceModifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .let { base ->
                if (onLongClick == null) {
                    base
                } else {
                    base.combinedClickable(
                        role = Role.Button,
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                }
            }
        Surface(
            modifier = surfaceModifier,
            color = if (spec.playing || selected) spec.accent.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface,
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
                    if (spec.favorite) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MoonGlow,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.favorite_badge),
                                color = MoonGlow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
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
