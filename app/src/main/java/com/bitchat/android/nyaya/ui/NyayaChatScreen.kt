package com.bitchat.android.nyaya.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.ui.components.NyayaBrandMark
import com.bitchat.android.nyaya.ui.components.NyayaInputBar
import com.bitchat.android.nyaya.ui.components.NyayaTopBar
import com.bitchat.android.nyaya.ui.theme.NyayaPill
import com.bitchat.android.nyaya.ui.theme.NyayaTheme
import kotlinx.coroutines.launch

/**
 * The conversation screen.
 *
 * The user's messages sit in a tinted pill on the right; the assistant's answers
 * are plain text on the canvas with no bubble. That asymmetry is deliberate — a
 * legal answer runs to several paragraphs with section numbers, and wrapping that
 * in a bubble with a max width makes it markedly harder to read. Each answer gets
 * copy and read-aloud actions underneath, because people need to forward these to
 * a family member or a lawyer.
 */
@Composable
fun NyayaChatScreen(
    state: NyayaViewModel.UiState,
    onSend: (String) -> Unit,
    onVoiceMode: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onActions: () -> Unit,
    onNewChat: () -> Unit,
    onStop: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.generating) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NyayaTheme.gradients.canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            NyayaTopBar(
                engineLabel = state.engineLabel,
                incognito = state.incognito,
                onOpenDrawer = onOpenDrawer,
                onEngineClick = onOpenSettings,
                onNewChat = onNewChat
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 4.dp,
                    bottom = 12.dp
                )
            ) {
                item { DisclaimerNote() }

                items(state.messages) { message ->
                    if (message.role == ChatTurn.Role.USER) {
                        UserMessage(text = message.text)
                    } else {
                        AssistantMessage(text = message.text, onSpeak = { onSpeak(message.text) })
                    }
                }

                if (state.generating) {
                    item { ThinkingIndicator() }
                }

                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            NyayaInputBar(
                hint = "Ask a follow-up",
                generating = state.generating,
                onSend = onSend,
                onVoiceMode = onVoiceMode,
                onActions = onActions,
                onStop = onStop
            )
        }
    }
}

/**
 * The standing disclaimer, with the helpline numbers.
 *
 * It stays at the top of the transcript rather than appearing once and scrolling
 * away, because "this is information, not advice" has to be visible next to the
 * advice, not before it.
 */
@Composable
private fun DisclaimerNote() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), NyayaPill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Gavel,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = "Legal information, not legal advice \u00B7 Free legal aid 15100 \u00B7 " +
                "Emergency 112",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun AssistantMessage(text: String, onSpeak: () -> Unit) {
    // LocalClipboard rather than the deprecated LocalClipboardManager: the new
    // API is suspending, so the copy runs in the composition's scope.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageAction(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("Nyaya AI answer", text))
                        )
                    }
                }
            )
            MessageAction(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                label = "Read aloud",
                onClick = onSpeak
            )
        }
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), NyayaPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The brand mark breathing, instead of a spinner and the word "Thinking". */
@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NyayaBrandMark(size = 20.dp, animated = true)
        Text(
            text = "Reading the law\u2026",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
