package dev.ori.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo700,
    secondary = Gray500,
    onSecondary = Color.White,
    secondaryContainer = Gray100,
    onSecondaryContainer = Gray700,
    tertiary = StatusInfo,
    // background = Gray50 = #FAFAFA per mockup --bg; page-level color
    background = Gray50,
    onBackground = Gray900,
    // surface = Color.White = #FFFFFF per mockup --bg-white; card-level color.
    // Was Gray50 (latent bug — cards were indistinguishable from page bg).
    // v6 §P0.0 documents the swap and the screen audit done in this PR.
    surface = Color.White,
    onSurface = Gray900,
    // surfaceVariant: muted surface (search input bg, hover bg) — #F3F4F6
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    surfaceContainerHigh = Gray100,
    // outline: 1 dp card borders — #E5E7EB per mockup --border
    outline = Gray200,
    outlineVariant = Gray200,
    error = StatusDisconnected,
    onError = Color.White,
)

@Composable
fun OriDevTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = OriDevTypography,
        shapes = OriDevShapes,
        content = content,
    )
}
