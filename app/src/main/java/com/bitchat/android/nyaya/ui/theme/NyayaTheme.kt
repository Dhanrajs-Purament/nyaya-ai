package com.bitchat.android.nyaya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Nyaya AI design system.
 *
 * One source of truth for colour, type, shape and the brand gradients, so every
 * screen stays consistent and a change lands everywhere at once. Screens must
 * read from [MaterialTheme] and [NyayaTheme.gradients] rather than hard-coding
 * hex values — the previous UI hard-coded colours in three separate files, which
 * is why it could not support a light theme at all.
 *
 * The visual language: a calm near-white canvas that deepens into cornflower
 * blue towards the bottom of the screen, generous whitespace, one light-weight
 * display heading per screen, fully rounded "pill" controls that float above the
 * canvas, and no heavy borders or drop shadows.
 */
object NyayaTheme {

    /** Brand gradients, resolved for the active light/dark scheme. */
    val gradients: NyayaGradients
        @Composable
        @ReadOnlyComposable
        get() = LocalNyayaGradients.current

    /** True when the dark scheme is active; a few surfaces need to know. */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalNyayaIsDark.current
}

/**
 * Gradients cannot live in [androidx.compose.material3.ColorScheme], so they get
 * their own carrier that is provided alongside it.
 */
data class NyayaGradients(
    /** Full-screen canvas: near-white at the top, cornflower blue at the bottom. */
    val canvas: Brush,
    /** The soft white glow that lifts the hero heading off the canvas. */
    val heroGlow: Brush,
    /** The brand mark's four-point spark. */
    val brandMark: Brush,
    /** The pearlescent voice orb. */
    val voiceOrb: Brush
)

private val LocalNyayaGradients = staticCompositionLocalOf<NyayaGradients> {
    error("NyayaGradients not provided — wrap the UI in NyayaTheme { }")
}

private val LocalNyayaIsDark = staticCompositionLocalOf { true }

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------

/** Deep indigo — authority, and the darkest tone of the launcher icon's shield. */
private val Indigo700 = Color(0xFF1B4BC4)
private val Indigo500 = Color(0xFF3B6FE0)

/** Cornflower — the signature tint of the canvas gradient. */
private val Cornflower300 = Color(0xFFA8C7FA)
private val Cornflower200 = Color(0xFFC6DCFD)
private val Cornflower100 = Color(0xFFE3EEFE)

/** Saffron — used sparingly, only in the brand mark, as a nod to the flag. */
private val Saffron400 = Color(0xFFF5A524)

/** Teal — the "AI/mesh node" accent from the launcher icon. */
private val Teal400 = Color(0xFF19A5A5)

private val Ink900 = Color(0xFF10131A)
private val Ink800 = Color(0xFF171A21)
private val Ink700 = Color(0xFF20242D)
private val Ink600 = Color(0xFF2B303B)

private val Slate600 = Color(0xFF56606F)
private val Slate300 = Color(0xFFB6BEC9)
private val Slate100 = Color(0xFFEFF2F7)

private val NyayaLightColors = lightColorScheme(
    primary = Indigo700,
    onPrimary = Color.White,
    primaryContainer = Cornflower100,
    onPrimaryContainer = Indigo700,
    secondary = Teal400,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F1F1),
    onSecondaryContainer = Color(0xFF0A4B4B),
    tertiary = Saffron400,
    onTertiary = Color(0xFF3B2600),
    background = Color.White,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    // Deliberately near-white rather than grey: controls sit on a blue canvas,
    // so a grey fill reads as dirty against it.
    surfaceVariant = Color(0xFFF4F7FC),
    onSurfaceVariant = Slate600,
    surfaceContainerHighest = Slate100,
    outline = Color(0xFFC9D3E2),
    outlineVariant = Color(0xFFE2E9F3),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF6E1109),
    scrim = Color(0x66000000)
)

private val NyayaDarkColors = darkColorScheme(
    primary = Cornflower300,
    onPrimary = Color(0xFF0A1E45),
    primaryContainer = Color(0xFF23375F),
    onPrimaryContainer = Cornflower200,
    secondary = Teal400,
    onSecondary = Color(0xFF00201F),
    secondaryContainer = Color(0xFF11413F),
    onSecondaryContainer = Color(0xFFA6E9E7),
    tertiary = Saffron400,
    onTertiary = Color(0xFF3B2600),
    background = Ink900,
    onBackground = Color(0xFFE7EAF0),
    surface = Ink800,
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Ink700,
    onSurfaceVariant = Slate300,
    surfaceContainerHighest = Ink600,
    outline = Color(0xFF3B4250),
    outlineVariant = Color(0xFF2A303B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF4E1512),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color(0x99000000)
)

private val LightGradients = NyayaGradients(
    canvas = Brush.verticalGradient(
        // Stops chosen so the top 45% of the screen stays white for text
        // legibility, then the blue arrives quickly enough to be felt.
        0.00f to Color.White,
        0.45f to Color(0xFFF7FAFF),
        0.72f to Cornflower100,
        0.88f to Cornflower200,
        1.00f to Cornflower300
    ),
    heroGlow = Brush.radialGradient(
        listOf(Color.White, Color(0x00FFFFFF))
    ),
    brandMark = Brush.linearGradient(
        listOf(Indigo700, Indigo500, Teal400, Saffron400)
    ),
    voiceOrb = Brush.linearGradient(
        listOf(Color.White, Color(0xFFF2F7FF), Cornflower300, Indigo500)
    )
)

private val DarkGradients = NyayaGradients(
    canvas = Brush.verticalGradient(
        0.00f to Ink900,
        0.45f to Color(0xFF121722),
        0.75f to Color(0xFF16233C),
        1.00f to Color(0xFF1B2F52)
    ),
    heroGlow = Brush.radialGradient(
        listOf(Color(0x2E9EC0FF), Color(0x00000000))
    ),
    brandMark = Brush.linearGradient(
        listOf(Cornflower200, Cornflower300, Teal400, Saffron400)
    ),
    voiceOrb = Brush.linearGradient(
        listOf(Color(0xFFF2F7FF), Cornflower200, Cornflower300, Indigo500)
    )
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/**
 * Type scale. The hero headings are deliberately light-weight and large: the
 * screen asks one question, so it should look like one question and not like a
 * dashboard title.
 */
private val NyayaTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        // Legal text is dense, so body copy gets extra leading.
        lineHeight = 23.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)

// ---------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------

/** Fully rounded. Used for the input bar, drawer rows and every chip. */
val NyayaPill = RoundedCornerShape(percent = 50)

private val NyayaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/**
 * Wraps the Nyaya UI. Pass [darkTheme] explicitly to pin a screen to one
 * scheme; the voice screen does this because it is always shown on the deep
 * gradient regardless of the system setting.
 */
@Composable
fun NyayaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalNyayaGradients provides if (darkTheme) DarkGradients else LightGradients,
        LocalNyayaIsDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) NyayaDarkColors else NyayaLightColors,
            typography = NyayaTypography,
            shapes = NyayaShapes,
            content = content
        )
    }
}
