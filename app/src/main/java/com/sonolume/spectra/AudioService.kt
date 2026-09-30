package com.sonolume.spectra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioService : Service() {
    companion object {
        private const val CHANNEL_ID = "spectra_native_audio"
        private const val NOTIFICATION_ID = 6041
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.audio_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spectra)
            .setContentTitle("Spectra")
            .setContentText(getString(R.string.audio_notification))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        NativeAudio.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NativeAudio.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NativeAudio.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
