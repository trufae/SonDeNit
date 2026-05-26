package com.example.sondenit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.audio.AudioPlayer
import com.example.sondenit.audio.AudioSettings
import com.example.sondenit.audio.AudioTestRecorder
import com.example.sondenit.audio.AudioWaveform
import com.example.sondenit.service.LevelSnapshot
import com.example.sondenit.ui.components.Waveform
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    micGranted: Boolean,
    equalizationAmount: Float,
    recordingStartDelaySeconds: Int,
    onEqualizationChange: (Float) -> Unit,
    onRecordingStartDelayChange: (Int) -> Unit,
    onRequestMic: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val player = remember { AudioPlayer() }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var testFile by remember { mutableStateOf<File?>(null) }
    var testWaveform by remember { mutableStateOf(FloatArray(AudioWaveform.SAMPLE_COUNT)) }
    var testLevel by remember { mutableStateOf(LevelSnapshot()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val readyText = stringResource(R.string.settings_test_ready)
    val errorText = stringResource(R.string.settings_test_error)

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsTopBar(onBack = onBack)
            }
            item {
                EqualizationPanel(
                    amount = equalizationAmount,
                    onChange = onEqualizationChange,
                )
            }
            item {
                RecordingStartDelayPanel(
                    delaySeconds = recordingStartDelaySeconds,
                    onDelayChange = onRecordingStartDelayChange,
                )
            }
            item {
                TestRecordingPanel(
                    micGranted = micGranted,
                    isRecording = isRecording,
                    isPlaying = isPlaying,
                    hasRecording = testFile != null,
                    waveform = testWaveform,
                    level = testLevel,
                    statusText = statusText,
                    onRequestMic = onRequestMic,
                    onRecord = {
                        if (!micGranted || isRecording) return@TestRecordingPanel
                        scope.launch {
                            isRecording = true
                            isPlaying = false
                            statusText = null
                            testWaveform = FloatArray(AudioWaveform.SAMPLE_COUNT)
                            testLevel = LevelSnapshot(capturing = true)
                            player.stop()
                            testFile = AudioTestRecorder.record(context, equalizationAmount) { rmsDb, ambientDb ->
                                scope.launch {
                                    if (!isRecording) return@launch
                                    testWaveform = AudioWaveform.append(
                                        samples = testWaveform,
                                        value = AudioWaveform.visualLevel(rmsDb, ambientDb),
                                    )
                                    testLevel = LevelSnapshot(
                                        rmsDb = rmsDb,
                                        ambientDb = ambientDb,
                                        capturing = true,
                                    )
                                }
                            }
                            statusText = if (testFile != null) readyText else errorText
                            testLevel = testLevel.copy(capturing = false)
                            isRecording = false
                        }
                    },
                    onPlayToggle = {
                        val file = testFile ?: return@TestRecordingPanel
                        if (isPlaying) {
                            player.stop()
                            isPlaying = false
                        } else {
                            player.play(file) { isPlaying = false }
                            isPlaying = true
                        }
                    },
                )
            }
            item {
                PhaseExplanationPanel()
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(R.string.settings_title),
                color = OnNight,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                color = OnNightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EqualizationPanel(amount: Float, onChange: (Float) -> Unit) {
    SettingsPanel {
        IconTitle(
            icon = Icons.Filled.GraphicEq,
            accent = SkyTeal,
            title = stringResource(R.string.settings_equalization_title),
            value = stringResource(R.string.settings_equalization_hint, AudioSettings.percent(amount)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_equalization_description),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = amount,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = SkyTeal,
                activeTrackColor = SkyTeal,
                inactiveTrackColor = SkyTeal.copy(alpha = 0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.settings_equalization_natural),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(R.string.settings_equalization_strong),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RecordingStartDelayPanel(delaySeconds: Int, onDelayChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = recordingStartDelayLabel(delaySeconds)

    SettingsPanel {
        IconTitle(
            icon = Icons.Filled.Timer,
            accent = Lavender,
            title = stringResource(R.string.settings_start_delay_title),
            value = selectedLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_start_delay_description),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Lavender.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = NightDeep.copy(alpha = 0.2f),
                    contentColor = OnNight,
                ),
            ) {
                Text(
                    text = selectedLabel,
                    color = OnNight,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = OnNight,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                AudioSettings.RECORDING_START_DELAY_OPTIONS_SECONDS.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(recordingStartDelayLabel(seconds)) },
                        onClick = {
                            expanded = false
                            onDelayChange(seconds)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun recordingStartDelayLabel(seconds: Int): String =
    if (seconds <= 0) {
        stringResource(R.string.settings_start_delay_none)
    } else {
        stringResource(R.string.settings_start_delay_seconds, seconds)
    }

@Composable
private fun TestRecordingPanel(
    micGranted: Boolean,
    isRecording: Boolean,
    isPlaying: Boolean,
    hasRecording: Boolean,
    waveform: FloatArray,
    level: LevelSnapshot,
    statusText: String?,
    onRequestMic: () -> Unit,
    onRecord: () -> Unit,
    onPlayToggle: () -> Unit,
) {
    SettingsPanel {
        IconTitle(
            icon = Icons.Filled.Mic,
            accent = MoonGlow,
            title = stringResource(R.string.settings_test_title),
            value = null,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_test_body),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        if (!micGranted) {
            Text(
                text = stringResource(R.string.settings_mic_permission),
                color = PinkDawn,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequestMic,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MoonGlow, contentColor = NightDeep),
            ) {
                Text(stringResource(R.string.settings_mic_permission_button))
            }
        } else {
            TestWaveformPreview(
                samples = waveform,
                level = level,
                isRecording = isRecording,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRecord,
                    enabled = !isRecording,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MoonGlow, contentColor = NightDeep),
                ) {
                    if (isRecording) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = NightDeep,
                        )
                    } else {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isRecording) R.string.settings_test_recording else R.string.settings_test_record
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onPlayToggle,
                    enabled = hasRecording && !isRecording,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = OnNight,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isPlaying) R.string.settings_test_stop else R.string.settings_test_play
                        ),
                        color = OnNight,
                    )
                }
            }
        }
        statusText?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, color = OnNightMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TestWaveformPreview(
    samples: FloatArray,
    level: LevelSnapshot,
    isRecording: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        shape = RoundedCornerShape(18.dp),
        color = NightDeep.copy(alpha = 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRecording) MoonGlow.copy(alpha = 0.5f) else SkyTeal.copy(alpha = 0.18f),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Waveform(
                samples = samples,
                capturing = isRecording,
            )
            Text(
                text = "%.0f dB".format(level.ambientDb) + "  " +
                    stringResource(R.string.ambient_noise),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
            if (isRecording) {
                Text(
                    text = stringResource(R.string.settings_test_recording),
                    color = MoonGlow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun PhaseExplanationPanel() {
    SettingsPanel {
        IconTitle(
            icon = Icons.Filled.Timeline,
            accent = Lavender,
            title = stringResource(R.string.settings_phase_title),
            value = null,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_phase_intro),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        PhaseRow(MoonGlow, stringResource(R.string.phase_deep), stringResource(R.string.settings_phase_deep))
        PhaseRow(Lavender, stringResource(R.string.phase_rem), stringResource(R.string.settings_phase_rem))
        PhaseRow(SkyTeal, stringResource(R.string.phase_light), stringResource(R.string.settings_phase_light))
        PhaseRow(PinkDawn, stringResource(R.string.phase_awake), stringResource(R.string.settings_phase_awake))
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NightSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun IconTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    value: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        value?.let {
            Text(
                text = it,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PhaseRow(color: Color, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .background(color, RoundedCornerShape(5.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = OnNight,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = OnNightMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
