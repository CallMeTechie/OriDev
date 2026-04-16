package dev.ori.feature.connections.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriInput
import dev.ori.core.ui.components.OriSegmentedControl
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.ChevronDown
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.ChevronUp
import dev.ori.core.ui.icons.lucide.Eye
import dev.ori.core.ui.icons.lucide.EyeOff
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.feature.premium.ui.BandwidthThrottleSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditConnectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: AddEditConnectionViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddEditEffect.NavigateBack -> onNavigateBack()
                is AddEditEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            // Phase 11 P2.3 — OriTopBar with Lucide ChevronLeft as nav icon.
            OriTopBar(
                title = formState.title,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name — Phase 11 T2d — OriInput primitive.
            OriInput(
                value = formState.name,
                onValueChange = { viewModel.onEvent(AddEditEvent.NameChanged(it)) },
                label = "Name",
                placeholder = "My Server",
                isError = formState.nameError != null,
                errorMessage = formState.nameError,
            )

            // Host
            OriInput(
                value = formState.host,
                onValueChange = { viewModel.onEvent(AddEditEvent.HostChanged(it)) },
                label = "Host",
                placeholder = "192.168.1.100 or server.example.com",
                isError = formState.hostError != null,
                errorMessage = formState.hostError,
            )

            // Port and Protocol row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Port
                OriInput(
                    value = formState.port,
                    onValueChange = { viewModel.onEvent(AddEditEvent.PortChanged(it)) },
                    label = "Port",
                    isError = formState.portError != null,
                    errorMessage = formState.portError,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )

                // Protocol dropdown — kept on M3 OutlinedTextField because
                // ExposedDropdownMenuBox's menuAnchor requires a Material3
                // TextField anchor. No Ori dropdown primitive for this yet.
                var protocolExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = it },
                    modifier = Modifier.weight(1.5f),
                ) {
                    OutlinedTextField(
                        value = formState.protocol.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded)
                        },
                        singleLine = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false },
                    ) {
                        Protocol.entries.forEach { protocol ->
                            DropdownMenuItem(
                                text = { Text(protocol.displayName) },
                                onClick = {
                                    viewModel.onEvent(AddEditEvent.ProtocolChanged(protocol))
                                    protocolExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Username
            OriInput(
                value = formState.username,
                onValueChange = { viewModel.onEvent(AddEditEvent.UsernameChanged(it)) },
                label = "Username",
                placeholder = "root",
                isError = formState.usernameError != null,
                errorMessage = formState.usernameError,
            )

            // Auth Method — Phase 11 T2d — OriSegmentedControl primitive.
            Text(
                text = "Authentication",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            OriSegmentedControl(
                options = listOf(AuthMethod.PASSWORD, AuthMethod.SSH_KEY),
                selectedValue = formState.authMethod,
                onValueChange = { viewModel.onEvent(AddEditEvent.AuthMethodChanged(it)) },
                optionLabel = { it.displayName },
            )

            // Password or SSH Key path
            if (formState.authMethod == AuthMethod.PASSWORD) {
                // Password field is kept on M3 OutlinedTextField because OriInput
                // doesn't yet support trailing icons (needed for the eye toggle).
                var passwordVisible by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = formState.credential,
                    onValueChange = { viewModel.onEvent(AddEditEvent.CredentialChanged(it)) },
                    label = { Text("Password") },
                    isError = formState.credentialError != null,
                    supportingText = formState.credentialError?.let { { Text(it) } },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    LucideIcons.EyeOff
                                } else {
                                    LucideIcons.Eye
                                },
                                contentDescription = if (passwordVisible) {
                                    "Passwort verbergen"
                                } else {
                                    "Passwort anzeigen"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OriInput(
                    value = formState.credential,
                    onValueChange = { viewModel.onEvent(AddEditEvent.CredentialChanged(it)) },
                    label = "SSH Key Path",
                    placeholder = "/storage/emulated/0/.ssh/id_ed25519",
                    isError = formState.credentialError != null,
                    errorMessage = formState.credentialError,
                )
            }

            // Advanced section
            TextButton(
                onClick = { viewModel.onEvent(AddEditEvent.ToggleAdvanced) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(
                    imageVector = if (formState.isAdvancedExpanded) {
                        LucideIcons.ChevronUp
                    } else {
                        LucideIcons.ChevronDown
                    },
                    contentDescription = if (formState.isAdvancedExpanded) {
                        "Erweiterte Optionen einklappen"
                    } else {
                        "Erweiterte Optionen ausklappen"
                    },
                )
                Text(
                    text = "Advanced",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            AnimatedVisibility(visible = formState.isAdvancedExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OriInput(
                        value = formState.startupCommand,
                        onValueChange = {
                            viewModel.onEvent(AddEditEvent.StartupCommandChanged(it))
                        },
                        label = "Startup Command",
                        placeholder = "cd /opt/project && source venv/bin/activate",
                    )
                    OriInput(
                        value = formState.projectDirectory,
                        onValueChange = {
                            viewModel.onEvent(AddEditEvent.ProjectDirectoryChanged(it))
                        },
                        label = "Project Directory",
                        placeholder = "/home/user/project",
                    )
                    Spacer(Modifier.height(16.dp))
                    BandwidthThrottleSlider(
                        currentKbps = formState.maxBandwidthKbps,
                        isPremium = isPremium,
                        onValueChange = {
                            viewModel.onEvent(AddEditEvent.MaxBandwidthKbpsChanged(it))
                        },
                        onUpgradeTap = onNavigateToPaywall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.onEvent(AddEditEvent.Save) },
                enabled = !formState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = if (formState.isEditMode) "Save Changes" else "Add Connection")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
