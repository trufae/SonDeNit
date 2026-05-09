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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sondenit.R
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SleepSession
import com.example.sondenit.ui.components.SessionRow
import com.example.sondenit.ui.theme.Lavender
import com.example.sondenit.ui.theme.MoonGlow
import com.example.sondenit.ui.theme.NightDeep
import com.example.sondenit.ui.theme.NightMid
import com.example.sondenit.ui.theme.OnNight
import com.example.sondenit.ui.theme.OnNightMuted

@Composable
fun HomeScreen(
    repo: SessionRepository,
    sessions: List<SleepSession>,
    activeSession: SleepSession?,
    onPrimaryAction: () -> Unit,
    onOpenSession: (SleepSession) -> Unit,
    onRename: (SleepSession, String) -> Unit,
    onDelete: (SleepSession) -> Unit,
) {
    var renaming by rememberSaveable(stateSaver = sessionIdSaver) { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(stateSaver = sessionIdSaver) { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NightDeep, NightMid, NightDeep),
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = 56.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Header()
            }
            item {
                Spacer(Modifier.height(20.dp))
                PrimaryActionCard(
                    isResume = activeSession != null,
                    onClick = onPrimaryAction,
                )
                Spacer(Modifier.height(16.dp))
            }
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
                    val score = remember(session.id, session.endedAt) {
                        if (session.endedAt == null) null else repo.readStats(session.id)?.qualityScore
                    }
                    SessionRow(
                        session = session,
                        durationMs = durationMs,
                        qualityScore = score,
                        isActive = activeSession?.id == session.id,
                        onClick = { onOpenSession(session) },
                        onLongClick = {
                            // Long-press shows a tiny inline menu via dialogs.
                            // We just trigger the rename dialog by default and show
                            // a delete confirmation when the user picks delete.
                            renaming = session.id
                        },
                    )
                }
            }
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
private fun Header() {
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
        Column {
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
    }
}

@Composable
private fun PrimaryActionCard(isResume: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = stringResource(
                    if (isResume) R.string.continue_sleep else R.string.start_sleep
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
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
