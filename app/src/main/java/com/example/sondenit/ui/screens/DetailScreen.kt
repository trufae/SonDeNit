package com.example.sondenit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.sondenit.data.SoundClass
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
import com.example.sondenit.util.formatTimeOfDayLong
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val GROUP_MIN_S = 0f
private const val GROUP_MAX_S = 300f
private const val MIN_INT_MIN_S = 0f
private const val MIN_INT_MAX_S = 30f
private val WIDE_BREAKPOINT = 600.dp
private val SECTION_PAD = 20.dp
private val TIMELINE_PAD = 14.dp
private val EDITABLE_SOUND_CLASSES = listOf(
    SoundClass.SPEECH,
    SoundClass.SNORE,
    SoundClass.CAT_MEOW,
    SoundClass.DOG_BARK,
    SoundClass.COUGH,
    SoundClass.MOVEMENT,
    SoundClass.NOISE,
    SoundClass.UNKNOWN,
)

private data class PlaybackState(
    val file: String,
    val timestamp: Long,
)

@Composable
fun DetailScreen(
    repo: SessionRepository,
    session: SleepSession,
    playbackAmplificationAmount: Float,
    onBack: () -> Unit,
    onRename: (SleepSession, String) -> Unit,
    onUpdateNotes: (SleepSession, String) -> Unit,
    onDelete: (SleepSession) -> Unit,
) {
    val player = remember { AudioPlayer() }
    var playback by remember { mutableStateOf<PlaybackState?>(null) }
    var mapTimestamp by rememberSaveable(session.id) { mutableStateOf(session.startedAt) }
    var selectedTimestamp by rememberSaveable(session.id) { mutableStateOf<Long?>(null) }
    var events by remember(session.id, session.endedAt) { mutableStateOf(repo.readEvents(session.id)) }
    val sessionDir = remember(session.id) { repo.sessionDir(session.id) }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingNotes by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var editingAudioChunks by remember { mutableStateOf<List<SessionEvent.AudioChunk>?>(null) }
    var editingMotionEvent by remember { mutableStateOf<SessionEvent.Motion?>(null) }
    var changingAudioType by remember { mutableStateOf<List<SessionEvent.AudioChunk>?>(null) }
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
    val audioChunks = remember(events) {
        events.filterIsInstance<SessionEvent.AudioChunk>().sortedBy { it.timestamp }
    }
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
    val motionEvents = remember(events) {
        events.filterIsInstance<SessionEvent.Motion>().sortedBy { it.timestamp }
    }
    val labels = remember { defaultTimelineLabels() }

    DisposableEffectOnDispose { player.stop() }

    val sessionStart = session.startedAt
    val sessionEnd = session.endedAt ?: events.lastOrNull()?.timestamp ?: session.startedAt
    val playheadTimestamp = playback?.timestamp ?: selectedTimestamp ?: mapTimestamp

    val onPlayGroup: (NoiseGroup) -> Unit = { group ->
        val chunks = if (playback != null) {
            audioChunksFromGroupStart(audioChunks, group)
        } else {
            group.chunks
        }
        playAudioChunks(
            player = player,
            sessionDir = sessionDir,
            playbackAmplificationAmount = playbackAmplificationAmount,
            chunks = chunks,
            onStarted = { chunk ->
                playback = PlaybackState(chunk.file, chunk.timestamp)
                mapTimestamp = chunk.timestamp
            },
            onFinished = { playback = null },
        )
    }
    val onPlayAllAudio: () -> Unit = {
        playAudioChunks(
            player = player,
            sessionDir = sessionDir,
            playbackAmplificationAmount = playbackAmplificationAmount,
            chunks = audioChunks,
            onStarted = { chunk ->
                playback = PlaybackState(chunk.file, chunk.timestamp)
                mapTimestamp = chunk.timestamp
            },
            onFinished = { playback = null },
        )
    }
    val onStopPlayback: () -> Unit = {
        player.stop()
        playback = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))),
    ) {
        val isLandscape = maxWidth > maxHeight
        val isWide = maxWidth >= WIDE_BREAKPOINT
        val topPadding = maxOf(
            32.dp,
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
        )

        Column(Modifier.fillMaxSize().padding(top = topPadding)) {
            TopBar(
                title = session.displayName,
                subtitle = formatDateLong(session.startedAt),
                canPlayAllAudio = audioChunks.isNotEmpty(),
                onBack = onBack,
                onPlayAllAudio = onPlayAllAudio,
                onRename = { renaming = true },
                onEditNotes = {
                    notesDraft = session.notes
                    editingNotes = true
                },
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
                    motionEvents = motionEvents,
                    labels = labels,
                    playback = playback,
                    selectedTimestamp = selectedTimestamp,
                    audioCount = audioChunks.size,
                    playheadTimestamp = playheadTimestamp,
                    onPlayGroup = onPlayGroup,
                    onPlayAllAudio = onPlayAllAudio,
                    onStopPlayback = onStopPlayback,
                    onMapSeek = {
                        mapTimestamp = it
                        selectedTimestamp = it
                    },
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
                    },
                    onEditAudioRow = { editingAudioChunks = it },
                    onEditMotionEvent = { editingMotionEvent = it },
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
                    motionEvents = motionEvents,
                    labels = labels,
                    playback = playback,
                    selectedTimestamp = selectedTimestamp,
                    audioCount = audioChunks.size,
                    playheadTimestamp = playheadTimestamp,
                    onPlayGroup = onPlayGroup,
                    onPlayAllAudio = onPlayAllAudio,
                    onStopPlayback = onStopPlayback,
                    onMapSeek = {
                        mapTimestamp = it
                        selectedTimestamp = it
                    },
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
                    },
                    onEditAudioRow = { editingAudioChunks = it },
                    onEditMotionEvent = { editingMotionEvent = it },
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
                    motionEvents = motionEvents,
                    labels = labels,
                    playback = playback,
                    selectedTimestamp = selectedTimestamp,
                    audioCount = audioChunks.size,
                    playheadTimestamp = playheadTimestamp,
                    onPlayGroup = onPlayGroup,
                    onPlayAllAudio = onPlayAllAudio,
                    onStopPlayback = onStopPlayback,
                    onMapSeek = {
                        mapTimestamp = it
                        selectedTimestamp = it
                    },
                    groupSeconds = groupSeconds,
                    onGroupChange = { groupSeconds = it },
                    minIntSeconds = minIntSeconds,
                    onMinIntChange = { minIntSeconds = it },
                    onEditNotes = {
                        notesDraft = session.notes
                        editingNotes = true
                    },
                    onEditAudioRow = { editingAudioChunks = it },
                    onEditMotionEvent = { editingMotionEvent = it },
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
    editingAudioChunks?.let { chunks ->
        AudioChunkActionsDialog(
            chunks = chunks,
            onDismiss = { editingAudioChunks = null },
            onFavorite = {
                repo.markAudioChunksFavorite(session.id, chunks)
                events = repo.readEvents(session.id)
                editingAudioChunks = null
            },
            onDelete = {
                val files = chunks.map { it.file }.toSet()
                if (playback?.file in files) {
                    player.stop()
                    playback = null
                }
                repo.deleteAudioChunks(session.id, chunks)
                events = repo.readEvents(session.id)
                editingAudioChunks = null
            },
            onChangeType = {
                changingAudioType = chunks
                editingAudioChunks = null
            },
        )
    }
    editingMotionEvent?.let { event ->
        MotionEventActionsDialog(
            onDismiss = { editingMotionEvent = null },
            onFavorite = {
                repo.markMotionEventsFavorite(session.id, listOf(event))
                events = repo.readEvents(session.id)
                editingMotionEvent = null
            },
            onDelete = {
                if (selectedTimestamp == event.timestamp) {
                    selectedTimestamp = null
                }
                repo.deleteMotionEvents(session.id, listOf(event))
                events = repo.readEvents(session.id)
                editingMotionEvent = null
            },
        )
    }
    changingAudioType?.let { chunks ->
        AudioTypeDialog(
            chunks = chunks,
            onDismiss = { changingAudioType = null },
            onSelect = { klass ->
                repo.changeAudioChunksClass(session.id, chunks, klass)
                events = repo.readEvents(session.id)
                changingAudioType = null
            },
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
    motionEvents: List<SessionEvent.Motion>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playback: PlaybackState?,
    selectedTimestamp: Long?,
    audioCount: Int,
    playheadTimestamp: Long?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onPlayAllAudio: () -> Unit,
    onStopPlayback: () -> Unit,
    onMapSeek: (Long) -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
    onEditAudioRow: (List<SessionEvent.AudioChunk>) -> Unit,
    onEditMotionEvent: (SessionEvent.Motion) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val timelineHeaderIndex = compactTimelineHeaderIndex(stats.sleptDurationMs > 0)
    LaunchedEffect(playback?.timestamp, timelineRows) {
        playback?.timestamp?.let { ts ->
            timelineListIndexForTimestamp(timelineHeaderIndex, timelineRows, ts)?.let { idx ->
                listState.animateScrollToItem(idx)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { SummaryCard(session, stats) }
        item { NotesSection(session.notes, onEditNotes) }
        if (stats.sleptDurationMs > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                PhasesSection(
                    stats = stats,
                    legendBelow = false,
                    chartMaxSize = 180.dp,
                )
            }
            item { Spacer(Modifier.height(16.dp)); SignalsCard(stats) }
            item {
                Spacer(Modifier.height(16.dp))
                EventRibbon(
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd,
                    groups = significantGroups,
                    screenOnTimestamps = screenOnTimestamps,
                    motionEvents = motionEvents,
                    pausedRanges = pausedRanges,
                    playheadTimestamp = playheadTimestamp,
                    onSeekTimestamp = { ts ->
                        onMapSeek(ts)
                    },
                    onSeekFinished = { ts ->
                        scrollTimelineTo(scope, listState, timelineHeaderIndex, timelineRows, ts)
                    },
                    title = stringResource(R.string.event_ribbon),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
            }
            item { Spacer(Modifier.height(16.dp)); StatsGrid(stats) }
        }
        timelineSection(
            timelineRows = timelineRows,
            labels = labels,
            playback = playback,
            selectedTimestamp = selectedTimestamp,
            audioCount = audioCount,
            onPlayAllAudio = onPlayAllAudio,
            onPlayGroup = onPlayGroup,
            onStopPlayback = onStopPlayback,
            onEditAudioRow = onEditAudioRow,
            onEditMotionEvent = onEditMotionEvent,
        )
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
    motionEvents: List<SessionEvent.Motion>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playback: PlaybackState?,
    selectedTimestamp: Long?,
    audioCount: Int,
    playheadTimestamp: Long?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onPlayAllAudio: () -> Unit,
    onStopPlayback: () -> Unit,
    onMapSeek: (Long) -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
    onEditAudioRow: (List<SessionEvent.AudioChunk>) -> Unit,
    onEditMotionEvent: (SessionEvent.Motion) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val timelineHeaderIndex = widePortraitTimelineHeaderIndex(stats.sleptDurationMs > 0)
    LaunchedEffect(playback?.timestamp, timelineRows) {
        playback?.timestamp?.let { ts ->
            timelineListIndexForTimestamp(timelineHeaderIndex, timelineRows, ts)?.let { idx ->
                listState.animateScrollToItem(idx)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { SummaryCard(session, stats) }
        item { NotesSection(session.notes, onEditNotes) }
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
                        Column {
                            PhasesSection(
                                stats = stats,
                                legendBelow = false,
                                outerPadding = 0.dp,
                                chartMaxSize = 180.dp,
                            )
                            Spacer(Modifier.height(16.dp))
                            SignalsCard(stats, outerPadding = 0.dp)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatsGrid(stats, outerPadding = 0.dp)
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
                    motionEvents = motionEvents,
                    pausedRanges = pausedRanges,
                    playheadTimestamp = playheadTimestamp,
                    onSeekTimestamp = { ts ->
                        onMapSeek(ts)
                    },
                    onSeekFinished = { ts ->
                        scrollTimelineTo(scope, listState, timelineHeaderIndex, timelineRows, ts)
                    },
                    title = stringResource(R.string.event_ribbon),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
            }
        }
        timelineSection(
            timelineRows = timelineRows,
            labels = labels,
            playback = playback,
            selectedTimestamp = selectedTimestamp,
            audioCount = audioCount,
            onPlayAllAudio = onPlayAllAudio,
            onPlayGroup = onPlayGroup,
            onStopPlayback = onStopPlayback,
            onEditAudioRow = onEditAudioRow,
            onEditMotionEvent = onEditMotionEvent,
        )
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
    motionEvents: List<SessionEvent.Motion>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playback: PlaybackState?,
    selectedTimestamp: Long?,
    audioCount: Int,
    playheadTimestamp: Long?,
    onPlayGroup: (NoiseGroup) -> Unit,
    onPlayAllAudio: () -> Unit,
    onStopPlayback: () -> Unit,
    onMapSeek: (Long) -> Unit,
    groupSeconds: Float,
    onGroupChange: (Float) -> Unit,
    minIntSeconds: Float,
    onMinIntChange: (Float) -> Unit,
    onEditNotes: () -> Unit,
    onEditAudioRow: (List<SessionEvent.AudioChunk>) -> Unit,
    onEditMotionEvent: (SessionEvent.Motion) -> Unit,
) {
    val timelineState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(playback?.timestamp, timelineRows) {
        playback?.timestamp?.let { ts ->
            timelineListIndexForTimestamp(0, timelineRows, ts)?.let { idx ->
                timelineState.animateScrollToItem(idx)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left half: stats / sliders / signals
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item { SummaryCard(session, stats) }
            item { NotesSection(session.notes, onEditNotes) }
            if (stats.sleptDurationMs > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PhasesSection(
                        stats = stats,
                        legendBelow = false,
                        chartMaxSize = 220.dp,
                    )
                }
                item { Spacer(Modifier.height(16.dp)); SignalsCard(stats) }
                item {
                    Spacer(Modifier.height(16.dp))
                    EventRibbon(
                        sessionStart = sessionStart,
                        sessionEnd = sessionEnd,
                        groups = significantGroups,
                        screenOnTimestamps = screenOnTimestamps,
                        motionEvents = motionEvents,
                        pausedRanges = pausedRanges,
                        playheadTimestamp = playheadTimestamp,
                        onSeekTimestamp = { ts ->
                            onMapSeek(ts)
                            scrollTimelineTo(scope, timelineState, 0, timelineRows, ts)
                        },
                        title = stringResource(R.string.event_ribbon),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PAD),
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    AnalysisSliders(groupSeconds, onGroupChange, minIntSeconds, onMinIntChange)
                }
                item { Spacer(Modifier.height(16.dp)); StatsGrid(stats) }
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
            state = timelineState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            timelineSection(
                timelineRows = timelineRows,
                labels = labels,
                playback = playback,
                selectedTimestamp = selectedTimestamp,
                audioCount = audioCount,
                onPlayAllAudio = onPlayAllAudio,
                onPlayGroup = onPlayGroup,
                onStopPlayback = onStopPlayback,
                onEditAudioRow = onEditAudioRow,
                onEditMotionEvent = onEditMotionEvent,
            )
        }
    }
}

// ── timeline (shared LazyListScope extension) ──────────────────────────────

private fun LazyListScope.timelineSection(
    timelineRows: List<TimelineRowData>,
    labels: com.example.sondenit.ui.components.TimelineLabels,
    playback: PlaybackState?,
    selectedTimestamp: Long?,
    audioCount: Int,
    onPlayAllAudio: () -> Unit,
    onPlayGroup: (NoiseGroup) -> Unit,
    onStopPlayback: () -> Unit,
    onEditAudioRow: (List<SessionEvent.AudioChunk>) -> Unit,
    onEditMotionEvent: (SessionEvent.Motion) -> Unit,
) {
    item {
        Spacer(Modifier.height(20.dp))
        TimelinePlaybackControl(
            audioCount = audioCount,
            currentTimestamp = playback?.timestamp,
            isPlaying = playback != null,
            onPlayAll = onPlayAllAudio,
            onStop = onStopPlayback,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.timeline),
            color = OnNight,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = SECTION_PAD),
        )
        Spacer(Modifier.height(4.dp))
    }
    val selectedIndex = selectedTimestamp?.let { timestamp ->
        timelineRows.withIndex().minByOrNull { (_, row) ->
            abs(row.timestamp - timestamp)
        }?.index
    }
    items(timelineRows.size) { idx ->
        val row = timelineRows[idx]
        val prev = if (idx == 0) null else timelineRows[idx - 1]
        val gap = if (prev == null) 0L else (row.timestamp - prev.timestamp)
        if (gap > 0) TimelineGap(gap)
        Box(modifier = Modifier.padding(horizontal = TIMELINE_PAD)) {
            when (row) {
                is TimelineRowData.Event -> {
                    val motionEvent = row.event as? SessionEvent.Motion
                    TimelineRow(
                        spec = describe(row.event, labels),
                        showLineAbove = idx > 0,
                        showLineBelow = idx < timelineRows.size - 1,
                        selected = idx == selectedIndex,
                        onLongClick = motionEvent?.let { event ->
                            { onEditMotionEvent(event) }
                        },
                    )
                }
                is TimelineRowData.Group -> {
                    val isPlaying = playback?.file?.let { file ->
                        row.group.chunks.any { it.file == file }
                    } == true
                    TimelineRow(
                        spec = describeGroup(row.group)
                            .copy(playing = isPlaying),
                        showLineAbove = idx > 0,
                        showLineBelow = idx < timelineRows.size - 1,
                        selected = idx == selectedIndex,
                        onPlay = { onPlayGroup(row.group) },
                        onStop = onStopPlayback,
                        onLongClick = { onEditAudioRow(row.group.chunks) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioChunkActionsDialog(
    chunks: List<SessionEvent.AudioChunk>,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onChangeType: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Text(
                text = if (chunks.size == 1)
                    stringResource(R.string.recording_actions_title)
                else stringResource(R.string.recording_group_actions_title, chunks.size),
            )
        },
        text = {
            Column {
                DialogActionButton(
                    icon = Icons.Filled.Star,
                    text = stringResource(R.string.mark_as_favorite),
                    tint = MoonGlow,
                    onClick = onFavorite,
                )
                DialogActionButton(
                    icon = Icons.Filled.Delete,
                    text = stringResource(R.string.delete_recording),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
                DialogActionButton(
                    icon = Icons.Filled.Edit,
                    text = stringResource(R.string.change_sound_type),
                    tint = SkyTeal,
                    onClick = onChangeType,
                )
            }
        },
    )
}

@Composable
private fun MotionEventActionsDialog(
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.motion_actions_title)) },
        text = {
            Column {
                DialogActionButton(
                    icon = Icons.Filled.Star,
                    text = stringResource(R.string.mark_as_favorite),
                    tint = MoonGlow,
                    onClick = onFavorite,
                )
                DialogActionButton(
                    icon = Icons.Filled.Delete,
                    text = stringResource(R.string.delete_motion_event),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        },
    )
}

@Composable
private fun AudioTypeDialog(
    chunks: List<SessionEvent.AudioChunk>,
    onDismiss: () -> Unit,
    onSelect: (SoundClass) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.change_sound_type_title)) },
        text = {
            Column {
                EDITABLE_SOUND_CLASSES.forEach { klass ->
                    DialogActionButton(
                        icon = Icons.Filled.GraphicEq,
                        text = soundClassLabel(klass),
                        tint = soundClassColor(klass),
                        onClick = { onSelect(klass) },
                    )
                }
            }
        },
    )
}

@Composable
private fun DialogActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = if (tint == MaterialTheme.colorScheme.error) tint else OnNight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun soundClassLabel(klass: SoundClass): String = when (klass) {
    SoundClass.SPEECH -> stringResource(R.string.sound_type_voice)
    SoundClass.SNORE -> stringResource(R.string.sound_type_snore)
    SoundClass.CAT_MEOW -> stringResource(R.string.sound_type_cat)
    SoundClass.DOG_BARK -> stringResource(R.string.sound_type_dog)
    SoundClass.COUGH -> stringResource(R.string.sound_type_cough)
    SoundClass.MOVEMENT -> stringResource(R.string.sound_type_movement)
    SoundClass.NOISE -> stringResource(R.string.sound_type_noise)
    SoundClass.UNKNOWN -> stringResource(R.string.sound_type_unknown)
}

private fun soundClassColor(klass: SoundClass): Color = when (klass) {
    SoundClass.SPEECH -> Lavender
    SoundClass.COUGH -> PinkDawn
    SoundClass.MOVEMENT -> SkyTeal
    SoundClass.SNORE -> MoonGlow
    SoundClass.DOG_BARK -> PinkDawn
    SoundClass.CAT_MEOW -> Lavender
    SoundClass.NOISE -> OnNightMuted
    SoundClass.UNKNOWN -> OnNightMuted
}

@Composable
private fun TimelinePlaybackControl(
    audioCount: Int,
    currentTimestamp: Long?,
    isPlaying: Boolean,
    onPlayAll: () -> Unit,
    onStop: () -> Unit,
) {
    val enabled = audioCount > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TIMELINE_PAD, vertical = 4.dp),
        color = NightSurface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = if (enabled) MoonGlow else OnNightMuted
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(accent.copy(alpha = if (enabled) 0.95f else 0.25f), RoundedCornerShape(50))
                    .clickable(enabled = enabled) {
                        if (isPlaying) onStop() else onPlayAll()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying)
                        stringResource(R.string.stop_chunk)
                    else stringResource(R.string.play_all_audio),
                    tint = NightDeep,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.play_all_audio),
                    color = OnNight,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        currentTimestamp != null -> stringResource(
                            R.string.now_playing_audio_time,
                            formatTimeOfDayLong(currentTimestamp),
                        )
                        enabled -> stringResource(R.string.audio_recording_count, audioCount)
                        else -> stringResource(R.string.no_audio_recorded)
                    },
                    color = if (currentTimestamp != null) MoonGlow else OnNightMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
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

private fun audioChunksFromGroupStart(
    audioChunks: List<SessionEvent.AudioChunk>,
    group: NoiseGroup,
): List<SessionEvent.AudioChunk> {
    val firstChunk = group.chunks.firstOrNull() ?: return emptyList()
    val startIndex = audioChunks.indexOfFirst { it.file == firstChunk.file }
    return if (startIndex >= 0) audioChunks.drop(startIndex) else group.chunks
}

private fun playAudioChunks(
    player: AudioPlayer,
    sessionDir: File,
    playbackAmplificationAmount: Float,
    chunks: List<SessionEvent.AudioChunk>,
    onStarted: (SessionEvent.AudioChunk) -> Unit,
    onFinished: () -> Unit,
) {
    player.stop()

    fun playAt(index: Int) {
        if (index >= chunks.size) {
            onFinished()
            return
        }

        val chunk = chunks[index]
        val file = File(sessionDir, chunk.file)
        if (!file.exists()) {
            playAt(index + 1)
            return
        }

        runCatching {
            player.play(file, playbackAmplificationAmount) { playAt(index + 1) }
        }.onSuccess {
            onStarted(chunk)
        }.onFailure {
            playAt(index + 1)
        }
    }

    playAt(0)
}

private fun compactTimelineHeaderIndex(showStats: Boolean): Int =
    2 + if (showStats) 5 else 0

private fun widePortraitTimelineHeaderIndex(showStats: Boolean): Int =
    2 + if (showStats) 3 else 0

private fun scrollTimelineTo(
    scope: CoroutineScope,
    state: LazyListState,
    timelineHeaderIndex: Int,
    timelineRows: List<TimelineRowData>,
    timestamp: Long,
) {
    val index = timelineListIndexForTimestamp(timelineHeaderIndex, timelineRows, timestamp) ?: return
    scope.launch {
        state.animateScrollToItem(index)
    }
}

private fun timelineListIndexForTimestamp(
    timelineHeaderIndex: Int,
    timelineRows: List<TimelineRowData>,
    timestamp: Long,
): Int? {
    if (timelineRows.isEmpty()) return null
    val nearest = timelineRows.withIndex().minByOrNull { (_, row) ->
        abs(row.timestamp - timestamp)
    }?.index ?: return null
    return timelineHeaderIndex + 1 + nearest
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
    canPlayAllAudio: Boolean,
    onBack: () -> Unit,
    onPlayAllAudio: () -> Unit,
    onRename: () -> Unit,
    onEditNotes: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onRename)
        ) {
            Text(
                text = title,
                color = OnNight,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = OnNightMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.session_menu),
                    tint = OnNight,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_play_all_audio)) },
                    enabled = canPlayAllAudio,
                    onClick = {
                        menuExpanded = false
                        onPlayAllAudio()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_edit_title)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_annotations)) },
                    onClick = {
                        menuExpanded = false
                        onEditNotes()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.menu_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    session: SleepSession,
    stats: SessionStats,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD),
        shape = RoundedCornerShape(24.dp),
        color = NightSurface,
    ) {
        Column(Modifier.padding(16.dp)) {
            val range = if (session.endedAt != null) {
                "${formatTimeOfDay(session.startedAt)} – ${formatTimeOfDay(session.endedAt)}"
            } else {
                formatTimeOfDay(session.startedAt) + " — en curs"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                QualityBadge(score = stats.qualityScore)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDurationShort(stats.sleptDurationMs),
                    color = OnNight,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.hours_slept),
                        color = OnNightMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = range,
                        color = OnNightMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesSection(notes: String, onEditNotes: () -> Unit) {
    val trimmed = notes.trim()
    if (trimmed.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD),
    ) {
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditNotes),
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

@Composable
private fun QualityBadge(score: Int) {
    val (color, label) = qualityColor(score)
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.Vibration,
                accent = SkyTeal,
                label = stringResource(R.string.movement_events),
                value = stats.movementEvents.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.Insights,
                accent = Lavender,
                label = stringResource(R.string.wake_movement_events),
                value = stats.wakeMovementEvents.toString(),
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
