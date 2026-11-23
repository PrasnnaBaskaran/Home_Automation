package com.example

import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.DoorLockState
import com.example.homesphere.domain.model.DoorSensorState
import com.example.homesphere.domain.model.DoorState
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanState
import com.example.homesphere.domain.model.LightMode
import com.example.homesphere.domain.model.LightState
import com.example.homesphere.domain.model.WaterLevelReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun defaultHardwareStatesStartDisconnectedAndAccurate() {
        val fan = FanState()
        assertEquals(ConnectionStatus.DISCONNECTED, fan.connectionStatus)
        assertFalse(fan.isOn)
        assertEquals(0, fan.speedPercent)
        assertEquals(FanMode.NORMAL, fan.mode)

        val door = DoorState()
        assertEquals(ConnectionStatus.DISCONNECTED, door.connectionStatus)
        assertEquals(DoorLockState.UNKNOWN, door.lockState)
        assertEquals(DoorSensorState.UNKNOWN, door.sensorState)

        val light = LightState()
        assertEquals(ConnectionStatus.DISCONNECTED, light.connectionStatus)
        assertFalse(light.isOn)
        assertEquals(LightMode.WARM, light.mode)
        assertNull(light.measuredWatts)

        val water = WaterLevelReading()
        assertEquals(ConnectionStatus.DISCONNECTED, water.connectionStatus)
        assertFalse(water.sensorConnected)
    }

    @Test
    fun waterLevelReadingCalculatesRemainingCapacityAndConnectionStatus() {
        val reading = WaterLevelReading(
            percentage = 65,
            currentLitres = 650,
            tankCapacity = 1000,
            sensorConnected = true
        )
        assertEquals(ConnectionStatus.CONNECTED, reading.connectionStatus)
        assertEquals(350, reading.remainingCapacityLitres)
    }

    @Test
    fun rssiDistanceEstimationAndQualityLabelsAreValid() {
        val meters = FormatUtils.estimateDistanceMeters(-55)
        assertTrue(meters > 0.5f && meters < 30f)
        assertEquals("Excellent", FormatUtils.rssiQualityLabel(-45))
        assertEquals("Very Good", FormatUtils.rssiQualityLabel(-60))
        assertEquals("Fair", FormatUtils.rssiQualityLabel(-72))
        assertEquals("Poor", FormatUtils.rssiQualityLabel(-85))
    }

    @Test
    fun uptimeFormatterProducesReadableDuration() {
        assertEquals("1d 2h 3m", FormatUtils.formatUptime(93780L))
        assertEquals("5m", FormatUtils.formatUptime(310L))
    }

    @Test
    fun relativeTimeFormatterProducesSecondsAndMinutesAgo() {
        val now = 1_000_000L
        assertEquals("Just now", FormatUtils.formatRelativeTime(now - 500L, now))
        assertEquals("5 seconds ago", FormatUtils.formatRelativeTime(now - 5_000L, now))
        assertEquals("29 seconds ago", FormatUtils.formatRelativeTime(now - 29_000L, now))
        assertEquals("1 minute ago", FormatUtils.formatRelativeTime(now - 60_000L, now))
        assertEquals("3 minutes ago", FormatUtils.formatRelativeTime(now - 180_000L, now))
    }

    @Test
    fun hostNormalizerStripsSchemeAndTrailingSlash() {
        assertEquals("192.168.1.42", FormatUtils.normalizeHostInput("http://192.168.1.42/"))
        assertEquals("192.168.1.42:8080", FormatUtils.normalizeHostInput("  https://192.168.1.42:8080/api  "))
        assertEquals("", FormatUtils.normalizeHostInput("192.168.1.42 invalid"))
    }
}
