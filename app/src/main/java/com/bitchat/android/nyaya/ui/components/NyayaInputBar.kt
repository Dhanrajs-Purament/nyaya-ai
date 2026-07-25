package com.bitchat.android.nyaya.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.theme.NyayaPill

/**
 * The floating pill input bar, shared by the home and chat screens.
 *
 * Layout, left to right: a `+` that opens the actions sheet, the text field,
 * a mic that starts hands-free voice mode, and a single trailing action that is
 * *either* send or stop — never both, because offering "stop" while a reply is
 * streaming and "send" at the same time invites a double submit.
 *
 * The bar owns its own text state so the parent screen does not recompose on
 * every keystroke; [onSend] is the only thing that leaves it.
 */
@Composable
fun NyayaInputBar(
    hint: String,
    generating: Boolean,
    onSend: (String) -> Unit,
    onVoiceMode: () -> Unit,
    onActions: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    val canSend = enabled && !generating && text.isNotBlank()

    fun submit() {
        if (!canSend) return
        onSend(text.trim())
        text = ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), NyayaPill)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, NyayaPill)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "More actions",
            onClick = onActions,
            tinted = false
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp, vertical = 9.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint }
            )
        }

        // The mic is hidden once there is something to send, so the two primary
        // actions never compete for the same glance.
        AnimatedVisibility(
            visible = text.isBlank() && !generating,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            CircleIconButton(
                icon = Icons.Filled.Mic,
                contentDescription = "Speak instead of typing",
                onClick = onVoiceMode,
                tinted = false
            )
        }

        if (generating) {
            CircleIconButton(
                icon = Icons.Filled.Stop,
                contentDescription = "Stop generating",
                onClick = onStop,
                tinted = true
            )
        } else {
            val sendAlpha by animateFloatAsState(
                targetValue = if (canSend) 1f else 0.38f,
                label = "sendAlpha"
            )
            CircleIconButton(
                icon = if (text.isBlank()) Icons.Filled.GraphicEq else Icons.Filled.ArrowUpward,
                contentDescription = if (text.isBlank()) "Voice conversation" else "Send",
                onClick = { if (text.isBlank()) onVoiceMode() else submit() },
                tinted = true,
                modifier = Modifier.alpha(if (text.isBlank()) 1f else sendAlpha)
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
    }
}

/**
 * A 40 dp circular icon button. [tinted] fills it with the primary container so
 * the trailing action reads as the primary one.
 */
@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tinted: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (tinted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (tinted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(background, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides content) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
