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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.component.LoadingIndicator
import dev.ori.core.ui.component.OriDevTopBar
import dev.ori.core.ui.component.ProtocolBadge
import dev.ori.core.ui.component.StatusDot
import dev.ori.domain.model.ConnectionStatus
import dev.ori.domain.model.ServerProfile
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
                viewModel.onEvent(ConnectionListEvent.Connect(profile.id))
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
            OriDevTopBar(
                title = "Connections",
                actions = {
                    IconButton(onClick = onNavigateToProxmox) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Proxmox Manager öffnen",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { role = Role.Button },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Verbindung hinzufügen")
            }
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
                            Icons.Default.Search,
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
                            items(
                                items = filteredProfiles,
                                key = { it.id },
                            ) { profile ->
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

            ProtocolBadge(protocol = profile.protocol.name)

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (profile.isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
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
