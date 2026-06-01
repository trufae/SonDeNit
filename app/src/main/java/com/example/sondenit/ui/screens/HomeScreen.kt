package com.example.sondenit.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SessionStats
import com.example.sondenit.data.SleepPhase
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.components.SessionRow
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.NightSurface
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.PinkDawn
import com.example.sondenit.ui.theme.SkyTeal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PortraitFirstSessionItemIndex = 4
private const val LandscapeFirstSessionItemIndex = 1

@Composable
fun HomeScreen(
    repo: SessionRepository,
    sessions: List<SleepSession>,
    activeSession: SleepSession?,
    onPrimaryAction: () -> Unit,
    onOpenBreathing: () -> Unit,
    onOpenSession: (SleepSession) -> Unit,
    onOpenSettings: () -> Unit,
    onRename: (SleepSession, String) -> Unit,
    onDelete: (SleepSession) -> Unit,
) {
    var renaming by rememberSaveable(stateSaver = sessionIdSaver) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(stateSaver = sessionIdSaver) { mutableStateOf<String?>(null) }
    var selectedPhaseSessionId by rememberSaveable(stateSaver = sessionIdSaver) { mutableStateOf<String?>(null) }
    val phaseHistory = remember(sessions) {
        sessions
            .filter { it.endedAt != null }
            .sortedBy { it.startedAt }
            .mapNotNull { session ->
                repo.readStats(session.id)?.toPhaseHistoryPoint(session)
            }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NightDeep, NightMid, NightDeep),
                )
            )
    ) {
        if (isLandscape) {
            LandscapeHomeContent(
                repo = repo,
                sessions = sessions,
                activeSession = activeSession,
                phaseHistory = phaseHistory,
                onPrimaryAction = onPrimaryAction,
                onOpenBreathing = onOpenBreathing,
                onOpenSession = onOpenSession,
                onOpenSettings = onOpenSettings,
                onRenameRequest = { renaming = it.id },
                selectedSessionId = selectedPhaseSessionId,
                onSelectedSessionChange = { selectedPhaseSessionId = it },
            )
        } else {
            PortraitHomeContent(
                repo = repo,
                sessions = sessions,
                activeSession = activeSession,
                phaseHistory = phaseHistory,
                onPrimaryAction = onPrimaryAction,
                onOpenBreathing = onOpenBreathing,
                onOpenSession = onOpenSession,
                onOpenSettings = onOpenSettings,
                onRenameRequest = { renaming = it.id },
                selectedSessionId = selectedPhaseSessionId,
                onSelectedSessionChange = { selectedPhaseSessionId = it },
            )
        }
    }

    if (renaming != null) {
        val s = sessions.firstOrNull { it.id == renaming }
        if (s == null) renaming = null else SessionActionsDialog(
            session = s,
            onDismiss = { renaming = null },
            onRename = { newName ->
                onRename(s, newName)
                renaming = null
            },
            onAskDelete = {
                deleting = s.id
                renaming = null
            },
        )
    }

    if (deleting != null) {
        val s = sessions.firstOrNull { it.id == deleting }
        if (s == null) deleting = null else AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(s)
                    if (selectedPhaseSessionId == s.id) selectedPhaseSessionId = null
                    deleting = null
                }) { Text(stringResource(R.string.delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message)) },
        )
    }
}

@Composable
private fun PortraitHomeContent(
    repo: SessionRepository,
    sessions: List<SleepSession>,
    activeSession: SleepSession?,
    phaseHistory: List<PhaseHistoryPoint>,
    onPrimaryAction: () -> Unit,
    onOpenBreathing: () -> Unit,
    onOpenSession: (SleepSession) -> Unit,
    onOpenSettings: () -> Unit,
    onRenameRequest: (SleepSession) -> Unit,
    selectedSessionId: String?,
    onSelectedSessionChange: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = 56.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Header(onOpenSettings)
        }
        item {
            Spacer(Modifier.height(20.dp))
            BreathingActionButton(onClick = onOpenBreathing)
            Spacer(Modifier.height(12.dp))
            PrimaryActionCard(
                isResume = activeSession != null,
                onClick = onPrimaryAction,
            )
            Spacer(Modifier.height(16.dp))
        }
        item {
            PhaseHistoryGraph(
                points = phaseHistory,
                onPointSelected = { session ->
                    onSelectedSessionChange(session.id)
                    scrollToSession(
                        scope = scope,
                        listState = listState,
                        sessions = sessions,
                        sessionId = session.id,
                        firstSessionItemIndex = PortraitFirstSessionItemIndex,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        sessionListItems(
            repo = repo,
            sessions = sessions,
            activeSession = activeSession,
            selectedSessionId = selectedSessionId,
            onOpenSession = onOpenSession,
            onRenameRequest = onRenameRequest,
        )
    }
}

@Composable
private fun LandscapeHomeContent(
    repo: SessionRepository,
    sessions: List<SleepSession>,
    activeSession: SleepSession?,
    phaseHistory: List<PhaseHistoryPoint>,
    onPrimaryAction: () -> Unit,
    onOpenBreathing: () -> Unit,
    onOpenSession: (SleepSession) -> Unit,
    onOpenSettings: () -> Unit,
    onRenameRequest: (SleepSession) -> Unit,
    selectedSessionId: String?,
    onSelectedSessionChange: (String) -> Unit,
) {
    val sessionListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 36.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Header(onOpenSettings)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BreathingActionButton(
                    onClick = onOpenBreathing,
                    modifier = Modifier.weight(1f),
                    height = 70.dp,
                    compact = true,
                )
                PrimaryActionCard(
                    isResume = activeSession != null,
                    onClick = onPrimaryAction,
                    modifier = Modifier.weight(1f),
                    height = 70.dp,
                    compact = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            PhaseHistoryGraph(
                points = phaseHistory,
                modifier = Modifier.weight(1f),
                fillAvailableHeight = true,
                onPointSelected = { session ->
                    onSelectedSessionChange(session.id)
                    scrollToSession(
                        scope = scope,
                        listState = sessionListState,
                        sessions = sessions,
                        sessionId = session.id,
                        firstSessionItemIndex = LandscapeFirstSessionItemIndex,
                    )
                },
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            state = sessionListState,
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sessionListItems(
                repo = repo,
                sessions = sessions,
                activeSession = activeSession,
                selectedSessionId = selectedSessionId,
                onOpenSession = onOpenSession,
                onRenameRequest = onRenameRequest,
            )
        }
    }
}

private fun scrollToSession(
    scope: kotlinx.coroutines.CoroutineScope,
    listState: LazyListState,
    sessions: List<SleepSession>,
    sessionId: String,
    firstSessionItemIndex: Int,
) {
    val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
    if (sessionIndex < 0) return

    scope.launch {
        listState.animateScrollToItem(firstSessionItemIndex + sessionIndex)
    }
}

private fun LazyListScope.sessionListItems(
    repo: SessionRepository,
    sessions: List<SleepSession>,
    activeSession: SleepSession?,
    selectedSessionId: String?,
    onOpenSession: (SleepSession) -> Unit,
    onRenameRequest: (SleepSession) -> Unit,
) {
    if (sessions.isEmpty()) {
        item {
            EmptyState()
        }
    } else {
        item {
            Text(
                text = stringResource(R.string.past_sessions),
                color = OnNightMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(sessions, key = { it.id }) { session ->
            val durationMs = session.endedAt?.let { it - session.startedAt }
            val stats = remember(session.id, session.endedAt) {
                if (session.endedAt == null) null else repo.readStats(session.id)
            }
            SessionRow(
                session = session,
                durationMs = durationMs,
                qualityScore = stats?.qualityScore,
                phaseDurations = stats?.phaseDurations,
                isActive = activeSession?.id == session.id,
                isSelected = selectedSessionId == session.id,
                onClick = { onOpenSession(session) },
                onLongClick = { onRenameRequest(session) },
            )
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(MoonGlow.copy(alpha = 0.3f), MoonGlow.copy(alpha = 0f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.NightsStay,
                contentDescription = null,
                tint = MoonGlow,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                color = OnNight,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                color = OnNightMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = OnNight,
            )
        }
    }
}

@Composable
private fun BreathingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 72.dp,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(if (compact) 22.dp else 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SkyTeal,
            contentColor = NightDeep,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Air,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 26.dp else 30.dp),
            )
            Spacer(Modifier.size(if (compact) 8.dp else 12.dp))
            Text(
                text = stringResource(R.string.breathing_button),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PrimaryActionCard(
    isResume: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp = 96.dp,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(if (compact) 22.dp else 28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MoonGlow,
            contentColor = NightDeep,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 26.dp else 36.dp),
            )
            Spacer(Modifier.size(if (compact) 8.dp else 14.dp))
            Text(
                text = stringResource(
                    if (isResume) R.string.continue_sleep else R.string.start_sleep
                ),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class PhaseHistoryPoint(
    val session: SleepSession,
    val deepShare: Float,
    val remShare: Float,
    val lightShare: Float,
    val awakeShare: Float,
)

private data class PhaseHistoryLine(
    val label: String,
    val color: Color,
    val value: (PhaseHistoryPoint) -> Float,
)

private fun SessionStats.toPhaseHistoryPoint(session: SleepSession): PhaseHistoryPoint? {
    val totalPhaseMs = phaseDurations.values.sum()
    if (totalPhaseMs <= 0L) return null

    fun shareOf(phase: SleepPhase): Float =
        ((phaseDurations[phase] ?: 0L).toFloat() / totalPhaseMs.toFloat()).coerceIn(0f, 1f)

    return PhaseHistoryPoint(
        session = session,
        deepShare = shareOf(SleepPhase.DEEP),
        remShare = shareOf(SleepPhase.REM),
        lightShare = shareOf(SleepPhase.LIGHT),
        awakeShare = shareOf(SleepPhase.AWAKE),
    )
}

@Composable
private fun PhaseHistoryGraph(
    points: List<PhaseHistoryPoint>,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
    onPointSelected: ((SleepSession) -> Unit)? = null,
) {
    val lines = listOf(
        PhaseHistoryLine(stringResource(R.string.phase_deep), Lavender) { it.deepShare },
        PhaseHistoryLine(stringResource(R.string.phase_rem), MoonGlow) { it.remShare },
        PhaseHistoryLine(stringResource(R.string.phase_light), SkyTeal) { it.lightShare },
        PhaseHistoryLine(stringResource(R.string.phase_awake), PinkDawn) { it.awakeShare },
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NightSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier
                .then(if (fillAvailableHeight) Modifier.fillMaxSize() else Modifier)
                .padding(16.dp)
        ) {
            val chartTapModifier = if (onPointSelected == null || points.isEmpty()) {
                Modifier
            } else {
                Modifier.pointerInput(points, onPointSelected) {
                    detectTapGestures { offset ->
                        val left = 8.dp.toPx()
                        val right = size.width.toFloat() - 8.dp.toPx()
                        val chartWidth = (right - left).coerceAtLeast(1f)
                        val normalizedX = (offset.x - left).coerceIn(0f, chartWidth) / chartWidth
                        val selectedIndex = if (points.size == 1) {
                            0
                        } else {
                            (normalizedX * (points.size - 1)).roundToInt()
                                .coerceIn(0, points.lastIndex)
                        }
                        onPointSelected(points[selectedIndex].session)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillAvailableHeight) Modifier.weight(1f) else Modifier.height(120.dp))
                    .then(chartTapModifier),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val top = 8.dp.toPx()
                    val bottom = size.height - 8.dp.toPx()
                    val chartWidth = (right - left).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)

                    repeat(4) { idx ->
                        val y = top + chartHeight * idx / 3f
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    if (points.isNotEmpty()) {
                        lines.forEach { line ->
                            val coordinates = points.mapIndexed { index, point ->
                                val x = if (points.size == 1) {
                                    left + chartWidth / 2f
                                } else {
                                    left + chartWidth * index / (points.size - 1).toFloat()
                                }
                                val y = bottom - chartHeight * line.value(point).coerceIn(0f, 1f)
                                Offset(x, y)
                            }
                            if (coordinates.size > 1) {
                                val path = Path().apply {
                                    moveTo(coordinates.first().x, coordinates.first().y)
                                    coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path = path,
                                    color = line.color.copy(alpha = 0.9f),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    ),
                                )
                            }
                            coordinates.forEach { point ->
                                drawCircle(
                                    color = line.color,
                                    radius = 3.5.dp.toPx(),
                                    center = point,
                                )
                            }
                        }
                    }
                }
                if (points.isEmpty()) {
                    Text(
                        text = stringResource(R.string.phase_history_empty),
                        color = OnNightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lines.forEach { line ->
                    PhaseLegendItem(
                        label = line.label,
                        color = line.color,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseLegendItem(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            color = OnNightMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_sessions),
            color = OnNightMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SessionActionsDialog(
    session: SleepSession,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onAskDelete: () -> Unit,
) {
    var newName by rememberSaveable(session.id) { mutableStateOf(session.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onRename(trimmed)
            }) { Text(stringResource(R.string.rename_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAskDelete) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

private val sessionIdSaver = androidx.compose.runtime.saveable.Saver<String?, String>(
    save = { it ?: "" },
    restore = { if (it.isEmpty()) null else it },
)
