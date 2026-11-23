package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.HomeSphereApplication
import com.example.homesphere.core.design.HomeSphereAppTheme
import com.example.homesphere.core.navigation.HomeSphereRootNavigation
import com.example.homesphere.domain.model.HomeSphereSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as HomeSphereApplication).container

        setContent {
            val settings by container.settingsDataStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = HomeSphereSettings())

            HomeSphereAppTheme(themeMode = settings.themeMode) {
                HomeSphereRootNavigation(container = container)
            }
        }
    }
}
