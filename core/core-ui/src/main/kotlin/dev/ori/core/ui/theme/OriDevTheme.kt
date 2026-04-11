package dev.ori.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Gray50,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo700,
    secondary = Gray500,
    onSecondary = Gray50,
    secondaryContainer = Gray100,
    onSecondaryContainer = Gray700,
    tertiary = StatusInfo,
    background = Gray50,
    onBackground = Gray900,
    surface = Gray50,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    surfaceContainerHigh = Gray100,
    outline = Gray200,
    outlineVariant = Gray200,
    error = StatusDisconnected,
    onError = Gray50,
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
