package xendroid.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta XenDroid Adreno: preto + verde (estilo Adreno/NVIDIA) + branco.
val XenGreen = Color(0xFF76B900)
val XenGreenDark = Color(0xFF2F4A00)
val XenBlack = Color(0xFF050505)
val XenSurface = Color(0xFF0C0C0C)
val XenSurfaceHigh = Color(0xFF161616)

private val DarkColors = darkColorScheme(
    primary = XenGreen,
    onPrimary = Color.Black,
    primaryContainer = XenGreenDark,
    onPrimaryContainer = Color.White,
    inversePrimary = XenGreenDark,
    secondary = Color(0xFF9BDB1F),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1B2A06),
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
    outline = XenGreen,
    outlineVariant = XenGreenDark,
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

/** Tema do XenDroid Adreno: sempre escuro, em preto e verde. */
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
