package com.vmers.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vmers.app.core.VMManager

class VmersApp : Application() {

    companion object {
        lateinit var instance: VmersApp
            private set
        const val CHANNEL_ID = "vmers_core_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.vmers.app.debug.CrashHandler.init(this)
        createNotificationChannel()
        VMManager.initialize(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vmers Virtual Engine Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the virtual Android container running in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
