package dev.ori.feature.premium.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriSectionLabel
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.Crown
import dev.ori.core.ui.icons.lucide.FileText
import dev.ori.core.ui.icons.lucide.Globe
import dev.ori.core.ui.icons.lucide.Lock
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Star
import dev.ori.core.ui.icons.lucide.Zap
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.PremiumGold

@Composable
fun PaywallScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaywallEffect.NavigateBack -> onNavigateBack()
                is PaywallEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            OriTopBar(
                title = "Ori:Dev Premium",
                navigationIcon = {
                    OriIconButton(
                        icon = LucideIcons.ChevronLeft,
                        contentDescription = "Zurück",
                        onClick = onNavigateBack,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val s = state) {
            is PaywallUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Indigo500)
                }
            }

            is PaywallUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.dismissError() }) {
                            Text("Erneut versuchen")
                        }
                    }
                }
            }

            is PaywallUiState.Purchasing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Indigo500)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Kauf wird verarbeitet…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            is PaywallUiState.Purchased -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Indigo500,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Premium aktiviert!",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }

            is PaywallUiState.Ready -> {
                PaywallReadyContent(
                    state = s,
                    onSelectSku = viewModel::selectSku,
                    onPurchase = viewModel::purchase,
                    onRestore = viewModel::restorePurchases,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun PaywallReadyContent(
    state: PaywallUiState.Ready,
    onSelectSku: (Int) -> Unit,
    onPurchase: (Activity) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Hero section
        Icon(
            imageVector = LucideIcons.Crown,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = PremiumGold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ori:Dev Premium",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Schalte alle Features frei und hole das Maximum aus Ori:Dev.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Feature list
        OriCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureRow(icon = LucideIcons.Zap, text = "Unbegrenzte Bandbreite")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow(icon = LucideIcons.FileText, text = "Chunked-Transfers für große Dateien")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow(icon = LucideIcons.Globe, text = "Alle zukünftigen Premium-Features")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow(icon = LucideIcons.Star, text = "Priorisierter Support")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan selection
        OriSectionLabel(
            text = "Wähle deinen Plan",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        state.skus.forEachIndexed { index, sku ->
            SkuTile(
                sku = sku,
                isSelected = index == state.selectedIndex,
                onClick = { onSelectSku(index) },
            )
            if (index < state.skus.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CTA
        Button(
            onClick = { (context as? Activity)?.let(onPurchase) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Indigo500,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Jetzt upgraden",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Restore purchases
        TextButton(onClick = onRestore) {
            Text(
                text = "Käufe wiederherstellen",
                color = Gray500,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trust row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TrustItem(icon = LucideIcons.Lock, label = "Sichere Zahlung")
            TrustItem(icon = LucideIcons.Star, label = "Jederzeit kündbar")
            TrustItem(icon = LucideIcons.Check, label = "Sofort aktiv")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Indigo500,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SkuTile(
    sku: SkuUi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) Indigo500 else Color(0xFFE5E7EB)
    val bgColor = if (isSelected) Indigo500.copy(alpha = 0.04f) else Color.White

    OriCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = sku.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (sku.isMostPopular) {
                        Text(
                            text = "BELIEBT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Indigo500,
                            modifier = Modifier
                                .background(
                                    color = Indigo500.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                sku.savingsLabel?.let { savings ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = savings,
                        style = MaterialTheme.typography.bodySmall,
                        color = Indigo500,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = sku.price,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = sku.period,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray400,
                )
            }
        }
    }
}

@Composable
private fun TrustItem(
    icon: ImageVector,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Gray400,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Gray400,
        )
    }
}
