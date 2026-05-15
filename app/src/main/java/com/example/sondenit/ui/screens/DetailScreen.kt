package com.example.sondenit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.audio.AudioGrouping
import com.example.sondenit.audio.AudioPlayer
import com.example.sondenit.audio.NoiseGroup
import com.example.sondenit.data.DetectedSignal
import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SessionStats
import com.example.sondenit.data.SessionStatsComputer
import com.example.sondenit.data.SleepPhase
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.components.EventRibbon
import com.example.sondenit.ui.components.PieChartWithLegend
import com.example.sondenit.ui.components.PieSlice
import com.example.sondenit.ui.components.StatCard
import com.example.sondenit.ui.components.TimelineRow
import com.example.sondenit.ui.components.describe
import com.example.sondenit.ui.components.describeGroup
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
import kotlin.math.sqrt

private const val GROUP_MIN_S = 0f
private const val GROUP_MAX_S = 300f
private const val MIN_INT_MIN_S = 0f
private const val MIN_INT_MAX_S = 30f
private val WIDE_BREAKPOINT = 600.dp
private val SECTION_PAD = 20.dp
private val TIMELINE_PAD = 14.dp

@Composable
fun DetailScreen(
    repo: SessionRepository,
    session: SleepSession,
    onBack: () -> Unit,
    onRename: (SleepSession, String) -> Unit,
    onUpdateNotes: (SleepSession, String) -> Unit,
    onDelete: (SleepSession) -> Unit,
) {
    val player = remember { AudioPlayer() }
    var playingFile by remember { mutableStateOf<String?>(null) }
    val events = remember(session.id, session.endedAt) { repo.readEvents(session.id) }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingNotes by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable(session.id) { mutableStateOf(session.displayName) }
    var notesDraft by rememberSaveable(session.id) { mutableStateOf(session.notes) }

    var groupSeconds by rememberSaveable(session.id) {
        mutableFloatStateOf(SessionStats.DEFAULT_GROUPING_WINDOW_MS / 1000f)
    }
    var minIntSeconds by rememberSaveable(session.id) {
        mutableFloatStateOf(SessionStats.DEFAULT_MIN_INTERRUPTION_MS / 1000f)
    }

    val stats by remember(events) {
        derivedStateOf {
            SessionStatsComputer.compute(
                events = events,
                fallbackEnd = session.endedAt,
                groupingWindowMs = (groupSeconds * 1000f).toLong(),
                minInterruptionMs = (minIntSeconds * 1000f).toLong(),
            )
        }
    }
    val audioChunks = remember(events) { events.filterIsInstance<SessionEvent.AudioChunk>() }
    val groups by remember(audioChunks) {
        derivedStateOf { AudioGrouping.group(audioChunks, (groupSeconds * 1000f).toLong()) }
    }
    val significantGroups by remember(groups) {
        derivedStateOf { groups.filter { it.totalDurationMs >= (minIntSeconds * 1000f).toLong() } }
    }
    val timelineRows by remember(events) {
        derivedStateOf { buildTimelineRows(events, groups) }
    }
    val pausedRanges = remember(events) { computePausedRanges(events) }
    val screenOnTimestamps = remember(events) {
        events.filterIsInstance<SessionEvent.ScreenOn>().map { it.timestamp }
    }
    val labels = remember { defaultTimelineLabels() }

    DisposableEffectOnDispose { player.stop() }

    val sessionStart = session.startedAt
    val sessionEnd = session.endedAt ?: events.lastOrNull()?.timestamp ?: session.startedAt

    val onPlayGroup: (NoiseGroup) -> Unit = { group ->
        val first = group.chunks.first()
        val file = File(repo.sessionDir(session.id), first.file)
        if (file.exists()) {
            playingFile = first.file
            player.play(file) { playingFile = null }
        }
    }
    val onStopPlayback: () -> Unit = {
        player.stop()
        playingFile = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))),
    ) {
        val isLandscape = maxWidth > maxHeight
        val isWide = maxWidth >= WIDE_BREAKPOINT

        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            TopBar(
                title = session.displayName,
                subtitle = formatDateLong(session.startedAt),
                onBack = onBack,
                onRename = { renaming = true },
                onDelete = { deleting = true },
            )

            when {
                isLandscape -> SplitLayout(
                    session = session,
                    stats = stats,
                    timelineRows = timelineRows,
                    significantGroups = significantGroups,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    pausedRanges = pausedRanges,
                    screenOnTimestamps = screenOnTimestamps,
                    labels = labels,
                    playingFile = playingFile,
                    onPlayGroup = onPlayGroup,
                    onStopPlayback = onStopPlayback,
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
                    },
                )
                isWide -> WidePortraitLayout(
                    session = session,
                    stats = stats,
                    timelineRows = timelineRows,
                    significantGroups = significantGroups,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    pausedRanges = pausedRanges,
                    screenOnTimestamps = screenOnTimestamps,
                    labels = labels,
                    playingFile = playingFile,
                    onPlayGroup = onPlayGroup,
                    onStopPlayback = onStopPlayback,
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
                    },
                )
                else -> CompactLayout(
                    session = session,
                    stats = stats,
                    timelineRows = timelineRows,
                    significantGroups = significantGroups,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    pausedRanges = pausedRanges,
                    screenOnTimestamps = screenOnTimestamps,
                    labels = labels,
                    playingFile = playingFile,
                    onPlayGroup = onPlayGroup,
                    onStopPlayback = onStopPlayback,
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
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
    if (editingNotes) {
        AlertDialog(
            onDismissRequest = { editingNotes = false },
            title = { Text(stringResource(R.string.comments_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { notesDraft = it },
                    label = { Text(stringResource(R.string.comments_field_label)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateNotes(session, notesDraft.trim())
                    editingNotes = false
                }) { Text(stringResource(R.string.rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNotes = false }) {
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

// ── layout variants ────────────────────────────────────────────────────────

@Composable
private fun CompactLayout(
    session: SleepSession,
    stats: SessionStats,
    timelineRows: List<TimelineRowData>,
    significantGroups: List<NoiseGroup>,
    sessionStart: Long,
    sessionEnd: Long,
    pausedRanges: List<LongRange>,
    screenOnTimestamps: List<Long>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playingFile: String?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onStopPlayback: () -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { SummaryCard(session, stats) }
        item { CommentsSection(session.notes, onEditNotes) }
        if (stats.sleptDurationMs > 0) {
            item { Spacer(Modifier.height(16.dp)); PhasesSection(stats, legendBelow = true) }
            item {
                Spacer(Modifier.height(16.dp))
                EventRibbon(
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    groups = significantGroups,
                    screenOnTimestamps = screenOnTimestamps,
                    pausedRanges = pausedRanges,
                    title = stringResource(R.string.event_ribbon),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
            }
            item { Spacer(Modifier.height(16.dp)); StatsGrid(stats) }
            item { Spacer(Modifier.height(16.dp)); SignalsCard(stats) }
        }
        timelineSection(timelineRows, labels, playingFile, onPlayGroup, onStopPlayback)
    }
}

@Composable
private fun WidePortraitLayout(
    session: SleepSession,
    stats: SessionStats,
    timelineRows: List<TimelineRowData>,
    significantGroups: List<NoiseGroup>,
    sessionStart: Long,
    sessionEnd: Long,
    pausedRanges: List<LongRange>,
    screenOnTimestamps: List<Long>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playingFile: String?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onStopPlayback: () -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { SummaryCard(session, stats) }
        item { CommentsSection(session.notes, onEditNotes) }
        if (stats.sleptDurationMs > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SECTION_PAD),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PhasesSection(
                            stats = stats,
                            legendBelow = true,
                            outerPadding = 0.dp,
                            chartMaxSize = 240.dp,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatsGrid(stats, outerPadding = 0.dp)
                        SignalsCard(stats, outerPadding = 0.dp)
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                EventRibbon(
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    groups = significantGroups,
                    screenOnTimestamps = screenOnTimestamps,
                    pausedRanges = pausedRanges,
                    title = stringResource(R.string.event_ribbon),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
            }
        }
        timelineSection(timelineRows, labels, playingFile, onPlayGroup, onStopPlayback)
    }
}

@Composable
private fun SplitLayout(
    session: SleepSession,
    stats: SessionStats,
    timelineRows: List<TimelineRowData>,
    significantGroups: List<NoiseGroup>,
    sessionStart: Long,
    sessionEnd: Long,
    pausedRanges: List<LongRange>,
    screenOnTimestamps: List<Long>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playingFile: String?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onStopPlayback: () -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left half: stats / sliders / signals
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item { SummaryCard(session, stats) }
            item { CommentsSection(session.notes, onEditNotes) }
            if (stats.sleptDurationMs > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PhasesSection(
                        stats = stats,
                        legendBelow = false,
                        chartMaxSize = 220.dp,
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    EventRibbon(
                        sessionStart = sessionStart,
                        sessionEnd = sessionEnd,
                        groups = significantGroups,
                        screenOnTimestamps = screenOnTimestamps,
                        pausedRanges = pausedRanges,
                        title = stringResource(R.string.event_ribbon),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
                }
                item { Spacer(Modifier.height(16.dp)); StatsGrid(stats) }
                item { Spacer(Modifier.height(16.dp)); SignalsCard(stats) }
            }
        }
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.06f))
        )
        // Right half: timeline
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            timelineSection(timelineRows, labels, playingFile, onPlayGroup, onStopPlayback)
        }
    }
}

// ── timeline (shared LazyListScope extension) ──────────────────────────────

private fun LazyListScope.timelineSection(
    timelineRows: List<TimelineRowData>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playingFile: String?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onStopPlayback: () -> Unit,
) {
    item {
        Spacer(Modifier.height(20.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.timeline),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = SECTION_PAD),
        )
        Spacer(Modifier.height(4.dp))
    }
    items(timelineRows.size) { idx ->
        val row = timelineRows[idx]
        val prev = if (idx == 0) null else timelineRows[idx - 1]
        val gap = if (prev == null) 0L else (row.timestamp - prev.timestamp)
        if (gap > 0) TimelineGap(gap)
        Box(modifier = Modifier.padding(horizontal = TIMELINE_PAD)) {
            when (row) {
                is TimelineRowData.Event -> TimelineRow(
                    spec = describe(row.event, labels),
                    showLineAbove = idx > 0,
                    showLineBelow = idx < timelineRows.size - 1,
                )
                is TimelineRowData.Group -> {
                    val isPlayable = row.group.chunks.first().file
                    TimelineRow(
                        spec = describeGroup(row.group)
                            .copy(playing = playingFile == isPlayable),
                        showLineAbove = idx > 0,
                        showLineBelow = idx < timelineRows.size - 1,
                        onPlay = { onPlayGroup(row.group) },
                        onStop = onStopPlayback,
                    )
                }
            }
        }
    }
}

// ── data + helpers ─────────────────────────────────────────────────────────

sealed interface TimelineRowData {
    val timestamp: Long
    data class Event(val event: SessionEvent) : TimelineRowData {
        override val timestamp: Long get() = event.timestamp
    }
    data class Group(val group: NoiseGroup) : TimelineRowData {
        override val timestamp: Long get() = group.startTimestamp
    }
}

private fun buildTimelineRows(
    events: List<SessionEvent>,
    groups: List<NoiseGroup>,
): List<TimelineRowData> {
    val rows = mutableListOf<TimelineRowData>()
    for (e in events) {
        if (e !is SessionEvent.AudioChunk) rows.add(TimelineRowData.Event(e))
    }
    for (g in groups) rows.add(TimelineRowData.Group(g))
    rows.sortBy { it.timestamp }
    return rows
}

private fun computePausedRanges(events: List<SessionEvent>): List<LongRange> {
    val out = mutableListOf<LongRange>()
    var p: Long? = null
    events.forEach { ev ->
        when (ev) {
            is SessionEvent.Pause -> p = ev.timestamp
            is SessionEvent.Resume -> p?.let { ps -> out.add(ps..ev.timestamp); p = null }
            is SessionEvent.SessionEnd -> p?.let { ps -> out.add(ps..ev.timestamp); p = null }
            else -> Unit
        }
    }
    return out
}

@Composable
private fun TimelineGap(gapMs: Long) {
    if (gapMs <= 0) return
    val seconds = gapMs / 1000.0
    val px = (sqrt(seconds) * 2.5).coerceIn(0.0, 88.0)
    val height = px.toFloat().dp
    if (seconds < 60) {
        Spacer(Modifier.height(height))
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TIMELINE_PAD)
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .width(40.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDurationShort(gapMs) + " després",
                    color = OnNightMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .width(40.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            }
        }
    }
}

@Composable
private fun AnalysisSliders(
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD),
        color = NightSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SliderBlock(
                icon = Icons.Filled.Layers,
                accent = Lavender,
                label = stringResource(R.string.grouping_window),
                value = groupSeconds,
                min = GROUP_MIN_S,
                max = GROUP_MAX_S,
                onChange = onGroupChange,
                hint = if (groupSeconds < 1f)
                    stringResource(R.string.grouping_off)
                else stringResource(
                    R.string.grouping_window_hint,
                    formatSeconds(groupSeconds.toInt()),
                ),
            )
            SliderBlock(
                icon = Icons.Filled.HourglassBottom,
                accent = MoonGlow,
                label = stringResource(R.string.min_interruption),
                value = minIntSeconds,
                min = MIN_INT_MIN_S,
                max = MIN_INT_MAX_S,
                onChange = onMinIntChange,
                hint = stringResource(
                    R.string.min_interruption_hint,
                    if (minIntSeconds < 1f) "0,${(minIntSeconds * 10).toInt()} s"
                    else formatSeconds(minIntSeconds.toInt()),
                ),
            )
        }
    }
}

@Composable
private fun SliderBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    hint: String,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = OnNight,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = hint,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.25f),
            ),
        )
    }
}

private fun formatSeconds(sec: Int): String = when {
    sec < 60 -> "$sec s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60} min ${sec % 60} s"
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
private fun SummaryCard(session: SleepSession, stats: SessionStats) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD),
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
            Text(
                text = formatDurationShort(stats.sleptDurationMs),
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
            Spacer(Modifier.height(14.dp))
            QualityBadge(score = stats.qualityScore)
        }
    }
}

@Composable
private fun CommentsSection(notes: String, onEditNotes: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD),
    ) {
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onEditNotes,
            colors = ButtonDefaults.buttonColors(
                containerColor = MoonGlow,
                contentColor = NightDeep,
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.comments),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val trimmed = notes.trim()
        if (trimmed.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = NightSurface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = trimmed,
                    color = OnNight,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
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
private fun PhasesSection(
    stats: SessionStats,
    legendBelow: Boolean,
    outerPadding: Dp = SECTION_PAD,
    chartMaxSize: Dp = 280.dp,
) {
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
    Column(Modifier.padding(horizontal = outerPadding)) {
        Text(
            text = stringResource(R.string.phases),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.widthIn(max = if (legendBelow) chartMaxSize else Dp.Infinity)) {
            PieChartWithLegend(
                slices = slices,
                centerLabel = formatDurationShort(stats.sleptDurationMs),
                centerSubLabel = stringResource(R.string.hours_slept),
                maxChartSize = chartMaxSize,
                legendBelow = legendBelow,
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: SessionStats, outerPadding: Dp = SECTION_PAD) {
    Column(
        Modifier.padding(horizontal = outerPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                value = "${stats.audioGroupCount} / ${stats.audioChunkCount}",
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
private fun SignalsCard(stats: SessionStats, outerPadding: Dp = SECTION_PAD) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = outerPadding),
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
    DetectedSignal.DOG_BARKING -> stringResource(R.string.signal_dog) to PinkDawn
    DetectedSignal.CAT_MEOWING -> stringResource(R.string.signal_cat) to Lavender
}
