package com.vmers.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vmers.app.R
import com.vmers.app.VmersApp
import com.vmers.app.core.VMManager
import com.vmers.app.ui.VMDisplayActivity

class VMCoreService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(1001, notification)

        val vm = VMManager.getInstance()
        vm?.startVM()

        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, VMDisplayActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, VmersApp.CHANNEL_ID)
            .setContentTitle("Vmers - Android 15 Running")
            .setContentText("Tap to open the virtual container display")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        VMManager.getInstance()?.stopVM()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
