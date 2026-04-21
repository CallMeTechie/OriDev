package dev.ori.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Reusable single-choice picker dialog for the settings dummy rows
 * (Standard-Shell, Bell-Modus, Terminal-Schrift, Überschreiben-Modus, …).
 *
 * Each option is rendered as a radio row with an optional human-readable
 * description. Tapping an option fires [onSelect] with the underlying
 * value and dismisses the dialog; the dismiss button bows out without
 * persisting anything so an accidental long-press on the row can be
 * cancelled cleanly.
 */
@Composable
internal fun <T> SettingsOptionPickerDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    onSelect(option.value)
                                    onDismiss()
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            option.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

internal data class SettingsPickerOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
)
