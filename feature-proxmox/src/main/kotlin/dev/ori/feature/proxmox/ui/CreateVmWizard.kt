package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.TriangleAlert
import dev.ori.domain.model.ProxmoxVm

private const val TOTAL_STEPS = 4

@Composable
@Suppress("LongMethod")
fun CreateVmWizard(
    onNavigateToTerminal: (profileId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateVmWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            OriTopBar(
                title = stepTitle(state.step),
                navigationIcon = {
                    OriIconButton(
                        icon = LucideIcons.ChevronLeft,
                        contentDescription = "Zurück",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                WizardStep.SELECT_TEMPLATE -> SelectTemplateStep(state, viewModel::onEvent)
                WizardStep.CONFIGURE -> ConfigureStep(state, viewModel::onEvent)
                WizardStep.NETWORK -> NetworkStep(state, viewModel::onEvent)
                WizardStep.REVIEW -> ReviewStep(state, viewModel::onEvent)
                WizardStep.CLONING -> ProgressStep("Cloning template...")
                WizardStep.CONNECTING -> ProgressStep("Waiting for SSH...")
                WizardStep.DONE -> DoneStep(
                    state = state,
                    onOpenTerminal = { profileId -> onNavigateToTerminal(profileId) },
                    onBack = onBack,
                )
            }
        }
    }
}

private fun stepTitle(step: WizardStep): String {
    val userStep = when (step) {
        WizardStep.SELECT_TEMPLATE -> 1
        WizardStep.CONFIGURE -> 2
        WizardStep.NETWORK -> 3
        WizardStep.REVIEW -> 4
        WizardStep.CLONING -> 4
        WizardStep.CONNECTING -> 4
        WizardStep.DONE -> TOTAL_STEPS
    }
    return "Create VM - Step $userStep of $TOTAL_STEPS"
}

@Composable
private fun SelectTemplateStep(
    state: CreateVmWizardState,
    onEvent: (CreateVmWizardEvent) -> Unit,
) {
    Text("Pick a template VM", style = MaterialTheme.typography.titleMedium)
    if (state.templates.isEmpty()) {
        Text(
            "No templates found on this node.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(TEMPLATE_LIST_HEIGHT_DP.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.templates, key = { it.vmid }) { template ->
                TemplateCard(
                    template = template,
                    selected = state.selectedTemplate?.vmid == template.vmid,
                    onClick = { onEvent(CreateVmWizardEvent.SelectTemplate(template)) },
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { onEvent(CreateVmWizardEvent.NextStep) },
        enabled = state.selectedTemplate != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Next")
    }
}

@Composable
private fun TemplateCard(
    template: ProxmoxVm,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "vmid: ${template.vmid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigureStep(
    state: CreateVmWizardState,
    onEvent: (CreateVmWizardEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.newVmid.toString(),
        onValueChange = { new ->
            val parsed = new.filter { it.isDigit() }.toIntOrNull() ?: 0
            onEvent(CreateVmWizardEvent.UpdateConfig(newVmid = parsed))
        },
        label = { Text("VM ID") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.newName,
        onValueChange = { onEvent(CreateVmWizardEvent.UpdateConfig(newName = it)) },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Clone mode", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.fullClone,
            onClick = { onEvent(CreateVmWizardEvent.UpdateConfig(fullClone = true)) },
        )
        Text("Full Clone")
        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
        RadioButton(
            selected = !state.fullClone,
            onClick = { onEvent(CreateVmWizardEvent.UpdateConfig(fullClone = false)) },
        )
        Text("Linked Clone")
    }
    StepNav(
        onBack = { onEvent(CreateVmWizardEvent.PreviousStep) },
        onNext = { onEvent(CreateVmWizardEvent.NextStep) },
        nextEnabled = state.newName.isNotBlank() && state.newVmid > 0,
    )
}

@Composable
@Suppress("LongMethod")
private fun NetworkStep(
    state: CreateVmWizardState,
    onEvent: (CreateVmWizardEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.bridge,
        onValueChange = { onEvent(CreateVmWizardEvent.UpdateNetwork(bridge = it)) },
        label = { Text("Bridge") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Use static IP")
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Switch(
            checked = state.useStaticIp,
            onCheckedChange = {
                onEvent(CreateVmWizardEvent.UpdateNetwork(useStaticIp = it))
            },
        )
    }
    if (state.useStaticIp) {
        OutlinedTextField(
            value = state.staticIp,
            onValueChange = { onEvent(CreateVmWizardEvent.UpdateNetwork(staticIp = it)) },
            label = { Text("Static IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.gateway,
            onValueChange = { onEvent(CreateVmWizardEvent.UpdateNetwork(gateway = it)) },
            label = { Text("Gateway") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = state.sshUsername,
        onValueChange = { onEvent(CreateVmWizardEvent.UpdateNetwork(sshUsername = it)) },
        label = { Text("SSH Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.sshPassword,
        onValueChange = { onEvent(CreateVmWizardEvent.UpdateNetwork(sshPassword = it)) },
        label = { Text("SSH Password") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    StepNav(
        onBack = { onEvent(CreateVmWizardEvent.PreviousStep) },
        onNext = { onEvent(CreateVmWizardEvent.NextStep) },
        nextEnabled = state.sshUsername.isNotBlank() && state.sshPassword.isNotBlank(),
    )
}

@Composable
private fun ReviewStep(
    state: CreateVmWizardState,
    onEvent: (CreateVmWizardEvent) -> Unit,
) {
    Text("Review", style = MaterialTheme.typography.titleMedium)
    SummaryRow("Template", state.selectedTemplate?.name ?: "-")
    SummaryRow("VM ID", state.newVmid.toString())
    SummaryRow("Name", state.newName)
    SummaryRow("Clone", if (state.fullClone) "Full" else "Linked")
    SummaryRow("Bridge", state.bridge)
    SummaryRow(
        "IP Mode",
        if (state.useStaticIp) "Static ${state.staticIp}" else "DHCP",
    )
    SummaryRow("Username", state.sshUsername)
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    Row {
        TextButton(onClick = { onEvent(CreateVmWizardEvent.PreviousStep) }) {
            Text("Back")
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onEvent(CreateVmWizardEvent.CloneAndStart) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("Clone and Start")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(SUMMARY_LABEL_WIDTH_DP.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProgressStep(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DoneStep(
    state: CreateVmWizardState,
    onOpenTerminal: (profileId: Long) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.warningMessage != null) {
                Icon(
                    imageVector = LucideIcons.TriangleAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    state.warningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onBack) { Text("View VM") }
            } else {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("VM ready!", style = MaterialTheme.typography.titleMedium)
                val profileId = state.resultSshProfileId
                if (profileId != null) {
                    Button(onClick = { onOpenTerminal(profileId) }) {
                        Text("Open Terminal")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNav(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onNext, enabled = nextEnabled) { Text("Next") }
    }
}

private const val SUMMARY_LABEL_WIDTH_DP = 120
private const val TEMPLATE_LIST_HEIGHT_DP = 280
