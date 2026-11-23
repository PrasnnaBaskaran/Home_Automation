package com.example.homesphere.features.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.core.notifications.AlertChannelType
import com.example.homesphere.core.notifications.HomeSphereNotificationManager
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.AutomationRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.AutomationActionType
import com.example.homesphere.domain.model.AutomationRule
import com.example.homesphere.domain.model.EventSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val executionHistory: List<ActivityEvent> = emptyList(),
    val feedbackMessage: String? = null
)

class AutomationViewModel(
    private val automationRepository: AutomationRepository,
    private val activityRepository: ActivityRepository,
    private val hardwareHub: HardwareSwitchingHub,
    private val notificationManager: HomeSphereNotificationManager
) : ViewModel() {

    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AutomationUiState> = combine(
        automationRepository.allRules,
        activityRepository.observeByCategory(ActivityCategory.AUTOMATION, 15),
        feedback
    ) { rules, history, msg ->
        AutomationUiState(
            rules = rules,
            executionHistory = history,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationUiState())

    fun saveRule(rule: AutomationRule) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                automationRepository.saveRule(rule)
                activityRepository.logEvent(
                    title = "Created Automation: ${rule.name}",
                    details = "When: ${rule.trigger} → Then: ${rule.action}",
                    category = ActivityCategory.AUTOMATION
                )
                feedback.value = "Automation rule created"
            } else {
                automationRepository.updateRule(rule)
                feedback.value = "Automation rule updated"
            }
        }
    }

    fun toggleRuleEnabled(rule: AutomationRule, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateRule(rule.copy(enabled = enabled))
        }
    }

    fun deleteRule(rule: AutomationRule) {
        viewModelScope.launch {
            automationRepository.deleteRule(rule.id)
            feedback.value = "Deleted '${rule.name}'"
        }
    }

    fun executeRuleNow(rule: AutomationRule) {
        viewModelScope.launch {
            automationRepository.recordExecution(rule)
            val cfg = hardwareHub.settings.value

            val (succeeded, outcomeDetail) = when (rule.actionType) {
                AutomationActionType.TURN_ON_LIGHT -> {
                    val res = hardwareHub.setLightBrightness(75)
                    if (res.isSuccess) {
                        true to "Room Light switched ON (75%) on ESP8266 (${cfg.esp8266Ip})."
                    } else {
                        false to "ESP8266 lighting controller unreachable at ${cfg.esp8266Ip}."
                    }
                }
                AutomationActionType.SET_FAN_SPEED -> {
                    val res = hardwareHub.setFanSpeed(50)
                    if (res.isSuccess) {
                        true to "Smart Fan set to 50% on ESP8266 (${cfg.esp8266Ip})."
                    } else {
                        false to "ESP8266 fan controller unreachable at ${cfg.esp8266Ip}."
                    }
                }
                AutomationActionType.SEND_NOTIFICATION -> {
                    true to "Local notification dispatched: ${rule.action}"
                }
                AutomationActionType.SECURITY_ALERT -> {
                    true to "Security alert dispatched: ${rule.action}"
                }
            }

            activityRepository.logEvent(
                title = if (succeeded) "Rule Executed: ${rule.name}" else "Rule Pending Hardware: ${rule.name}",
                details = outcomeDetail,
                category = ActivityCategory.AUTOMATION,
                severity = if (succeeded) EventSeverity.INFO else EventSeverity.WARNING
            )

            if (cfg.notificationsEnabled && cfg.automationAlertEnabled) {
                val channel = if (rule.actionType == AutomationActionType.SECURITY_ALERT) {
                    AlertChannelType.DOOR_ACTIVITY
                } else {
                    AlertChannelType.AUTOMATION_EVENT
                }
                notificationManager.sendAlert(
                    type = channel,
                    title = "Automation: ${rule.name}",
                    message = outcomeDetail
                )
            }
            feedback.value = if (succeeded) {
                "Executed '${rule.name}'"
            } else {
                "'${rule.name}' could not reach ESP8266 (${cfg.esp8266Ip})"
            }
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val automationRepository: AutomationRepository,
        private val activityRepository: ActivityRepository,
        private val hardwareHub: HardwareSwitchingHub,
        private val notificationManager: HomeSphereNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AutomationViewModel(
                automationRepository,
                activityRepository,
                hardwareHub,
                notificationManager
            ) as T
    }
}
