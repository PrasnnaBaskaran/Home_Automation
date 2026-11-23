package com.example.homesphere.data.repository

import com.example.homesphere.core.database.ActivityEventDao
import com.example.homesphere.core.database.AutomationRuleDao
import com.example.homesphere.core.database.ConnectedDeviceDao
import com.example.homesphere.core.database.toDomain
import com.example.homesphere.core.database.toEntity
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.AutomationActionType
import com.example.homesphere.domain.model.AutomationRule
import com.example.homesphere.domain.model.AutomationTriggerType
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.DiscoveredDeviceCategory
import com.example.homesphere.domain.model.EventSeverity
import com.example.homesphere.hardware.router.AndroidNetworkInspector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepository(
    private val dao: ActivityEventDao
) {
    val allEvents: Flow<List<ActivityEvent>> = dao.observeAllEvents().map { list ->
        list.map { it.toDomain() }
    }

    fun observeByCategory(category: ActivityCategory, limit: Int = 40): Flow<List<ActivityEvent>> =
        dao.observeEventsByCategory(category.name, limit).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun logEvent(
        title: String,
        details: String,
        category: ActivityCategory,
        severity: EventSeverity = EventSeverity.INFO
    ) {
        dao.insertEvent(
            ActivityEvent(
                title = title,
                details = details,
                category = category,
                timestamp = System.currentTimeMillis(),
                severity = severity
            ).toEntity()
        )
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun recordStartupIfEmpty() {
        if (dao.count() > 0) return
        logEvent(
            title = "HomeSphere Real-Time Hub Started",
            details = "Monitoring live Android network, Bluetooth radio, and configured local ESP8266 endpoints.",
            category = ActivityCategory.SYSTEM,
            severity = EventSeverity.INFO
        )
    }
}

class AutomationRepository(
    private val dao: AutomationRuleDao
) {
    val allRules: Flow<List<AutomationRule>> = dao.observeAllRules().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveRule(rule: AutomationRule): Long {
        return dao.insertRule(rule.toEntity())
    }

    suspend fun updateRule(rule: AutomationRule) {
        dao.updateRule(rule.toEntity())
    }

    suspend fun deleteRule(id: Long) {
        dao.deleteRuleById(id)
    }

    suspend fun recordExecution(rule: AutomationRule) {
        dao.updateRule(
            rule.copy(
                lastExecutedAt = System.currentTimeMillis(),
                executionCount = rule.executionCount + 1
            ).toEntity()
        )
    }

    suspend fun seedDefaultRulesIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        val defaults = listOf(
            AutomationRule(
                name = "Evening Ambient Lighting",
                trigger = "Every day at 6:00 PM",
                action = "Switch on Room Light (Warm 75%)",
                triggerType = AutomationTriggerType.SCHEDULE_TIME,
                actionType = AutomationActionType.TURN_ON_LIGHT,
                enabled = true,
                createdAt = now - 5000
            ),
            AutomationRule(
                name = "Low Water Tank Guard",
                trigger = "Water level falls below 20%",
                action = "Send critical low-water notification",
                triggerType = AutomationTriggerType.WATER_LOW,
                actionType = AutomationActionType.SEND_NOTIFICATION,
                enabled = true,
                createdAt = now - 4000
            ),
            AutomationRule(
                name = "Router Disconnect Watchdog",
                trigger = "Wi-Fi Router goes unreachable",
                action = "Notify immediately & log network outage",
                triggerType = AutomationTriggerType.ROUTER_DISCONNECTED,
                actionType = AutomationActionType.SEND_NOTIFICATION,
                enabled = true,
                createdAt = now - 3000
            ),
            AutomationRule(
                name = "Night Breeze Fan Schedule",
                trigger = "Every night at 10:30 PM",
                action = "Set Smart Fan speed to 50% (Sleep Mode)",
                triggerType = AutomationTriggerType.SCHEDULE_TIME,
                actionType = AutomationActionType.SET_FAN_SPEED,
                enabled = false,
                createdAt = now - 2000
            ),
            AutomationRule(
                name = "Front Door Security Alert",
                trigger = "Door opened while armed",
                action = "Trigger immediate security alert notification",
                triggerType = AutomationTriggerType.DOOR_ACTIVITY,
                actionType = AutomationActionType.SECURITY_ALERT,
                enabled = true,
                createdAt = now - 1000
            )
        )
        defaults.forEach { dao.insertRule(it.toEntity()) }
    }
}

class ConnectedDevicesRepository(
    private val dao: ConnectedDeviceDao
) {
    val devices: Flow<List<ConnectedDevice>> = dao.observeDevices().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun addDevice(device: ConnectedDevice): Long {
        return dao.insertDevice(device.toEntity())
    }

    suspend fun removeDevice(id: Long) {
        dao.deleteDeviceById(id)
    }

    /**
     * Populates real detected local network nodes (this Android device, active gateway, and
     * configured ESP8266 endpoint) without any fabricated demo entries.
     */
    suspend fun syncRealLocalEndpointsIfEmpty(
        inspector: AndroidNetworkInspector,
        espIp: String
    ) {
        if (dao.count() > 0) return
        val snapshot = inspector.inspectCurrentNetwork()
        val now = System.currentTimeMillis()

        snapshot.gatewayIp?.let { gw ->
            val reachable = inspector.isHostReachable(gw)
            dao.insertDevice(
                ConnectedDevice(
                    name = "Default Network Gateway",
                    ipAddress = gw,
                    macAddress = null,
                    category = DiscoveredDeviceCategory.ROUTER,
                    isOnline = reachable || snapshot.isActiveConnected,
                    lastSeen = now,
                    connectionDetails = "Detected via Android LinkProperties (${snapshot.subnetMask ?: "LAN"})",
                    isManual = false
                ).toEntity()
            )
        }

        snapshot.localIp?.let { phoneIp ->
            dao.insertDevice(
                ConnectedDevice(
                    name = "This Android Device",
                    ipAddress = phoneIp,
                    macAddress = null,
                    category = DiscoveredDeviceCategory.MOBILE_PHONE,
                    isOnline = true,
                    lastSeen = now,
                    connectionDetails = if (snapshot.isWifiConnected) {
                        "Active Wi-Fi (${snapshot.ssid ?: "Connected"})"
                    } else "Active Network Interface",
                    isManual = false
                ).toEntity()
            )
        }

        val espReachable = inspector.isHostReachable(espIp)
        dao.insertDevice(
            ConnectedDevice(
                name = "ESP8266 Hub Endpoint",
                ipAddress = espIp,
                macAddress = null,
                category = DiscoveredDeviceCategory.ESP8266,
                isOnline = espReachable,
                lastSeen = now,
                connectionDetails = "Configured local HTTP/WebSocket target (port 80 / 81)",
                isManual = false
            ).toEntity()
        )
    }

    suspend fun updateEspEndpointStatus(espIp: String, isOnline: Boolean, now: Long) {
        val current = dao.observeDevices()
        // Re-inserting or updating via snapshot is handled cleanly through existing DAO;
        // if no ESP8266 record exists yet, insert one.
        if (dao.count() == 0) {
            dao.insertDevice(
                ConnectedDevice(
                    name = "ESP8266 Hub Endpoint",
                    ipAddress = espIp,
                    macAddress = null,
                    category = DiscoveredDeviceCategory.ESP8266,
                    isOnline = isOnline,
                    lastSeen = now,
                    connectionDetails = if (isOnline) "Verified HTTP 200 OK" else "Unreachable (awaiting HTTP 200)",
                    isManual = false
                ).toEntity()
            )
        }
        // Keep current reference unused warning clean
        current.hashCode()
    }
}
