package com.example.sondenit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SleepSession
import com.example.sondenit.audio.AudioSettings
import com.example.sondenit.service.RecordingController
import com.example.sondenit.service.RecordingState
import com.example.sondenit.ui.screens.BreathingScreen
import com.example.sondenit.ui.screens.DetailScreen
import com.example.sondenit.ui.screens.HomeScreen
import com.example.sondenit.ui.screens.RecordingScreen
import com.example.sondenit.ui.screens.SettingsScreen
import com.example.sondenit.ui.screens.StartCountdownScreen
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted
import com.example.sondenit.ui.theme.SonDeNitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SonDeNitTheme {
                AppRoot()
            }
        }
    }
}

private sealed class Screen {
    object Home : Screen()
    object Breathing : Screen()
    object Countdown : Screen()
    object Recording : Screen()
    object Settings : Screen()
    data class Detail(val sessionId: String) : Screen()
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context.applicationContext) }

    var sessions by remember { mutableStateOf(repo.listSessions()) }
    val activeSession by RecordingController.activeSession.collectAsState()
    val state by RecordingController.state.collectAsState()
    val level by RecordingController.level.collectAsState()
    val waveform by RecordingController.waveform.collectAsState()
    val recentEvents by RecordingController.recentEvents.collectAsState()
    val chunkCount by RecordingController.chunkCount.collectAsState()

    var equalizationAmount by remember {
        mutableStateOf(AudioSettings.equalizationAmount(context.applicationContext))
    }
    var recordingStartDelaySeconds by remember {
        mutableStateOf(AudioSettings.recordingStartDelaySeconds(context.applicationContext))
    }
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Home)
    }
    var pendingPermissionStart by rememberSaveable { mutableStateOf(false) }
    var permissionDeniedSticky by rememberSaveable { mutableStateOf(false) }

    val micGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun beginSessionStart() {
        if (recordingStartDelaySeconds <= 0) {
            startSession(repo, context, screen) { newScreen -> screen = newScreen }
            sessions = repo.listSessions()
        } else {
            screen = Screen.Countdown
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedSticky = false
            if (pendingPermissionStart) {
                pendingPermissionStart = false
                beginSessionStart()
            }
        } else {
            pendingPermissionStart = false
            permissionDeniedSticky = true
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; we keep going either way */ }

    // On first launch, recover an in-progress session if there's an active marker.
    LaunchedEffect(Unit) {
        repo.activeSession()?.let { existing ->
            // If the service is running, RecordingController.activeSession will be set
            // already. Otherwise, surface the resumable session via the controller so
            // the home button shows "Continua".
            if (RecordingController.activeSession.value == null) {
                // We don't auto-start the service — we let the user press "Continua".
                // The active marker stays and startSession() will reuse the same id.
            }
            // Also expose it to the home screen by including it in `sessions`:
            sessions = repo.listSessions()
            // If the controller is currently recording, jump straight into that screen.
            if (RecordingController.state.value != RecordingState.IDLE) {
                screen = Screen.Recording
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NightDeep)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    )
                ),
        ) {
            when (val s = screen) {
                Screen.Home -> {
                    if (!micGranted && permissionDeniedSticky) {
                        PermissionDeniedScreen(onRetry = {
                            permissionDeniedSticky = false
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        })
                    } else {
                        HomeScreen(
                            repo = repo,
                            sessions = sessions,
                            activeSession = activeSession ?: repo.activeSession(),
                            onPrimaryAction = {
                                if (!micGranted) {
                                    pendingPermissionStart = true
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    beginSessionStart()
                                }
                            },
                            onOpenBreathing = { screen = Screen.Breathing },
                            onOpenSession = { sess ->
                                screen = if (sess.endedAt == null) Screen.Recording
                                         else Screen.Detail(sess.id)
                            },
                            onOpenSettings = { screen = Screen.Settings },
                            onRename = { sess, newName ->
                                repo.rename(sess.id, newName)
                                sessions = repo.listSessions()
                            },
                            onDelete = { sess ->
                                repo.delete(sess.id)
                                sessions = repo.listSessions()
                            },
                        )
                    }
                }
                Screen.Breathing -> {
                    BreathingScreen(
                        onClose = { screen = Screen.Home },
                    )
                }
                Screen.Countdown -> {
                    StartCountdownScreen(
                        delaySeconds = recordingStartDelaySeconds,
                        onFinished = {
                            startSession(repo, context, screen) { newScreen -> screen = newScreen }
                            sessions = repo.listSessions()
                        },
                        onCancel = {
                            screen = Screen.Home
                            sessions = repo.listSessions()
                        },
                    )
                }
                Screen.Settings -> {
                    SettingsScreen(
                        micGranted = micGranted,
                        equalizationAmount = equalizationAmount,
                        recordingStartDelaySeconds = recordingStartDelaySeconds,
                        onEqualizationChange = { amount ->
                            equalizationAmount = amount
                            AudioSettings.setEqualizationAmount(context.applicationContext, amount)
                        },
                        onRecordingStartDelayChange = { seconds ->
                            recordingStartDelaySeconds = seconds
                            AudioSettings.setRecordingStartDelaySeconds(context.applicationContext, seconds)
                        },
                        onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onBack = { screen = Screen.Home },
                    )
                }
                Screen.Recording -> {
                    val current = activeSession ?: repo.activeSession()
                    if (current == null || state == RecordingState.IDLE) {
                        // The session ended elsewhere — bounce back to home.
                        LaunchedEffect(Unit) {
                            sessions = repo.listSessions()
                            screen = Screen.Home
                        }
                    } else {
                        RecordingScreen(
                            session = current,
                            state = state,
                            level = level,
                            waveform = waveform,
                            recentEvents = recentEvents,
                            chunkCount = chunkCount,
                            onPause = { RecordingController.pause(context) },
                            onResume = { RecordingController.resume(context) },
                            onStop = { RecordingController.stop(context) },
                            onBack = { screen = Screen.Home; sessions = repo.listSessions() },
                        )
                    }
                }
                is Screen.Detail -> {
                    val session = remember(s.sessionId, sessions) { repo.readSession(s.sessionId) }
                    if (session == null) {
                        LaunchedEffect(Unit) { screen = Screen.Home }
                    } else {
                        DetailScreen(
                            repo = repo,
                            session = session,
                            onBack = { screen = Screen.Home; sessions = repo.listSessions() },
                            onRename = { sess, newName ->
                                repo.rename(sess.id, newName)
                                sessions = repo.listSessions()
                            },
                            onUpdateNotes = { sess, notes ->
                                repo.updateNotes(sess.id, notes)
                                sessions = repo.listSessions()
                            },
                            onDelete = { sess ->
                                repo.delete(sess.id)
                                sessions = repo.listSessions()
                                screen = Screen.Home
                            },
                        )
                    }
                }
            }
        }
    }

    // Auto-refresh sessions when a recording session ends (state transitions to IDLE).
    LaunchedEffect(state) {
        if (state == RecordingState.IDLE) {
            sessions = repo.listSessions()
        }
    }
}

private fun startSession(
    repo: SessionRepository,
    context: android.content.Context,
    @Suppress("UNUSED_PARAMETER") currentScreen: Screen,
    setScreen: (Screen) -> Unit,
) {
    RecordingController.startSession(context, repo)
    setScreen(Screen.Recording)
}

@Composable
private fun PermissionDeniedScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightDeep, NightMid, NightDeep))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MoonGlow,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.perm_title),
                color = OnNight,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.perm_message),
                color = OnNightMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoonGlow,
                    contentColor = NightDeep,
                ),
            ) {
                Text(
                    text = stringResource(R.string.perm_grant),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            Screen.Home -> "home"
            Screen.Breathing -> "breathing"
            Screen.Countdown -> "countdown"
            Screen.Recording -> "rec"
            Screen.Settings -> "settings"
            is Screen.Detail -> "detail:${screen.sessionId}"
        }
    },
    restore = { value ->
        when {
            value == "home" -> Screen.Home
            value == "breathing" -> Screen.Breathing
            value == "countdown" -> Screen.Countdown
            value == "rec" -> Screen.Recording
            value == "settings" -> Screen.Settings
            value.startsWith("detail:") -> Screen.Detail(value.removePrefix("detail:"))
            else -> Screen.Home
        }
    },
)
