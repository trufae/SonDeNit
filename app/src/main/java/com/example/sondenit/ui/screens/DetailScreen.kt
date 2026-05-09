package com.example.sondenit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.audio.AudioPlayer
import com.example.sondenit.data.DetectedSignal
import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SessionStats
import com.example.sondenit.data.SleepPhase
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.components.PieChartWithLegend
import com.example.sondenit.ui.components.PieSlice
import com.example.sondenit.ui.components.StatCard
import com.example.sondenit.ui.components.TimelineRow
import com.example.sondenit.ui.components.describe
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import com.example.sondenit.util.formatDateLong
import com.example.sondenit.util.formatDurationShort
import com.example.sondenit.util.formatTimeOfDay
import java.io.File

@Composable
fun DetailScreen(
    repo: SessionRepository,
    session: SleepSession,
    onBack: () -> Unit,
    onRename: (SleepSession, String) -> Unit,
    onDelete: (SleepSession) -> Unit,
) {
    val ctx = LocalContext.current
    val player = remember { AudioPlayer() }
    var playingFile by remember { mutableStateOf<String?>(null) }
    val stats = remember(session.id, session.endedAt) { repo.readStats(session.id) }
    val events = remember(session.id, session.endedAt) { repo.readEvents(session.id) }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable(session.id) { mutableStateOf(session.displayName) }

    DisposableEffectOnDispose { player.stop() }

    val labels = remember { defaultTimelineLabels() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 32.dp, bottom = 24.dp),
        ) {
            item {
                TopBar(
                    title = session.displayName,
                    subtitle = formatDateLong(session.startedAt),
                    onBack = onBack,
                    onRename = { renaming = true },
                    onDelete = { deleting = true },
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                SummaryCard(session = session, stats = stats)
            }
            if (stats != null && stats.sleptDurationMs > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PhasesSection(stats)
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    StatsGrid(stats = stats)
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    SignalsCard(stats = stats)
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.timeline),
                    color = OnNight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            val timelineEvents = events
            items(timelineEvents.size, key = { it }) { idx ->
                val event = timelineEvents[idx]
                val spec = describe(event, labels)
                TimelineRow(
                    spec = spec.copy(playing = event is SessionEvent.AudioChunk && playingFile == event.file),
                    showLineAbove = idx > 0,
                    showLineBelow = idx < timelineEvents.size - 1,
                    onPlay = if (event is SessionEvent.AudioChunk) {
                        {
                            val file = File(repo.sessionDir(session.id), event.file)
                            if (file.exists()) {
                                playingFile = event.file
                                player.play(file) { playingFile = null }
                            }
                        }
                    } else null,
                    onStop = {
                        player.stop()
                        playingFile = null
                    },
                )
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim().ifBlank { session.displayName }
                    onRename(session, trimmed)
                    renaming = false
                }) { Text(stringResource(R.string.rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onDelete(session)
                }) {
                    Text(
                        stringResource(R.string.delete_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message)) },
        )
    }
}

@Composable
private fun DisposableEffectOnDispose(block: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { block() }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back_home),
                tint = OnNight,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = OnNight,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = OnNightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRename) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.rename),
                tint = OnNight,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SummaryCard(session: SleepSession, stats: SessionStats?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = NightSurface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MoonGlow,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.session_summary),
                    color = OnNight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            val sleptMs = stats?.sleptDurationMs ?: 0L
            Text(
                text = formatDurationShort(sleptMs),
                color = OnNight,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.hours_slept),
                color = OnNightMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            val range = if (session.endedAt != null) {
                "${formatTimeOfDay(session.startedAt)} – ${formatTimeOfDay(session.endedAt)}"
            } else {
                formatTimeOfDay(session.startedAt) + " — en curs"
            }
            Text(
                text = range,
                color = OnNightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (stats != null) {
                Spacer(Modifier.height(14.dp))
                QualityBadge(score = stats.qualityScore)
            }
        }
    }
}

@Composable
private fun QualityBadge(score: Int) {
    val (color, label) = qualityColor(score)
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${stringResource(R.string.quality)}: $label · $score",
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun qualityColor(score: Int): Pair<Color, String> = when {
    score >= 70 -> SkyTeal to "Bona"
    score >= 45 -> MoonGlow to "Acceptable"
    else -> PinkDawn to "Pobra"
}

@Composable
private fun PhasesSection(stats: SessionStats) {
    val total = stats.phaseDurations.values.sum().coerceAtLeast(1L)
    val slices = listOf(
        PieSlice(
            stringResource(R.string.phase_deep),
            (stats.phaseDurations[SleepPhase.DEEP] ?: 0L).toFloat(),
            Lavender,
        ),
        PieSlice(
            stringResource(R.string.phase_rem),
            (stats.phaseDurations[SleepPhase.REM] ?: 0L).toFloat(),
            MoonGlow,
        ),
        PieSlice(
            stringResource(R.string.phase_light),
            (stats.phaseDurations[SleepPhase.LIGHT] ?: 0L).toFloat(),
            SkyTeal,
        ),
        PieSlice(
            stringResource(R.string.phase_awake),
            (stats.phaseDurations[SleepPhase.AWAKE] ?: 0L).toFloat(),
            PinkDawn,
        ),
    )
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.phases),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        PieChartWithLegend(
            slices = slices,
            centerLabel = formatDurationShort(stats.sleptDurationMs),
            centerSubLabel = stringResource(R.string.hours_slept),
        )
    }
}

@Composable
private fun StatsGrid(stats: SessionStats) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.NotificationsActive,
                accent = PinkDawn,
                label = stringResource(R.string.interruptions),
                value = stats.interruptions.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.GraphicEq,
                accent = MoonGlow,
                label = stringResource(R.string.noise_events),
                value = stats.audioChunkCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.PhoneAndroid,
                accent = Lavender,
                label = stringResource(R.string.screen_events),
                value = stats.screenOnEvents.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.Insights,
                accent = SkyTeal,
                label = stringResource(R.string.ambient_avg),
                value = "%.0f dB".format(stats.ambientAvgDb),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SignalsCard(stats: SessionStats) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = NightSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    tint = MoonGlow,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.signals),
                    color = OnNight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (stats.signals.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_signals),
                    color = OnNightMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                stats.signals.forEach { sig ->
                    val (label, color) = signalLabelAndColor(sig)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = OnNight,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun signalLabelAndColor(sig: DetectedSignal): Pair<String, Color> = when (sig) {
    DetectedSignal.SPEECH -> stringResource(R.string.signal_speech) to Lavender
    DetectedSignal.COUGH -> stringResource(R.string.signal_cough) to PinkDawn
    DetectedSignal.MOVEMENT -> stringResource(R.string.signal_movement) to SkyTeal
    DetectedSignal.SNORE -> stringResource(R.string.signal_snore) to MoonGlow
    DetectedSignal.IRREGULAR_BREATHING -> stringResource(R.string.signal_breathing) to PinkDawn
}
