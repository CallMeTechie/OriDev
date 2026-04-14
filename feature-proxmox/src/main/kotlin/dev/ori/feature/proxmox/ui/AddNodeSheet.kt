package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.ori.core.common.extension.isValidHost
import dev.ori.core.ui.icons.lucide.Eye
import dev.ori.core.ui.icons.lucide.EyeOff
import dev.ori.core.ui.icons.lucide.LucideIcons

private const val DEFAULT_PORT = "8006"

@Composable
fun AddNodeSheet(
    onDismiss: () -> Unit,
    onProbeAndAdd: (AddNodePending) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(DEFAULT_PORT) }
    var tokenId by remember { mutableStateOf("") }
    var tokenSecret by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val hostValid = host.isBlank() || host.isValidHost()
    val portInt = port.toIntOrNull()
    val portValid = portInt != null && portInt in 1..65535
    val canSubmit = name.isNotBlank() &&
        host.isNotBlank() &&
        host.isValidHost() &&
        portValid &&
        tokenId.isNotBlank() &&
        tokenSecret.isNotBlank() &&
        !isLoading

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Add Proxmox Node",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("pve.example.com or 10.0.0.1") },
            singleLine = true,
            isError = !hostValid,
            supportingText = {
                if (!hostValid) {
                    Text("Invalid hostname or IP")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = port,
            onValueChange = { new -> port = new.filter { it.isDigit() }.take(5) },
            label = { Text("Port") },
            singleLine = true,
            isError = port.isNotBlank() && !portValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = tokenId,
            onValueChange = { tokenId = it },
            label = { Text("Token ID") },
            placeholder = { Text("user@pam!tokenname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = tokenSecret,
            onValueChange = { tokenSecret = it },
            label = { Text("Token Secret") },
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
                            "Hide secret"
                        } else {
                            "Show secret"
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onProbeAndAdd(
                        AddNodePending(
                            name = name.trim(),
                            host = host.trim(),
                            port = portInt ?: DEFAULT_PORT.toInt(),
                            tokenId = tokenId.trim(),
                            tokenSecret = tokenSecret,
                        ),
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 8.dp),
                    )
                }
                Text("Probe and Add")
            }
        }
    }
}
