package com.example.timeblock

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val TaskColors = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF6C00), // orange
    Color(0xFFAB47BC), // purple
    Color(0xFFD81B60), // pink
    Color(0xFF43A047), // green
)

class BgTheme(val name: String, val swatch: Color, val scheme: ColorScheme)

private fun c(v: Long) = Color(v)

private fun makeScheme(
    light: Boolean,
    bg: Long, text: Long, textVariant: Long,
    card: Long, dialog: Long, bar: Long,
    primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long,
    secondary: Long, secondaryContainer: Long, onSecondaryContainer: Long,
    outline: Long, outlineVariant: Long,
): ColorScheme {
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = c(primary),
        onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer),
        onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(secondary),
        secondaryContainer = c(secondaryContainer),
        onSecondaryContainer = c(onSecondaryContainer),
        background = c(bg),
        onBackground = c(text),
        surface = c(bg),
        onSurface = c(text),
        surfaceVariant = c(card),
        onSurfaceVariant = c(textVariant),
        surfaceContainerLowest = c(bg),
        surfaceContainerLow = c(card),
        surfaceContainer = c(bar),
        surfaceContainerHigh = c(dialog),
        surfaceContainerHighest = c(card),
        outline = c(outline),
        outlineVariant = c(outlineVariant),
    )
}

val BgThemes: List<BgTheme> = listOf(
    BgTheme(
        "Sky blue", Color(0xFF87CEEB),
        makeScheme(
            light = true,
            bg = 0xFF87CEEB, text = 0xFF0B2538, textVariant = 0xFF2F5069,
            card = 0xFFE6F6FD, dialog = 0xFFF3FBFF, bar = 0xFF6DB9DA,
            primary = 0xFF0D47A1, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFBBDEFB, onPrimaryContainer = 0xFF0D2A4A,
            secondary = 0xFF00695C, secondaryContainer = 0xFFCDEFE8, onSecondaryContainer = 0xFF00352E,
            outline = 0xFF3A5F78, outlineVariant = 0xFF5C8BA6,
        ),
    ),
    BgTheme(
        "Orange", Color(0xFFFFB74D),
        makeScheme(
            light = true,
            bg = 0xFFFFB74D, text = 0xFF3B1F00, textVariant = 0xFF5E3600,
            card = 0xFFFFF1DB, dialog = 0xFFFFF8EE, bar = 0xFFF9A23B,
            primary = 0xFFBF360C, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFFFDDB8, onPrimaryContainer = 0xFF3B1500,
            secondary = 0xFF6D4C00, secondaryContainer = 0xFFFFE6B0, onSecondaryContainer = 0xFF3B2A00,
            outline = 0xFF7A4A00, outlineVariant = 0xFFC77A12,
        ),
    ),
    BgTheme(
        "Red", Color(0xFFC62828),
        makeScheme(
            light = false,
            bg = 0xFFC62828, text = 0xFFFFFFFF, textVariant = 0xFFFFD6D6,
            card = 0xFF9A1B1B, dialog = 0xFF8E1616, bar = 0xFFB02020,
            primary = 0xFFFFE0E0, onPrimary = 0xFF7F0000, primaryContainer = 0xFF7F1111, onPrimaryContainer = 0xFFFFFFFF,
            secondary = 0xFFFFCDD2, secondaryContainer = 0xFF8E1616, onSecondaryContainer = 0xFFFFFFFF,
            outline = 0xFFFFB4B4, outlineVariant = 0xFFE57373,
        ),
    ),
    BgTheme(
        "Gray", Color(0xFFBDBDBD),
        makeScheme(
            light = true,
            bg = 0xFFBDBDBD, text = 0xFF111111, textVariant = 0xFF3A3A3A,
            card = 0xFFF0F0F0, dialog = 0xFFF7F7F7, bar = 0xFFA9A9A9,
            primary = 0xFF263238, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFDADADA, onPrimaryContainer = 0xFF111111,
            secondary = 0xFF455A64, secondaryContainer = 0xFFE1E1E1, onSecondaryContainer = 0xFF111111,
            outline = 0xFF555555, outlineVariant = 0xFF8A8A8A,
        ),
    ),
    BgTheme(
        "Black", Color(0xFF000000),
        makeScheme(
            light = false,
            bg = 0xFF000000, text = 0xFFEDEDED, textVariant = 0xFFB0B0B0,
            card = 0xFF1C1C1C, dialog = 0xFF242424, bar = 0xFF111111,
            primary = 0xFF90CAF9, onPrimary = 0xFF002A4D, primaryContainer = 0xFF1E3A5A, onPrimaryContainer = 0xFFD6EBFF,
            secondary = 0xFF80CBC4, secondaryContainer = 0xFF2A2A2A, onSecondaryContainer = 0xFFEDEDED,
            outline = 0xFF8A8A8A, outlineVariant = 0xFF444444,
        ),
    ),
    BgTheme(
        "White", Color(0xFFFFFFFF),
        makeScheme(
            light = true,
            bg = 0xFFFFFFFF, text = 0xFF1B1B1F, textVariant = 0xFF55575F,
            card = 0xFFF1F2F7, dialog = 0xFFFFFFFF, bar = 0xFFF5F5F8,
            primary = 0xFF3F51B5, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFE0E4FF, onPrimaryContainer = 0xFF0E1A66,
            secondary = 0xFF00897B, secondaryContainer = 0xFFD6F2EE, onSecondaryContainer = 0xFF00332E,
            outline = 0xFF77777F, outlineVariant = 0xFFD0D1D8,
        ),
    ),
)

@Composable
fun TimeBlockTheme(themeIndex: Int, content: @Composable () -> Unit) {
    val scheme = BgThemes[themeIndex.coerceIn(0, BgThemes.lastIndex)].scheme
    val view = LocalView.current
    val lightBackground = scheme.background.luminance() > 0.4f
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = lightBackground
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
