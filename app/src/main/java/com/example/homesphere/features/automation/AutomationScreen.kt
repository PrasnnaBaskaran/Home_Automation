package com.example.homesphere.features.automation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.EmptyStateCard
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.AutomationActionType
import com.example.homesphere.domain.model.AutomationRule
import com.example.homesphere.domain.model.AutomationTriggerType

@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingRule by remember { mutableStateOf<AutomationRule?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 92.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automations", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Real-time local rule engine • Executes against LAN hardware",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(label = "LIVE ENGINE", tone = StatusTone.ONLINE, pulsing = true)
                    }
                }
            }

            if (state.rules.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "No Automation Rules",
                        subtitle = "Tap + to create time-based or sensor-triggered smart home rules."
                    )
                }
            } else {
                items(state.rules, key = { it.id }) { rule ->
                    AutomationRuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRuleEnabled(rule, it) },
                        onRunNow = { viewModel.executeRuleNow(rule) },
                        onEdit = { editingRule = rule },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }

            item {
                SectionHeader(title = "Execution History")
            }

            if (state.executionHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PlayArrow,
                        title = "No Executions Recorded",
                        subtitle = "Use 'Run Now' on any rule to dispatch its real-time hardware command or alert."
                    )
                }
            } else {
                item {
                    HomeSphereCard {
                        state.executionHistory.take(8).forEachIndexed { idx, event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        event.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    FormatUtils.formatRelativeTime(event.timestamp),
                                    style = HomeSphereTextStyles.MonoMetricSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx < state.executionHistory.take(8).lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
                .testTag("add_automation_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create automation rule")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp)
        )
    }

    if (showCreateDialog) {
        AutomationRuleEditorDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onSave = {
                viewModel.saveRule(it)
                showCreateDialog = false
            }
        )
    }

    editingRule?.let { ruleToEdit ->
        AutomationRuleEditorDialog(
            initial = ruleToEdit,
            onDismiss = { editingRule = null },
            onSave = {
                viewModel.saveRule(it)
                editingRule = null
            }
        )
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = HomeSphereTheme.extendedColors
    HomeSphereCard(
        accentGlow = if (rule.enabled) MaterialTheme.colorScheme.primary else null,
        borderColor = if (rule.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else ext.cardBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IF: ${rule.trigger}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "THEN: ${rule.action}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("automation_toggle_${rule.id}")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rule.lastExecutedAt != null) {
                    "Last run ${FormatUtils.formatRelativeTime(rule.lastExecutedAt)} (${rule.executionCount}x)"
                } else "Never executed",
                style = HomeSphereTextStyles.MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onRunNow,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("automation_run_${rule.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run rule now", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Run Now", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit rule", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete rule", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AutomationRuleEditorDialog(
    initial: AutomationRule?,
    onDismiss: () -> Unit,
    onSave: (AutomationRule) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var triggerText by remember { mutableStateOf(initial?.trigger ?: "Every day at 6:00 PM") }
    var actionText by remember { mutableStateOf(initial?.action ?: "Switch on Room Light (75%)") }
    var triggerType by remember { mutableStateOf(initial?.triggerType ?: AutomationTriggerType.SCHEDULE_TIME) }
    var actionType by remember { mutableStateOf(initial?.actionType ?: AutomationActionType.TURN_ON_LIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Automation Rule" else "Edit Automation Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Trigger Type", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AutomationTriggerType.entries) { tt ->
                        FilterChip(
                            selected = triggerType == tt,
                            onClick = {
                                triggerType = tt
                                if (initial == null) {
                                    triggerText = when (tt) {
                                        AutomationTriggerType.SCHEDULE_TIME -> "Every day at 6:00 PM"
                                        AutomationTriggerType.WATER_LOW -> "Water level drops below 20%"
                                        AutomationTriggerType.ROUTER_DISCONNECTED -> "Wi-Fi Router goes offline"
                                        AutomationTriggerType.DOOR_ACTIVITY -> "Door sensor detects OPEN"
                                    }
                                }
                            },
                            label = { Text(tt.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = triggerText,
                    onValueChange = { triggerText = it },
                    label = { Text("Trigger Condition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Action Type", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AutomationActionType.entries) { at ->
                        FilterChip(
                            selected = actionType == at,
                            onClick = {
                                actionType = at
                                if (initial == null) {
                                    actionText = when (at) {
                                        AutomationActionType.TURN_ON_LIGHT -> "Turn ON Room Light (75%)"
                                        AutomationActionType.SET_FAN_SPEED -> "Set Smart Fan to 50%"
                                        AutomationActionType.SEND_NOTIFICATION -> "Send local alert notification"
                                        AutomationActionType.SECURITY_ALERT -> "Trigger Door Security Alert"
                                    }
                                }
                            },
                            label = { Text(at.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = actionText,
                    onValueChange = { actionText = it },
                    label = { Text("Action Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            (initial ?: AutomationRule(
                                name = name,
                                trigger = triggerText,
                                action = actionText,
                                triggerType = triggerType,
                                actionType = actionType
                            )).copy(
                                name = name.trim(),
                                trigger = triggerText.trim(),
                                action = actionText.trim(),
                                triggerType = triggerType,
                                actionType = actionType
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
