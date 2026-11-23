package com.example.homesphere.core.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.homesphere.di.AppContainer
import com.example.homesphere.domain.model.DeviceType
import com.example.homesphere.features.activity.ActivityHistoryScreen
import com.example.homesphere.features.activity.ActivityHistoryViewModel
import com.example.homesphere.features.automation.AutomationScreen
import com.example.homesphere.features.automation.AutomationViewModel
import com.example.homesphere.features.bluetooth.BluetoothManagerScreen
import com.example.homesphere.features.bluetooth.BluetoothViewModel
import com.example.homesphere.features.dashboard.DashboardScreen
import com.example.homesphere.features.dashboard.DashboardViewModel
import com.example.homesphere.features.devices.ConnectedDevicesScreen
import com.example.homesphere.features.devices.ConnectedDevicesViewModel
import com.example.homesphere.features.door.DoorViewModel
import com.example.homesphere.features.door.SmartDoorScreen
import com.example.homesphere.features.fan.FanControlScreen
import com.example.homesphere.features.fan.FanViewModel
import com.example.homesphere.features.lighting.LightingViewModel
import com.example.homesphere.features.lighting.RoomLightScreen
import com.example.homesphere.features.network.NetworkAnalyzerScreen
import com.example.homesphere.features.network.NetworkViewModel
import com.example.homesphere.features.settings.SettingsScreen
import com.example.homesphere.features.settings.SettingsViewModel
import com.example.homesphere.features.water.WaterMonitorScreen
import com.example.homesphere.features.water.WaterViewModel

object HomeSphereRoutes {
    const val DASHBOARD = "dashboard"
    const val NETWORK = "network"
    const val AUTOMATION = "automation"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"

    const val FAN_CONTROL = "fan_control"
    const val SMART_DOOR = "smart_door"
    const val WATER_MONITOR = "water_monitor"
    const val ROOM_LIGHT = "room_light"
    const val BLUETOOTH_MANAGER = "bluetooth_manager"
    const val CONNECTED_DEVICES = "connected_devices"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = HomeSphereRoutes.DASHBOARD,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    TopLevelDestination(
        route = HomeSphereRoutes.NETWORK,
        label = "Network",
        selectedIcon = Icons.Filled.Router,
        unselectedIcon = Icons.Outlined.Router
    ),
    TopLevelDestination(
        route = HomeSphereRoutes.AUTOMATION,
        label = "Automate",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    TopLevelDestination(
        route = HomeSphereRoutes.ACTIVITY,
        label = "Activity",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    ),
    TopLevelDestination(
        route = HomeSphereRoutes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@Composable
fun HomeSphereRootNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showTopLevelNav = currentRoute in topLevelDestinations.map { it.route }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (showTopLevelNav && useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            modifier = Modifier.testTag("nav_${dest.route}")
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTopLevelNav && !useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeSphereRoutes.DASHBOARD,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(HomeSphereRoutes.DASHBOARD) {
                        val vm: DashboardViewModel = viewModel(
                            factory = DashboardViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                automationRepository = container.automationRepository,
                                activityRepository = container.activityRepository,
                                bluetoothManager = container.bluetoothManager
                            )
                        )
                        DashboardScreen(
                            viewModel = vm,
                            onNavigateToDevice = { type ->
                                when (type) {
                                    DeviceType.FAN -> navController.navigate(HomeSphereRoutes.FAN_CONTROL)
                                    DeviceType.DOOR -> navController.navigate(HomeSphereRoutes.SMART_DOOR)
                                    DeviceType.WATER_MONITOR -> navController.navigate(HomeSphereRoutes.WATER_MONITOR)
                                    DeviceType.ROOM_LIGHT -> navController.navigate(HomeSphereRoutes.ROOM_LIGHT)
                                    DeviceType.WIFI_ROUTER -> navigateTopLevel(HomeSphereRoutes.NETWORK)
                                    DeviceType.BLUETOOTH -> navController.navigate(HomeSphereRoutes.BLUETOOTH_MANAGER)
                                }
                            },
                            onNavigateToNotifications = { navigateTopLevel(HomeSphereRoutes.ACTIVITY) },
                            onNavigateToSettings = { navigateTopLevel(HomeSphereRoutes.SETTINGS) },
                            onNavigateToConnectedDevices = { navController.navigate(HomeSphereRoutes.CONNECTED_DEVICES) },
                            contentPadding = innerPadding
                        )
                    }

                    composable(HomeSphereRoutes.NETWORK) {
                        val vm: NetworkViewModel = viewModel(
                            factory = NetworkViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                connectedDevicesRepository = container.connectedDevicesRepository,
                                settingsDataStore = container.settingsDataStore
                            )
                        )
                        NetworkAnalyzerScreen(
                            viewModel = vm,
                            onOpenConnectedDevices = { navController.navigate(HomeSphereRoutes.CONNECTED_DEVICES) },
                            contentPadding = innerPadding
                        )
                    }

                    composable(HomeSphereRoutes.AUTOMATION) {
                        val vm: AutomationViewModel = viewModel(
                            factory = AutomationViewModel.Factory(
                                automationRepository = container.automationRepository,
                                activityRepository = container.activityRepository,
                                hardwareHub = container.hardwareHub,
                                notificationManager = container.notificationManager
                            )
                        )
                        AutomationScreen(
                            viewModel = vm,
                            contentPadding = innerPadding
                        )
                    }

                    composable(HomeSphereRoutes.ACTIVITY) {
                        val vm: ActivityHistoryViewModel = viewModel(
                            factory = ActivityHistoryViewModel.Factory(container.activityRepository)
                        )
                        ActivityHistoryScreen(
                            viewModel = vm,
                            contentPadding = innerPadding
                        )
                    }

                    composable(HomeSphereRoutes.SETTINGS) {
                        val vm: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                settingsDataStore = container.settingsDataStore,
                                hardwareHub = container.hardwareHub,
                                notificationManager = container.notificationManager
                            )
                        )
                        SettingsScreen(
                            viewModel = vm,
                            contentPadding = innerPadding
                        )
                    }

                    composable(HomeSphereRoutes.FAN_CONTROL) {
                        val vm: FanViewModel = viewModel(
                            factory = FanViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                activityRepository = container.activityRepository
                            )
                        )
                        FanControlScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(HomeSphereRoutes.SMART_DOOR) {
                        val vm: DoorViewModel = viewModel(
                            factory = DoorViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                activityRepository = container.activityRepository
                            )
                        )
                        SmartDoorScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(HomeSphereRoutes.WATER_MONITOR) {
                        val vm: WaterViewModel = viewModel(
                            factory = WaterViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                settingsDataStore = container.settingsDataStore
                            )
                        )
                        WaterMonitorScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(HomeSphereRoutes.ROOM_LIGHT) {
                        val vm: LightingViewModel = viewModel(
                            factory = LightingViewModel.Factory(
                                hardwareHub = container.hardwareHub,
                                activityRepository = container.activityRepository
                            )
                        )
                        RoomLightScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(HomeSphereRoutes.BLUETOOTH_MANAGER) {
                        val vm: BluetoothViewModel = viewModel(
                            factory = BluetoothViewModel.Factory(
                                bluetoothManager = container.bluetoothManager,
                                activityRepository = container.activityRepository
                            )
                        )
                        BluetoothManagerScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(HomeSphereRoutes.CONNECTED_DEVICES) {
                        val vm: ConnectedDevicesViewModel = viewModel(
                            factory = ConnectedDevicesViewModel.Factory(
                                repository = container.connectedDevicesRepository,
                                networkInspector = container.androidNetworkInspector,
                                activityRepository = container.activityRepository
                            )
                        )
                        ConnectedDevicesScreen(
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
