package com.example.sondenit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.data.SleepSession
import com.example.sondenit.service.LevelSnapshot
import com.example.sondenit.service.RecordingState
import com.example.sondenit.service.TimelineEntry
import com.example.sondenit.ui.components.MediumCircleButton
import com.example.sondenit.ui.components.TimelineLabels
import com.example.sondenit.ui.components.TimelineRow
import com.example.sondenit.ui.components.Waveform
import com.example.sondenit.ui.components.describe
import com.example.sondenit.ui.theme.DangerRed
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.MoonGlowSoft
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.SkyTeal
import com.example.sondenit.util.formatTimer
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen(
    session: SleepSession,
    state: RecordingState,
    level: LevelSnapshot,
    waveform: FloatArray,
    recentEvents: List<TimelineEntry>,
    chunkCount: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    var elapsedMs by remember { mutableLongStateOf(System.currentTimeMillis() - session.startedAt) }
    LaunchedEffect(session.id, state) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - session.startedAt
            delay(1000)
        }
    }

    var confirmStop by remember { mutableStateOf(false) }
    val labels = remember { defaultTimelineLabels() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))
            )
    ) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            TopBar(onBack = onBack, sessionName = session.displayName)
            Spacer(Modifier.height(8.dp))
            ElapsedDisplay(elapsedMs = elapsedMs, state = state)
            Spacer(Modifier.height(12.dp))
            WaveformPanel(samples = waveform, level = level, state = state)
            Spacer(Modifier.height(20.dp))
            Controls(
                state = state,
                onPause = onPause,
                onResume = onResume,
                onStop = { confirmStop = true },
            )
            Spacer(Modifier.height(20.dp))
            ChunksHeader(chunkCount = chunkCount)
            EventsList(events = recentEvents, labels = labels)
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.confirm_stop_title)) },
            text = { Text(stringResource(R.string.confirm_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onStop()
                }) {
                    Text(
                        stringResource(R.string.confirm_stop_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, sessionName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
        Column {
            Text(
                text = stringResource(R.string.session_in_progress),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = sessionName,
                color = OnNight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ElapsedDisplay(elapsedMs: Long, state: RecordingState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.elapsed),
            color = OnNightMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = formatTimer(elapsedMs),
            color = OnNight,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                when (state) {
                    RecordingState.RECORDING -> R.string.listening
                    RecordingState.PAUSED -> R.string.paused
                    RecordingState.IDLE -> R.string.session_in_progress
                }
            ),
            color = if (state == RecordingState.PAUSED) DangerRed else SkyTeal,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun WaveformPanel(samples: FloatArray, level: LevelSnapshot, state: RecordingState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(120.dp),
        shape = RoundedCornerShape(20.dp),
        color = NightSurface,
    ) {
        Box(Modifier.fillMaxSize()) {
            Waveform(
                samples = samples,
                capturing = level.capturing && state == RecordingState.RECORDING,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%.0f dB".format(level.ambientDb) + "  " +
                        stringResource(R.string.ambient_noise),
                    color = OnNightMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (level.capturing && state == RecordingState.RECORDING) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.recording_chunk),
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Controls(
    state: RecordingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        when (state) {
            RecordingState.RECORDING -> {
                MediumCircleButton(
                    icon = Icons.Filled.Pause,
                    label = stringResource(R.string.pause),
                    onClick = onPause,
                    primaryColor = Lavender,
                    secondaryColor = MoonGlowSoft,
                    contentColor = NightDeep,
                )
            }
            RecordingState.PAUSED -> {
                MediumCircleButton(
                    icon = Icons.Filled.PlayArrow,
                    label = stringResource(R.string.resume),
                    onClick = onResume,
                    primaryColor = SkyTeal,
                    secondaryColor = MoonGlow,
                    contentColor = NightDeep,
                )
            }
            else -> Unit
        }
        MediumCircleButton(
            icon = Icons.Filled.Stop,
            label = stringResource(R.string.stop),
            onClick = onStop,
            primaryColor = DangerRed,
            secondaryColor = Color(0xFFFFB1B1),
            contentColor = NightDeep,
        )
    }
}

@Composable
private fun ChunksHeader(chunkCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recent_events),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$chunkCount",
            color = MoonGlow,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EventsList(events: List<TimelineEntry>, labels: TimelineLabels) {
    val reversed = remember(events) { events.asReversed() }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        items(reversed.size, key = { idx -> reversed[idx].event.timestamp.toString() + idx }) { idx ->
            val entry = reversed[idx]
            TimelineRow(
                spec = describe(entry.event, labels),
                showLineAbove = idx > 0,
                showLineBelow = idx < reversed.size - 1,
            )
        }
    }
}

fun defaultTimelineLabels() = TimelineLabels(
    sessionStart = "Inici de la sessió",
    sessionEnd = "Final de la sessió",
    pause = "Pausa",
    resume = "Reactivació",
    screenOn = "Pantalla encesa",
    screenOff = "Pantalla apagada",
    movement = "Moviment detectat",
    phoneMoved = "Mòbil mogut",
)
