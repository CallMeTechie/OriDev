package dev.ori.feature.connections.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.common.model.Protocol
import dev.ori.core.ui.component.LoadingIndicator
import dev.ori.core.ui.component.StatusDot
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriFab
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriServiceIndicator
import dev.ori.core.ui.components.OriStatusBadge
import dev.ori.core.ui.components.OriStatusBadgeIntent
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Plus
import dev.ori.core.ui.icons.lucide.Search
import dev.ori.core.ui.icons.lucide.Server
import dev.ori.core.ui.icons.lucide.Star
import dev.ori.domain.model.AdSlot
import dev.ori.domain.model.ConnectionStatus
import dev.ori.domain.model.ServerProfile
import dev.ori.feature.premium.ui.AdSlotHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    onNavigateToAdd: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToProxmox: () -> Unit = {},
    onOpenTerminal: (Long) -> Unit = {},
    onOpenFileManager: (Long) -> Unit = {},
    viewModel: ConnectionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Phase 11 Tier-1 T1d — obtain the hosting FragmentActivity so the VM
    // can forward it to CredentialUnlockGate for BiometricPrompt. Nullable
    // to preserve @Preview rendering: the preview Context is not a
    // FragmentActivity, so the tap handler falls back to the non-gated path.
    val activity = LocalContext.current.findFragmentActivity()

    // Phase 11 carry-over #E — number of currently-connected sessions shown
    // as a pulsing pill in OriTopBar's indicator slot (e.g. "2 aktiv").
    val activeCount = uiState.activeConnections.count {
        it.status == ConnectionStatus.CONNECTED
    }

    // Bottom sheet state
    var selectedProfile by remember { mutableStateOf<ServerProfile?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(ConnectionListEvent.ClearError)
        }
    }

    // Show bottom sheet when a profile is selected
    selectedProfile?.let { profile ->
        val isConnected = uiState.activeConnections.any {
            it.profileId == profile.id && it.status == ConnectionStatus.CONNECTED
        }
        ConnectionDetailSheet(
            profile = profile,
            isConnected = isConnected,
            sheetState = sheetState,
            onDismiss = { selectedProfile = null },
            onConnect = {
                // Phase 11 Tier-1 T1d — gate credential fetch behind the
                // biometric preference. If we can't resolve a FragmentActivity
                // (e.g. preview / test), fall through to the legacy flow.
                if (activity != null) {
                    viewModel.unlockAndConnect(activity, profile.id)
                } else {
                    viewModel.onEvent(ConnectionListEvent.Connect(profile.id))
                }
                selectedProfile = null
            },
            onDisconnect = {
                viewModel.onEvent(ConnectionListEvent.Disconnect(profile.id))
                selectedProfile = null
            },
            onEdit = {
                scope.launch { sheetState.hide() }
                selectedProfile = null
                onNavigateToEdit(profile.id)
            },
            onDelete = {
                viewModel.onEvent(ConnectionListEvent.Delete(profile))
                selectedProfile = null
            },
            onOpenTerminal = {
                scope.launch { sheetState.hide() }
                selectedProfile = null
                onOpenTerminal(profile.id)
            },
            onOpenFileManager = {
                scope.launch { sheetState.hide() }
                selectedProfile = null
                onOpenFileManager(profile.id)
            },
        )
    }

    Scaffold(
        topBar = {
            OriTopBar(
                title = "Connections",
                height = 60.dp,
                indicator = if (activeCount > 0) {
                    { OriServiceIndicator(count = activeCount, label = "aktiv") }
                } else {
                    null
                },
                actions = {
                    OriIconButton(
                        icon = LucideIcons.Server,
                        contentDescription = "Proxmox Manager öffnen",
                        onClick = onNavigateToProxmox,
                    )
                },
            )
        },
        floatingActionButton = {
            // Phase 11 P2.3-polish — OriFab (52 dp, 16 dp radius, Indigo500)
            // replaces the Material3 FloatingActionButton (56 dp default)
            // per connection-manager.html mockup spec.
            OriFab(
                icon = LucideIcons.Plus,
                contentDescription = "Verbindung hinzufügen",
                onClick = onNavigateToAdd,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.searchQuery.isNotEmpty() || uiState.profiles.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(ConnectionListEvent.Search(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search connections") },
                    leadingIcon = {
                        Icon(
                            imageVector = LucideIcons.Search,
                            contentDescription = "Verbindungen durchsuchen",
                        )
                    },
                    singleLine = true,
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
                else -> {
                    val filteredProfiles = if (uiState.searchQuery.isBlank()) {
                        uiState.profiles
                    } else {
                        val query = uiState.searchQuery.lowercase()
                        uiState.profiles.filter { profile ->
                            profile.name.lowercase().contains(query) ||
                                profile.host.lowercase().contains(query)
                        }
                    }

                    if (filteredProfiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "No matching connections"
                                } else {
                                    "No connections yet.\nTap + to add one."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filteredProfiles.forEachIndexed { index, profile ->
                                if (index == 3) {
                                    item(key = "ad_native") {
                                        AdSlotHost(
                                            slot = AdSlot.CONNECTION_LIST_NATIVE,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                                item(key = profile.id) {
                                    val isConnected = uiState.activeConnections.any {
                                        it.profileId == profile.id &&
                                            it.status == ConnectionStatus.CONNECTED
                                    }
                                    ServerProfileCard(
                                        profile = profile,
                                        isConnected = isConnected,
                                        onClick = { selectedProfile = profile },
                                        onToggleFavorite = {
                                            viewModel.onEvent(
                                                ConnectionListEvent.ToggleFavorite(profile),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Walks [ContextWrapper] chain to find a [FragmentActivity]. Returns null
 * if the context is not hosted by one (Compose preview / unit test). This
 * keeps the connection screen renderable in isolation while still letting
 * the real activity flow through to [CredentialUnlockGate].
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Phase 11 P2.3-polish — maps domain [Protocol] to the mockup colour token
 * in [OriStatusBadgeIntent]. SFTP/SCP share the SFTP indigo palette, FTPS
 * shares FTP sky-blue, PROXMOX has its own red palette.
 */
private fun Protocol.toBadgeIntent(): OriStatusBadgeIntent = when (this) {
    Protocol.SSH -> OriStatusBadgeIntent.Ssh
    Protocol.SFTP, Protocol.SCP -> OriStatusBadgeIntent.Sftp
    Protocol.FTP, Protocol.FTPS -> OriStatusBadgeIntent.Ftp
    Protocol.PROXMOX -> OriStatusBadgeIntent.Proxmox
}

@Composable
private fun ServerProfileCard(
    profile: ServerProfile,
    isConnected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = if (isConnected) "verbunden" else "getrennt"
    val favoriteText = if (profile.isFavorite) ", Favorit" else ""
    val rowDescription = "${profile.name}, ${profile.protocol.name}, " +
        "${profile.host}:${profile.port}, $statusText$favoriteText"
    // Phase 11 P2.3-polish — OriCard replaces M3 Card (flat, 14 dp radius,
    // Gray200 border, no elevation) per connection-manager.html spec.
    // Card padding 14 dp × 16 dp matches `.server-card` in the mockup.
    OriCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(isConnected = isConnected)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${profile.host}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Phase 11 P2.3-polish — OriStatusBadge with mockup-matching
            // colour pairs per protocol (SFTP=indigo, SSH=amber, FTP/FTPS=sky,
            // PROXMOX=red) replaces the v0 ProtocolBadge stub.
            OriStatusBadge(
                label = profile.protocol.displayName,
                intent = profile.protocol.toBadgeIntent(),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Phase 11 T1a — Lucide has a single Star outline; favorite
            // vs non-favorite state is conveyed via `tint` (primary vs
            // onSurfaceVariant) as an approximation of the M3 Filled
            // Star / Outlined StarBorder pair.
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = LucideIcons.Star,
                    contentDescription = if (profile.isFavorite) {
                        "Favorit entfernen"
                    } else {
                        "Zu Favoriten hinzufügen"
                    },
                    tint = if (profile.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
