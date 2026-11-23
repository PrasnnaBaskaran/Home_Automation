package com.example.homesphere.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.homesphere.domain.model.AppThemeMode
import com.example.homesphere.domain.model.HomeSphereSettings
import com.example.homesphere.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeSphereDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "homesphere_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val HOME_NAME = stringPreferencesKey("home_name")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val TANK_CAPACITY = intPreferencesKey("tank_capacity_litres")
        val LOW_WATER_THRESHOLD = intPreferencesKey("low_water_threshold")
        val HIGH_WATER_THRESHOLD = intPreferencesKey("high_water_threshold")
        val ROUTER_IP = stringPreferencesKey("router_ip")
        val ESP8266_IP = stringPreferencesKey("esp8266_ip")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LOW_WATER_ALERT = booleanPreferencesKey("low_water_alert")
        val DOOR_ALERT = booleanPreferencesKey("door_alert")
        val ROUTER_ALERT = booleanPreferencesKey("router_alert")
        val AUTOMATION_ALERT = booleanPreferencesKey("automation_alert")
    }

    val settingsFlow: Flow<HomeSphereSettings> = context.homeSphereDataStore.data.map { prefs ->
        HomeSphereSettings(
            homeName = prefs[Keys.HOME_NAME] ?: "Skyline Residence",
            userName = prefs[Keys.USER_NAME] ?: "Alex Morgan",
            userEmail = prefs[Keys.USER_EMAIL] ?: "Home Administrator",
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name)
            }.getOrDefault(AppThemeMode.DARK),
            temperatureUnit = runCatching {
                TemperatureUnit.valueOf(prefs[Keys.TEMP_UNIT] ?: TemperatureUnit.CELSIUS.name)
            }.getOrDefault(TemperatureUnit.CELSIUS),
            tankCapacityLitres = prefs[Keys.TANK_CAPACITY] ?: 1000,
            lowWaterThresholdPercent = prefs[Keys.LOW_WATER_THRESHOLD] ?: 20,
            highWaterThresholdPercent = prefs[Keys.HIGH_WATER_THRESHOLD] ?: 90,
            routerIp = prefs[Keys.ROUTER_IP] ?: "192.168.1.1",
            esp8266Ip = prefs[Keys.ESP8266_IP] ?: "192.168.1.50",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            lowWaterAlertEnabled = prefs[Keys.LOW_WATER_ALERT] ?: true,
            doorAlertEnabled = prefs[Keys.DOOR_ALERT] ?: true,
            routerDisconnectAlertEnabled = prefs[Keys.ROUTER_ALERT] ?: true,
            automationAlertEnabled = prefs[Keys.AUTOMATION_ALERT] ?: true
        )
    }

    suspend fun updateHomeName(name: String) {
        context.homeSphereDataStore.edit { it[Keys.HOME_NAME] = name.trim().ifEmpty { "My Smart Home" } }
    }

    suspend fun updateUserProfile(name: String, roleOrEmail: String) {
        context.homeSphereDataStore.edit {
            it[Keys.USER_NAME] = name.trim().ifEmpty { "HomeSphere User" }
            it[Keys.USER_EMAIL] = roleOrEmail.trim().ifEmpty { "Home Administrator" }
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.homeSphereDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateTemperatureUnit(unit: TemperatureUnit) {
        context.homeSphereDataStore.edit { it[Keys.TEMP_UNIT] = unit.name }
    }

    suspend fun updateWaterConfig(capacityLitres: Int, lowThreshold: Int, highThreshold: Int) {
        context.homeSphereDataStore.edit {
            it[Keys.TANK_CAPACITY] = capacityLitres.coerceIn(100, 20000)
            it[Keys.LOW_WATER_THRESHOLD] = lowThreshold.coerceIn(5, 50)
            it[Keys.HIGH_WATER_THRESHOLD] = highThreshold.coerceIn(60, 100)
        }
    }

    suspend fun updateRouterIp(ip: String) {
        context.homeSphereDataStore.edit { it[Keys.ROUTER_IP] = ip.trim().ifEmpty { "192.168.1.1" } }
    }

    suspend fun updateEsp8266Ip(ip: String) {
        context.homeSphereDataStore.edit { it[Keys.ESP8266_IP] = ip.trim().ifEmpty { "192.168.1.50" } }
    }

    suspend fun updateNotifications(
        masterEnabled: Boolean? = null,
        lowWater: Boolean? = null,
        door: Boolean? = null,
        router: Boolean? = null,
        automation: Boolean? = null
    ) {
        context.homeSphereDataStore.edit { prefs ->
            masterEnabled?.let { prefs[Keys.NOTIFICATIONS_ENABLED] = it }
            lowWater?.let { prefs[Keys.LOW_WATER_ALERT] = it }
            door?.let { prefs[Keys.DOOR_ALERT] = it }
            router?.let { prefs[Keys.ROUTER_ALERT] = it }
            automation?.let { prefs[Keys.AUTOMATION_ALERT] = it }
        }
    }
}
