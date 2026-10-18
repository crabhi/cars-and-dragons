package cz.crabhi.dragons.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Keeps the process and the microphone alive while a game runs, even with the phone screen off. */
class GameService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val res = Czech.resources(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, res.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(res.getString(R.string.app_name))
                .setContentText(res.getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Android 14 refuses a microphone service started from the background; the game still runs
            // while the car screen or the activity is visible.
            Log.w(TAG, "Could not start foreground service", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "GameService"
        private const val CHANNEL = "game"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, GameService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameService::class.java))
        }
    }
}
