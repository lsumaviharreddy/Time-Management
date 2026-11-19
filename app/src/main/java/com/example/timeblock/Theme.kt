package com.example.timeblock

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TaskColors = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF6C00), // orange
    Color(0xFFAB47BC), // purple
    Color(0xFFD81B60), // pink
    Color(0xFF43A047), // green
)

@Composable
fun TimeBlockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF9FA8DA), secondary = Color(0xFF80CBC4))
    } else {
        lightColorScheme(primary = Color(0xFF3F51B5), secondary = Color(0xFF00897B))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
