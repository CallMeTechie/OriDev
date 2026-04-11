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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.ui.component.OriDevTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditConnectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddEditConnectionViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
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
            OriDevTopBar(
                title = formState.title,
                onNavigateBack = onNavigateBack,
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
            // Name
            OutlinedTextField(
                value = formState.name,
                onValueChange = { viewModel.onEvent(AddEditEvent.NameChanged(it)) },
                label = { Text("Name") },
                placeholder = { Text("My Server") },
                isError = formState.nameError != null,
                supportingText = formState.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Host
            OutlinedTextField(
                value = formState.host,
                onValueChange = { viewModel.onEvent(AddEditEvent.HostChanged(it)) },
                label = { Text("Host") },
                placeholder = { Text("192.168.1.100 or server.example.com") },
                isError = formState.hostError != null,
                supportingText = formState.hostError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Port and Protocol row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Port
                OutlinedTextField(
                    value = formState.port,
                    onValueChange = { viewModel.onEvent(AddEditEvent.PortChanged(it)) },
                    label = { Text("Port") },
                    isError = formState.portError != null,
                    supportingText = formState.portError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )

                // Protocol dropdown
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
            OutlinedTextField(
                value = formState.username,
                onValueChange = { viewModel.onEvent(AddEditEvent.UsernameChanged(it)) },
                label = { Text("Username") },
                placeholder = { Text("root") },
                isError = formState.usernameError != null,
                supportingText = formState.usernameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Auth Method segmented button
            Text(
                text = "Authentication",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val authOptions = listOf(AuthMethod.PASSWORD, AuthMethod.SSH_KEY)
                authOptions.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = formState.authMethod == method,
                        onClick = { viewModel.onEvent(AddEditEvent.AuthMethodChanged(method)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = authOptions.size,
                        ),
                    ) {
                        Text(method.displayName)
                    }
                }
            }

            // Password or SSH Key path
            if (formState.authMethod == AuthMethod.PASSWORD) {
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
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = formState.credential,
                    onValueChange = { viewModel.onEvent(AddEditEvent.CredentialChanged(it)) },
                    label = { Text("SSH Key Path") },
                    placeholder = { Text("/storage/emulated/0/.ssh/id_ed25519") },
                    isError = formState.credentialError != null,
                    supportingText = formState.credentialError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Advanced section
            TextButton(
                onClick = { viewModel.onEvent(AddEditEvent.ToggleAdvanced) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(
                    imageVector = if (formState.isAdvancedExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
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
                    OutlinedTextField(
                        value = formState.startupCommand,
                        onValueChange = {
                            viewModel.onEvent(AddEditEvent.StartupCommandChanged(it))
                        },
                        label = { Text("Startup Command") },
                        placeholder = { Text("cd /opt/project && source venv/bin/activate") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = formState.projectDirectory,
                        onValueChange = {
                            viewModel.onEvent(AddEditEvent.ProjectDirectoryChanged(it))
                        },
                        label = { Text("Project Directory") },
                        placeholder = { Text("/home/user/project") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
