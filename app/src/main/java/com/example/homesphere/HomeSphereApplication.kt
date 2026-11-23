package com.example.homesphere

import android.app.Application
import com.example.homesphere.di.AppContainer

class HomeSphereApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
