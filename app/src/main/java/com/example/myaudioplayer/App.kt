package com.example.myaudioplayer

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

const val CHANNEL_ID = "My Music Player"

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "Media playback",
                NotificationManager.IMPORTANCE_LOW
            )

            notificationChannel.description = "Media playback controls";
            notificationChannel.setShowBadge(false);
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC;

            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}