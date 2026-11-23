package com.example.homesphere.features.bluetooth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.BluetoothDeviceInfo
import com.example.homesphere.hardware.bluetooth.AndroidBluetoothManager
import com.example.homesphere.hardware.bluetooth.BluetoothHardwareSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BluetoothUiState(
    val snapshot: BluetoothHardwareSnapshot = BluetoothHardwareSnapshot(),
    val selectedDevice: BluetoothDeviceInfo? = null,
    val feedbackMessage: String? = null
)

class BluetoothViewModel(
    private val bluetoothManager: AndroidBluetoothManager,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val selectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BluetoothUiState> = combine(
        bluetoothManager.snapshot,
        selectedDevice,
        feedback
    ) { snap, selected, msg ->
        BluetoothUiState(
            snapshot = snap,
            selectedDevice = selected,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BluetoothUiState())

    fun requiredPermissions(): Array<String> = bluetoothManager.requiredPermissions()

    fun hasAllPermissions(): Boolean = bluetoothManager.hasAllPermissions()

    fun onEnterScreen() {
        bluetoothManager.registerReceiver()
        bluetoothManager.refreshState()
    }

    fun onLeaveScreen() {
        bluetoothManager.stopDiscovery()
        bluetoothManager.unregisterReceiver()
    }

    fun onPermissionsResult(granted: Boolean) {
        bluetoothManager.refreshState()
        feedback.value = if (granted) {
            "Bluetooth permissions granted"
        } else {
            "Bluetooth permissions denied. Grant permissions to scan nearby devices."
        }
    }

    fun startScan() {
        val started = bluetoothManager.startDiscovery()
        viewModelScope.launch {
            if (started) {
                activityRepository.logEvent(
                    title = "Bluetooth Discovery Started",
                    details = "Scanning for nearby discoverable Bluetooth peripherals.",
                    category = ActivityCategory.BLUETOOTH
                )
            } else {
                feedback.value = uiState.value.snapshot.statusMessage
            }
        }
    }

    fun stopScan() {
        bluetoothManager.stopDiscovery()
    }

    fun openSettingsIntent(): Intent = bluetoothManager.openBluetoothSettingsIntent()

    fun selectDevice(device: BluetoothDeviceInfo?) {
        selectedDevice.value = device
    }

    fun clearFeedback() {
        feedback.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.stopDiscovery()
        bluetoothManager.unregisterReceiver()
    }

    class Factory(
        private val bluetoothManager: AndroidBluetoothManager,
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BluetoothViewModel(bluetoothManager, activityRepository) as T
    }
}
