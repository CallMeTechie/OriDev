package dev.ori.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Phase 11 PR 4a introduced [dev.ori.core.ui.components.OriTopBar] as the
 * canonical mockup-aligned top bar primitive (56/44/40 dp instead of Material 3's
 * 64 dp default). This `OriDevTopBar` wraps `androidx.compose.material3.TopAppBar`
 * and is now forbidden in feature modules by the diff-scoped grep guard added in
 * PR 3.5. Kept temporarily so existing un-migrated screens still compile during
 * the P2 per-screen migration; will be deleted once the last feature screen
 * migrates to `OriTopBar`.
 */
@Deprecated(
    message = "Replaced by OriTopBar in dev.ori.core.ui.components — see Phase 11 plan v6 §P0.5",
    replaceWith = ReplaceWith(
        expression = "OriTopBar(title = title)",
        imports = ["dev.ori.core.ui.components.OriTopBar"],
    ),
    level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriDevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
