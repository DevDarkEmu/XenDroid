package xendroid.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta XenDroid Nightly: preto + vermelho + branco.
val XenRed = Color(0xFFFF1E1E)
val XenRedDark = Color(0xFF8B0000)
val XenBlack = Color(0xFF000000)
val XenSurface = Color(0xFF0D0D0D)
val XenSurfaceHigh = Color(0xFF1A1A1A)

private val DarkColors = darkColorScheme(
    primary = XenRed,
    onPrimary = Color.White,
    primaryContainer = XenRedDark,
    onPrimaryContainer = Color.White,
    inversePrimary = XenRedDark,
    secondary = Color(0xFFFF5252),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A0A0A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFE0E0E0),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color.White,
    background = XenBlack,
    onBackground = Color.White,
    surface = XenSurface,
    onSurface = Color.White,
    surfaceVariant = XenSurfaceHigh,
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = XenRed,
    outlineVariant = XenRedDark,
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

/** Tema do XenDroid Nightly: sempre escuro, em vermelho e preto. */
@Composable
fun xendroidTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
