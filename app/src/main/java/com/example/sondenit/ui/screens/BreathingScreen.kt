package com.example.sondenit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.delay

private const val BreathCycles = 6
private const val InhaleMillis = 4_000
private const val HoldMillis = 2_000
private const val ExhaleMillis = 6_000

private enum class BreathPhase {
    Inhale,
    Hold,
    Exhale,
}

@Composable
fun BreathingScreen(
    onClose: () -> Unit,
) {
    val circleProgress = remember { Animatable(0f) }
    val player = remember { SpaTonePlayer() }
    val lifecycleOwner = LocalLifecycleOwner.current
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

    LaunchedEffect(player, circleProgress) {
        snapshotFlow { circleProgress.value }
            .collect { progress -> player.setBreathLevel(progress) }
    }

    LaunchedEffect(runId) {
        complete = false
        circleProgress.snapTo(0f)
        repeat(BreathCycles) { index ->
            cycle = index + 1
            phase = BreathPhase.Inhale
            circleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(InhaleMillis, easing = LinearEasing),
            )
            phase = BreathPhase.Hold
            delay(HoldMillis.toLong())
            phase = BreathPhase.Exhale
            circleProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(ExhaleMillis, easing = LinearEasing),
            )
        }
        complete = true
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
                progress = circleProgress.value,
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
                Text(
                    text = stringResource(R.string.breathing_safety_note),
                    color = OnNightMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
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
                            Color(0xFFB7E7FF),
                            Color(0xFF3A9FEF),
                            Color(0xFF0D4FA5),
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
