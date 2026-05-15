package com.example.sondenit.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sondenit.R
import com.example.sondenit.audio.SpaTonePlayer
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted

private const val BreathCycles = 6
private const val InhaleMillis = 5_000
private const val HoldMillis = 4_000
private const val ExhaleMillis = 7_000
private const val CycleMillis = InhaleMillis + HoldMillis + ExhaleMillis
private const val TotalExerciseMillis = BreathCycles * CycleMillis
private val BreathEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

private enum class BreathPhase {
    Inhale,
    Hold,
    Exhale,
}

private suspend fun runBreathAnimation(
    durationMillis: Int,
    onProgress: (elapsedMillis: Int, linearProgress: Float, easedProgress: Float) -> Unit,
) {
    onProgress(0, 0f, 0f)
    val startNanos = withFrameNanos { it }
    while (true) {
        val frameNanos = withFrameNanos { it }
        val elapsedMillis = ((frameNanos - startNanos) / 1_000_000L)
            .coerceIn(0L, durationMillis.toLong())
            .toInt()
        val linearProgress = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        onProgress(elapsedMillis, linearProgress, BreathEasing.transform(linearProgress))
        if (linearProgress >= 1f) break
    }
}

@Composable
fun BreathingScreen(
    onClose: () -> Unit,
) {
    val player = remember { SpaTonePlayer() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var circleProgress by remember { mutableFloatStateOf(0f) }
    var phaseProgress by remember { mutableFloatStateOf(0f) }
    var phaseElapsedMillis by remember { mutableIntStateOf(0) }
    var totalElapsedMillis by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(BreathPhase.Inhale) }
    var cycle by remember { mutableIntStateOf(1) }
    var complete by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.start()
                Lifecycle.Event.ON_PAUSE -> player.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
        }
    }

    LaunchedEffect(runId) {
        fun setBreathProgress(progress: Float) {
            circleProgress = progress.coerceIn(0f, 1f)
            player.setBreathLevel(circleProgress)
        }

        fun setTimedProgress(phaseStartMillis: Int, elapsedMillis: Int, progress: Float) {
            phaseElapsedMillis = elapsedMillis
            phaseProgress = progress.coerceIn(0f, 1f)
            totalElapsedMillis = (phaseStartMillis + elapsedMillis)
                .coerceIn(0, TotalExerciseMillis)
        }

        complete = false
        setBreathProgress(0f)
        setTimedProgress(0, 0, 0f)
        repeat(BreathCycles) { index ->
            cycle = index + 1
            val cycleStartMillis = index * CycleMillis

            phase = BreathPhase.Inhale
            runBreathAnimation(InhaleMillis) { elapsedMillis, linearProgress, easedProgress ->
                setTimedProgress(cycleStartMillis, elapsedMillis, linearProgress)
                setBreathProgress(easedProgress)
            }

            phase = BreathPhase.Hold
            setBreathProgress(1f)
            runBreathAnimation(HoldMillis) { elapsedMillis, linearProgress, _ ->
                setTimedProgress(cycleStartMillis + InhaleMillis, elapsedMillis, linearProgress)
                setBreathProgress(1f)
            }

            phase = BreathPhase.Exhale
            runBreathAnimation(ExhaleMillis) { elapsedMillis, linearProgress, easedProgress ->
                setTimedProgress(
                    cycleStartMillis + InhaleMillis + HoldMillis,
                    elapsedMillis,
                    linearProgress,
                )
                setBreathProgress(1f - easedProgress)
            }
        }
        complete = true
        setBreathProgress(0f)
        setTimedProgress(TotalExerciseMillis, 0, 1f)
        player.setBreathLevel(0.15f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF061427), Color(0xFF102B4B), NightDeep),
                )
            )
            .padding(24.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.breathing_interrupt),
                tint = OnNight,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.breathing_title),
                    color = OnNight,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.breathing_guidance),
                    color = OnNightMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            BreathingCircle(
                progress = circleProgress,
                phase = phase,
                cycle = cycle,
                complete = complete,
            )

            if (complete) {
                CompletionActions(
                    onClose = onClose,
                    onRepeat = { runId++ },
                )
            } else {
                BreathingProgress(
                    phase = phase,
                    phaseProgress = phaseProgress,
                    phaseElapsedMillis = phaseElapsedMillis,
                    totalProgress = totalElapsedMillis.toFloat() / TotalExerciseMillis,
                    totalElapsedMillis = totalElapsedMillis,
                )
            }
        }
    }
}

@Composable
private fun BreathingCircle(
    progress: Float,
    phase: BreathPhase,
    cycle: Int,
    complete: Boolean,
) {
    val circleSize = 156.dp + 132.dp * progress
    val phaseColor by animateColorAsState(
        targetValue = phaseColor(phase),
        animationSpec = tween(durationMillis = 450),
        label = "breathingPhaseColor",
    )
    val phaseText = when {
        complete -> stringResource(R.string.breathing_done)
        phase == BreathPhase.Inhale -> stringResource(R.string.breathing_inhale)
        phase == BreathPhase.Hold -> stringResource(R.string.breathing_hold)
        else -> stringResource(R.string.breathing_exhale)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.96f),
                            phaseColor,
                            phaseColor.copy(alpha = 0.48f),
                        )
                    )
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phaseText,
                color = OnNight,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.breathing_cycle_count, cycle, BreathCycles),
                color = OnNight.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun BreathingProgress(
    phase: BreathPhase,
    phaseProgress: Float,
    phaseElapsedMillis: Int,
    totalProgress: Float,
    totalElapsedMillis: Int,
) {
    val phaseDurationMillis = phase.durationMillis
    val phaseElapsedSeconds = phaseElapsedMillis.toDisplaySeconds()
    val phaseRemainingSeconds = ((phaseDurationMillis - phaseElapsedMillis).coerceAtLeast(0))
        .toDisplaySeconds()
    val totalRemainingSeconds = ((TotalExerciseMillis - totalElapsedMillis).coerceAtLeast(0))
        .toDisplaySeconds()
    val accent by animateColorAsState(
        targetValue = phaseColor(phase),
        animationSpec = tween(durationMillis = 450),
        label = "breathingProgressColor",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProgressLabelRow(
            start = stringResource(
                R.string.breathing_step_seconds,
                phaseElapsedSeconds,
                phaseRemainingSeconds,
            ),
            end = stringResource(R.string.breathing_total_left, totalRemainingSeconds),
        )
        BreathingProgressBar(
            progress = phaseProgress,
            color = accent,
        )
        BreathingProgressBar(
            progress = totalProgress.coerceIn(0f, 1f),
            color = OnNight,
            trackColor = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.height(5.dp),
        )
        Text(
            text = stringResource(R.string.breathing_safety_note),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ProgressLabelRow(
    start: String,
    end: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = start,
            color = OnNight,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = end,
            color = OnNightMuted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BreathingProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier.height(8.dp),
    trackColor: Color = Color.White.copy(alpha = 0.16f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

private val BreathPhase.durationMillis: Int
    get() = when (this) {
        BreathPhase.Inhale -> InhaleMillis
        BreathPhase.Hold -> HoldMillis
        BreathPhase.Exhale -> ExhaleMillis
    }

private fun phaseColor(phase: BreathPhase): Color = when (phase) {
    BreathPhase.Inhale -> Color(0xFF6FE7A7)
    BreathPhase.Hold -> Color(0xFF79C7FF)
    BreathPhase.Exhale -> Color(0xFFFF78D4)
}

private fun Int.toDisplaySeconds(): Int = ((this + 999) / 1000).coerceAtLeast(0)

@Composable
private fun CompletionActions(
    onClose: () -> Unit,
    onRepeat: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.breathing_complete_message),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnNight),
            ) {
                Text(stringResource(R.string.close))
            }
            Button(
                onClick = onRepeat,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF79C7FF),
                    contentColor = NightDeep,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.repeat_exercise))
            }
        }
    }
}
