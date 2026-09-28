package dev.munote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF4D5DB3),
    surface = Color(0xFFF8F8F6),
    background = Color(0xFFF2F2EF),
    surfaceVariant = Color(0xFFECECE8),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFBCC4FF),
    surface = Color(0xFF1D1D1B),
    background = Color(0xFF151513),
    surfaceVariant = Color(0xFF2A2A27),
)

@Composable
fun MuNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
