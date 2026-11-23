package com.example.homesphere.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.AutomationActionType
import com.example.homesphere.domain.model.AutomationRule
import com.example.homesphere.domain.model.AutomationTriggerType
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.DiscoveredDeviceCategory
import com.example.homesphere.domain.model.EventSeverity

@Entity(tableName = "activity_events")
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val details: String,
    val category: String,
    val timestamp: Long,
    val severity: String
)

fun ActivityEventEntity.toDomain(): ActivityEvent = ActivityEvent(
    id = id,
    title = title,
    details = details,
    category = runCatching { ActivityCategory.valueOf(category) }.getOrDefault(ActivityCategory.SYSTEM),
    timestamp = timestamp,
    severity = runCatching { EventSeverity.valueOf(severity) }.getOrDefault(EventSeverity.INFO)
)

fun ActivityEvent.toEntity(): ActivityEventEntity = ActivityEventEntity(
    id = id,
    title = title,
    details = details,
    category = category.name,
    timestamp = timestamp,
    severity = severity.name
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val trigger: String,
    val action: String,
    val triggerType: String,
    val actionType: String,
    val enabled: Boolean,
    val lastExecutedAt: Long?,
    val executionCount: Int,
    val createdAt: Long
)

fun AutomationRuleEntity.toDomain(): AutomationRule = AutomationRule(
    id = id,
    name = name,
    trigger = trigger,
    action = action,
    triggerType = runCatching { AutomationTriggerType.valueOf(triggerType) }
        .getOrDefault(AutomationTriggerType.SCHEDULE_TIME),
    actionType = runCatching { AutomationActionType.valueOf(actionType) }
        .getOrDefault(AutomationActionType.SEND_NOTIFICATION),
    enabled = enabled,
    lastExecutedAt = lastExecutedAt,
    executionCount = executionCount,
    createdAt = createdAt
)

fun AutomationRule.toEntity(): AutomationRuleEntity = AutomationRuleEntity(
    id = id,
    name = name,
    trigger = trigger,
    action = action,
    triggerType = triggerType.name,
    actionType = actionType.name,
    enabled = enabled,
    lastExecutedAt = lastExecutedAt,
    executionCount = executionCount,
    createdAt = createdAt
)

@Entity(tableName = "water_history")
data class WaterHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val percentage: Int,
    val currentLitres: Int,
    val tankCapacity: Int,
    val timestamp: Long
)

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val ipAddress: String,
    val macAddress: String?,
    val category: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val connectionDetails: String,
    val isManual: Boolean
)

fun ConnectedDeviceEntity.toDomain(): ConnectedDevice = ConnectedDevice(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    category = runCatching { DiscoveredDeviceCategory.valueOf(category) }
        .getOrDefault(DiscoveredDeviceCategory.OTHER_IOT),
    isOnline = isOnline,
    lastSeen = lastSeen,
    connectionDetails = connectionDetails,
    isManual = isManual
)

fun ConnectedDevice.toEntity(): ConnectedDeviceEntity = ConnectedDeviceEntity(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    category = category.name,
    isOnline = isOnline,
    lastSeen = lastSeen,
    connectionDetails = connectionDetails,
    isManual = isManual
)
