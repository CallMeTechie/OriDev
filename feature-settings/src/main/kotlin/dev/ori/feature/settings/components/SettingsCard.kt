package dev.ori.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriSectionLabel

/**
 * Wraps a labelled section: an [OriSectionLabel] above an [OriCard] containing
 * one or more [SettingsRow]s. Per `settings.html`, sections are stacked
 * vertically with 32 dp gap between them and 8 dp gap between the label and
 * its card.
 */
@Composable
public fun SettingsCard(
    sectionLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OriSectionLabel(text = sectionLabel)
        OriCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}
