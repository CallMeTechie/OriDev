package dev.ori.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Gray500

/**
 * Centered empty-state placeholder used by Transfer Queue, File Manager,
 * Connection list, and other list screens when there is no content to show.
 *
 * 48 dp icon in [Gray400], optional title in `titleMedium`, optional subtitle
 * in `bodySmall` / [Gray500]. Centered in a `fillMaxSize` column with 40 dp ×
 * 20 dp padding (matching the mockup `.empty-state` styling).
 *
 * Cycle 4 finding #13 corrected: this primitive lives in P0.5 (not as an inline
 * helper inside P2.4 as v5 originally said).
 */
@Composable
public fun OriEmptyState(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Gray400,
        )
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Gray500,
                textAlign = TextAlign.Center,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
                textAlign = TextAlign.Center,
            )
        }
    }
}
