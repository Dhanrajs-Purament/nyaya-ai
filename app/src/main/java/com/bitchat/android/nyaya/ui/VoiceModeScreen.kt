package com.bitchat.android.nyaya.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.ui.components.VoiceOrb
import com.bitchat.android.nyaya.ui.theme.NyayaTheme

/**
 * Hands-free voice mode.
 *
 * A single orb, one line of status, and a row of circular controls. Voice mode
 * exists for users who cannot type comfortably or read easily, so it shows almost
 * no text: the orb reacts to their voice, and the answer is spoken. The last
 * answer is still printed underneath for anyone who wants to check a section
 * number they just heard.
 */
@Composable
fun VoiceModeScreen(
    state: NyayaViewModel.UiState,
    onClose: () -> Unit,
    onMicTap: () -> Unit,
    onOpenTranscript: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NyayaTheme.gradients.canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            VoiceOrb(
                listening = state.listening,
                thinking = state.generating,
                level = state.micLevel
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = when {
                    state.listening -> "Listening\u2026"
                    state.generating -> "Reading the law\u2026"
                    else -> "Tap the mic and ask your question"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            val last = state.messages.lastOrNull()
            if (last != null && last.role == ChatTurn.Role.ASSISTANT) {
                Spacer(modifier = Modifier.height(20.dp))
                // Capped height rather than a third weighted child: competing
                // weights would fight the two spacers that centre the orb.
                Box(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = last.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(bottom = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                VoiceControl(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = "Show the written conversation",
                    onClick = onOpenTranscript
                )
                VoiceControl(
                    icon = if (state.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (state.listening) "Stop listening" else "Start listening",
                    primary = true,
                    onClick = onMicTap
                )
                VoiceControl(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Leave voice mode",
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun VoiceControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val diameter = if (primary) 68.dp else 52.dp
    Box(
        modifier = Modifier
            .size(diameter)
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(if (primary) 28.dp else 20.dp),
            tint = if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
