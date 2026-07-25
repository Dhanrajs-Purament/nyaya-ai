package com.bitchat.android.nyaya.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.theme.NyayaTheme

/**
 * The voice-mode orb: a soft pearlescent lozenge that breathes while listening
 * and settles when idle.
 *
 * Built from two layers — a blurred rotating gradient for the shifting sheen,
 * and a crisp lozenge on top — because a single static gradient reads as a flat
 * shape, and the whole point of the orb is to make it obvious the app is alive
 * and listening without printing a status string.
 *
 * [level] is the microphone amplitude, so the orb responds to the user's own
 * voice rather than animating on a timer regardless of what is happening.
 */
@Composable
fun VoiceOrb(
    listening: Boolean,
    thinking: Boolean,
    level: Float,
    modifier: Modifier = Modifier
) {
    val active = listening || thinking
    val infinite = rememberInfiniteTransition(label = "orb")

    val breathe by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (thinking) 700 else 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreathe"
    )
    val sheen by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9000)),
        label = "orbSheen"
    )
    // Amplitude is smoothed, otherwise the orb jitters on every RMS callback.
    val voice by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "orbVoice"
    )

    val brush = NyayaTheme.gradients.voiceOrb
    val scale = (if (active) breathe else 1f) + voice * 0.12f

    Box(
        modifier = modifier
            .width(168.dp)
            .height(132.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(1.08f)
                .rotate(sheen)
                .blur(26.dp)
                .background(brush, RoundedCornerShape(percent = 50))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .rotate(-sheen / 3f)
                .background(brush, RoundedCornerShape(percent = 50))
        )
    }
}
