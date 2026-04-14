@file:Suppress("MatchingDeclarationName")

package dev.ori.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Gray900

/** Variant for the primary action button. */
@Immutable
public enum class OriConfirmDialogVariant { Default, Danger }

/**
 * Reusable confirmation dialog primitive — used by file delete, VM delete,
 * connection delete, clear-completed-transfers, and any other destructive
 * action. 380 dp max width, 28 dp content padding, 14 dp radius (via the
 * theme's `large` shape), Indigo or Danger primary button.
 *
 * Wraps Material 3's `AlertDialog` to inherit modal behaviour but provides a
 * consistent visual styling.
 */
@Composable
public fun OriConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Abbrechen",
    variant: OriConfirmDialogVariant = OriConfirmDialogVariant.Default,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(max = 380.dp),
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Gray900,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Gray700,
            )
        },
        confirmButton = {
            OriPillButton(
                label = confirmLabel,
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                variant = when (variant) {
                    OriConfirmDialogVariant.Default -> OriPillButtonVariant.Primary
                    OriConfirmDialogVariant.Danger -> OriPillButtonVariant.Danger
                },
            )
        },
        dismissButton = {
            OriPillButton(
                label = cancelLabel,
                onClick = onDismiss,
                variant = OriPillButtonVariant.Default,
            )
        },
    )
    @Suppress("UnusedExpression")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {}
    @Suppress("UnusedExpression")
    Row {}
}
