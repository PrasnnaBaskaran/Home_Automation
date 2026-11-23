package com.example.homesphere.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

enum class AlertChannelType {
    LOW_WATER,
    ROUTER_DISCONNECT,
    DOOR_ACTIVITY,
    DEVICE_DISCONNECT,
    AUTOMATION_EVENT
}

class HomeSphereNotificationManager(private val context: Context) {

    private val idCounter = AtomicInteger(1000)

    init {
        createChannels()
    }

    private fun createChannels() {
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendAlert(
        type: AlertChannelType,
        title: String,
        message: String
    ): Boolean {
        if (!hasNotificationPermission()) return false

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        return try {
            NotificationManagerCompat.from(context).notify(idCounter.incrementAndGet(), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "homesphere_alerts_channel"
    }
}
