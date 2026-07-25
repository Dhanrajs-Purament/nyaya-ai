package com.bitchat.android.nyaya.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitchat.android.nyaya.ui.theme.NyayaTheme

/**
 * The Nyaya spark: a four-point star drawn in the brand gradient.
 *
 * Drawn with [Canvas] rather than shipped as a drawable so it scales to any size
 * without a second asset, and so the gradient can follow the light/dark scheme.
 * The shape echoes the beam in the launcher icon, which keeps the in-app mark and
 * the home-screen icon recognisably the same brand.
 */
@Composable
fun NyayaBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    animated: Boolean = false
) {
    val brush = NyayaTheme.gradients.brandMark
    val pulse by rememberBrandPulse(animated)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        // Waist controls how "pinched" the star is: a smaller waist reads as a
        // sharper spark. 0.17 keeps the points crisp while staying legible when
        // the mark is drawn small in a top bar.
        val waist = w * 0.17f

        val star = Path().apply {
            moveTo(cx, 0f)
            quadraticTo(cx + waist, cy - waist, w, cy)
            quadraticTo(cx + waist, cy + waist, cx, h)
            quadraticTo(cx - waist, cy + waist, 0f, cy)
            quadraticTo(cx - waist, cy - waist, cx, 0f)
            close()
        }

        scale(scale = pulse, pivot = Offset(cx, cy)) {
            drawPath(path = star, brush = brush)
            // A smaller star rotated 45° fills the gaps between the main points,
            // giving the eight-point sparkle silhouette at a glance.
            rotate(degrees = 45f, pivot = Offset(cx, cy)) {
                scale(scale = 0.62f, pivot = Offset(cx, cy)) {
                    drawPath(path = star, brush = brush, alpha = 0.75f)
                }
            }
        }
    }
}

/**
 * Slow breathing scale used while the model is thinking. Returns a constant when
 * [animated] is false so an idle screen runs no animation at all.
 */
@Composable
private fun rememberBrandPulse(animated: Boolean) = if (!animated) {
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
} else {
    rememberInfiniteTransition(label = "brandPulse").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brandPulseScale"
    )
}
