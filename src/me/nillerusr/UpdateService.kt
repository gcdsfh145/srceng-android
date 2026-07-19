package me.nillerusr

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import com.valvesoftware.source.R

class UpdateService : Service() {
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!serviceWork) {
            serviceWork = true
            sendNotification(intent?.getStringExtra("update_url"))
        }
        return START_NOT_STICKY
    }

    private fun sendNotification(updateUrl: String?) {
        if (updateUrl.isNullOrEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, browserIntent, pendingFlags)
        val content = RemoteViews(packageName, R.layout.update_notify)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "updates"
            val channel = NotificationChannel(
                channelId,
                "Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { enableVibration(true) }
            notificationManager.createNotificationChannel(channel)
            Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Update available")
                .setWhen(System.currentTimeMillis())
                .setCustomContentView(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .build()
        } else {
            Notification(R.drawable.ic_launcher, "Update available", System.currentTimeMillis()).apply {
                contentView = content
                contentIntent = pendingIntent
                flags = flags or Notification.FLAG_AUTO_CANCEL
                defaults = defaults or Notification.DEFAULT_ALL
                priority = Notification.PRIORITY_HIGH
            }
        }
        notificationManager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        var serviceWork = false
    }
}
