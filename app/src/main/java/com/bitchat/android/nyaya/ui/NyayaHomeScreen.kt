package com.bitchat.android.nyaya.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.components.NyayaBrandMark
import com.bitchat.android.nyaya.ui.components.NyayaInputBar
import com.bitchat.android.nyaya.ui.components.NyayaTopBar
import com.bitchat.android.nyaya.ui.theme.NyayaPill
import com.bitchat.android.nyaya.ui.theme.NyayaTheme

/**
 * The landing screen: brand mark, one question, and the input bar.
 *
 * Everything else is deliberately absent. The previous version stacked three
 * suggestion cards, a model-setup card and a mesh-chat card on top of each
 * other, which meant a first-time user met five competing calls to action. The
 * suggestions are now a single scrolling row, mesh chat lives in the drawer where
 * the two modes sit as peers, and the setup card only appears when there is
 * genuinely no engine ready.
 */
@Composable
fun NyayaHomeScreen(
    state: NyayaViewModel.UiState,
    onSend: (String) -> Unit,
    onVoiceMode: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onActions: () -> Unit,
    onDownloadModel: () -> Unit,
    onNewChat: () -> Unit,
    onDismissError: () -> Unit
) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Hero(kbReady = state.kbReady)

                Spacer(modifier = Modifier.height(28.dp))
                SuggestionRow(onSend = onSend)

                if (!state.modelDownloaded && !state.modelLoading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ModelSetupCard(state = state, onDownloadModel = onDownloadModel)
                }

                AnimatedVisibility(visible = state.error != null) {
                    ErrorNote(message = state.error.orEmpty(), onDismiss = onDismissError)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            NyayaInputBar(
                hint = "Ask about your legal problem",
                generating = state.generating,
                onSend = onSend,
                onVoiceMode = onVoiceMode,
                onActions = onActions,
                onStop = {}
            )
        }
    }
}

/**
 * The hero: brand mark over a soft glow, the question, and the reassurance that
 * this is offline and private.
 *
 * The privacy line is here rather than in a settings page on purpose. Someone
 * about to type a question about a police case or an abusive husband needs to
 * know where that text is going *before* they type it, not after.
 */
@Composable
private fun Hero(kbReady: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .blur(60.dp)
                .background(NyayaTheme.gradients.heroGlow, CircleShape)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NyayaBrandMark(size = 40.dp)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "How can I help?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (kbReady) {
                    "Indian law, offline. Nothing you type leaves this phone."
                } else {
                    "Loading the law library\u2026"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

/**
 * Starting points, as a single horizontally scrolling row.
 *
 * These are the questions people actually arrive with, phrased the way they would
 * say them rather than in legal language.
 */
@Composable
private fun SuggestionRow(onSend: (String) -> Unit) {
    val suggestions = listOf(
        "The police stopped me \u2014 what are my rights?",
        "The police refuse to file my FIR",
        "I can't afford a lawyer",
        "My landlord won't return my deposit",
        "Someone leaked my photos online",
        "A shop refuses to refund a faulty product"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        suggestions.forEach { suggestion ->
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        NyayaPill
                    )
                    .clickable { onSend(suggestion) }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}

/** Shown only while no engine is ready, so it never nags a set-up user. */
@Composable
private fun ModelSetupCard(
    state: NyayaViewModel.UiState,
    onDownloadModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Get the free offline AI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "One download over Wi-Fi. After that it answers with no internet, " +
                "no account and no data pack.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        val progress = state.downloadProgress
        when {
            progress != null && progress >= 0f -> {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloading \u00B7 " + (progress * 100).toInt() + "%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            progress != null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDownloadModel, shape = NyayaPill) {
                    Text("Download")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "or use your own API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorNote(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
