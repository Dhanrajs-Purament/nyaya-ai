package com.bitchat.android.nyaya.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.nyaya.ai.ChatTurn

/**
 * Full-screen voice conversation mode (Gemini-live-style): pulsing orb,
 * status text, and a bottom control row — waveform pill, mic toggle, close.
 */
@Composable
fun VoiceModeScreen(
    state: NyayaViewModel.UiState,
    onClose: () -> Unit,
    onMicTap: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "voicePulse")
    val scale by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val active = state.listening || state.generating

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0B0F)) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(if (active) scale else 1f)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF8AB4F8), Color(0xFF1B1C22))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.listening) "\uD83C\uDFA4" else "\u2696\uFE0F",
                    fontSize = 44.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = when {
                    state.listening -> "Listening\u2026"
                    state.generating -> "Thinking\u2026"
                    else -> "Tap the mic and speak"
                },
                color = Color(0xFFB6BAC3),
                style = MaterialTheme.typography.titleMedium
            )

            val last = state.messages.lastOrNull()
            if (last != null && last.role == ChatTurn.Role.ASSISTANT) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (last.text.length > 220) last.text.take(220) + "\u2026" else last.text,
                    color = Color(0xFFE8EAED),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(bottom = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF23242B), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (active) "\u25CF\u25CF\u25CF" else "\u00B7 \u00B7 \u00B7",
                        color = Color(0xFF8AB4F8)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (state.listening) Color(0xFFE46962) else Color(0xFF8AB4F8),
                            CircleShape
                        )
                        .clickable { onMicTap() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\uD83C\uDFA4", fontSize = 24.sp)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF23242B), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close voice mode",
                        tint = Color(0xFFE8EAED)
                    )
                }
            }
        }
    }
}
