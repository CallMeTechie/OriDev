package dev.ori.feature.connections.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import dev.ori.core.ui.icons.lucide.Link2Off
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
    // PR 2 — the sessionId is now a real id produced by
    // `sessionRegistry.connect(profileId)` inside the ViewModel's
    // `openProfile` flow. The host (`OriDevNavHost`) uses it to
    // `sessionRegistry.focus(sessionId)` before navigating to the
    // top-level Terminal / Files tab.
    onOpenTerminal: (sessionId: String) -> Unit = {},
    onOpenFileManager: (sessionId: String) -> Unit = {},
    viewModel: ConnectionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // PR 2 — collect one-shot nav effects emitted by `openProfile` on a
    // successful `sessionRegistry.connect()`. The effect carries the
    // real sessionId we hand to the navigation callback.
    LaunchedEffect(Unit) {
        viewModel.openEffects.collect { effect ->
            when (effect.target) {
                OpenTarget.TERMINAL -> onOpenTerminal(effect.sessionId)
                OpenTarget.FILES -> onOpenFileManager(effect.sessionId)
            }
        }
    }

    // PR 2 Section 8 — count from the derived activeProfiles mirror in
    // uiState. Source of truth is SessionRegistry.openSessions, so the
    // pill can never drift from the Aktiv section below.
    val activeCount = uiState.activeProfiles.size

    // PR 2 Section 8 — hoisted so the TopBar indicator pill can
    // `listState.animateScrollToItem(0)` when tapped, landing on the
    // first item of the Aktiv section.
    val listState = rememberLazyListState()

    // Bottom sheet state
    var selectedProfile by remember { mutableStateOf<ServerProfile?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(ConnectionListEvent.ClearError)
        }
    }

    uiState.hostKeyPrompt?.let { prompt ->
        HostKeyTrustDialog(
            prompt = prompt,
            onAccept = { viewModel.onEvent(ConnectionListEvent.AcceptHostKey) },
            onReject = { viewModel.onEvent(ConnectionListEvent.RejectHostKey) },
        )
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
            onOpenTerminal = {
                scope.launch { sheetState.hide() }
                selectedProfile = null
                // PR 2 — connect-on-demand. The VM calls
                // `sessionRegistry.connect(profile.id)` and, on success,
                // emits an effect this screen collects to hand the real
                // sessionId to `onOpenTerminal`.
                viewModel.openProfile(profile.id, OpenTarget.TERMINAL)
            },
            onOpenFiles = {
                scope.launch { sheetState.hide() }
                selectedProfile = null
                viewModel.openProfile(profile.id, OpenTarget.FILES)
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
        )
    }

    Scaffold(
        topBar = {
            OriTopBar(
                title = "Connections",
                height = 60.dp,
                indicator = if (activeCount > 0) {
                    {
                        // PR 2 Section 8 — tapping the pill scrolls the
                        // list to the Aktiv section (which is always
                        // the first LazyColumn item when non-empty).
                        // OriServiceIndicator has no built-in onClick,
                        // so wrap in a clickable Box. Role.Button is
                        // set explicitly so TalkBack announces it as
                        // tappable.
                        Box(
                            modifier = Modifier
                                .clickable(role = Role.Button) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                        ) {
                            OriServiceIndicator(count = activeCount, label = "aktiv")
                        }
                    }
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

            ConnectionListBody(
                uiState = uiState,
                listState = listState,
                onProfileTap = { selectedProfile = it },
                onQuickDisconnect = { viewModel.quickDisconnect(it) },
                onToggleFavorite = { profile ->
                    viewModel.onEvent(ConnectionListEvent.ToggleFavorite(profile))
                },
            )
        }
    }
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

/**
 * PR 2 Section 8 — main list area: loading spinner, empty state, or the
 * three-section LazyColumn (Aktiv / Favoriten / Alle Verbindungen).
 * Extracted from [ConnectionListScreen] so the scaffold-level composable
 * stays under detekt's 250-line LongMethod cap.
 *
 * Section semantics:
 *  - "Aktiv" — profiles with an open [dev.ori.domain.model.Session],
 *    green dot + eject icon wiring to [onQuickDisconnect].
 *  - "Favoriten" — favourite profiles NOT already in Aktiv; their row
 *    keeps the standard star toggle.
 *  - "Alle Verbindungen" — every profile; the ad slot (CONNECTION_LIST_NATIVE)
 *    is injected after the 3rd row per the Phase 11 ads placement.
 */
@Composable
private fun ConnectionListBody(
    uiState: ConnectionListUiState,
    listState: LazyListState,
    onProfileTap: (ServerProfile) -> Unit,
    onQuickDisconnect: (Long) -> Unit,
    onToggleFavorite: (ServerProfile) -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
        return
    }
    // PR 2 Section 8 — search filters each section independently
    // so typing narrows Aktiv/Favoriten/Alle simultaneously.
    val query = uiState.searchQuery.lowercase()
    val matches: (ServerProfile) -> Boolean = { profile ->
        uiState.searchQuery.isBlank() ||
            profile.name.lowercase().contains(query) ||
            profile.host.lowercase().contains(query)
    }
    val filteredActive = uiState.activeProfiles.filter(matches)
    // Favoriten section excludes already-active profiles so the same
    // row doesn't appear twice above the fold.
    val activeIds = uiState.activeProfiles.map { it.id }.toSet()
    val filteredFavorites = uiState.favorites
        .filter { it.id !in activeIds }
        .filter(matches)
    val filteredAll = uiState.profiles.filter(matches)

    if (filteredActive.isEmpty() && filteredFavorites.isEmpty() && filteredAll.isEmpty()) {
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
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filteredActive.isNotEmpty()) {
            item(key = "section-active") { SectionHeader(text = "Aktiv") }
            items(
                items = filteredActive,
                key = { "active-${it.id}" },
            ) { profile ->
                ServerProfileCard(
                    profile = profile,
                    isConnected = true,
                    onClick = { onProfileTap(profile) },
                    trailing = {
                        IconButton(onClick = { onQuickDisconnect(profile.id) }) {
                            Icon(
                                imageVector = LucideIcons.Link2Off,
                                contentDescription = "Trennen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        if (filteredFavorites.isNotEmpty()) {
            item(key = "section-favorites") { SectionHeader(text = "Favoriten") }
            items(
                items = filteredFavorites,
                key = { "fav-${it.id}" },
            ) { profile ->
                ServerProfileCard(
                    profile = profile,
                    isConnected = false,
                    onClick = { onProfileTap(profile) },
                    trailing = {
                        FavoriteToggle(
                            profile = profile,
                            onToggle = { onToggleFavorite(profile) },
                        )
                    },
                )
            }
        }
        if (filteredAll.isNotEmpty()) {
            item(key = "section-all") { SectionHeader(text = "Alle Verbindungen") }
            filteredAll.forEachIndexed { index, profile ->
                if (index == 3) {
                    item(key = "ad_native") {
                        AdSlotHost(
                            slot = AdSlot.CONNECTION_LIST_NATIVE,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item(key = "all-${profile.id}") {
                    ServerProfileCard(
                        profile = profile,
                        isConnected = profile.id in activeIds,
                        onClick = { onProfileTap(profile) },
                        trailing = {
                            FavoriteToggle(
                                profile = profile,
                                onToggle = { onToggleFavorite(profile) },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * PR 2 Section 8 — section title rendered as a LazyColumn `item { }`
 * between connection groups (Aktiv / Favoriten / Alle Verbindungen).
 */
@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Phase 11 T1a + PR 2 Section 8 — extracted trailing favourite-toggle so
 * the shared [ServerProfileCard] can hand its trailing slot to either
 * this star icon (Favoriten + Alle sections) or a quick-disconnect
 * icon (Aktiv section).
 */
@Composable
private fun FavoriteToggle(
    profile: ServerProfile,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
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

@Composable
private fun ServerProfileCard(
    profile: ServerProfile,
    isConnected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
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

            // PR 2 Section 8 — trailing slot is Star toggle for Favoriten +
            // Alle Verbindungen sections, or the eject icon in the Aktiv
            // section for one-tap "good-night, trenne alles"-flow.
            trailing()
        }
    }
}
