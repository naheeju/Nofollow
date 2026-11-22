package com.nofollow.app.features.update.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nofollow.app.R
import com.nofollow.app.features.update.domain.model.AppUpdate

/**
 * Notifikasi "versi baru tersedia" saat app tertutup. Ketuk langsung mengunduh APK yang cocok dengan HP
 * ([AppUpdate.downloadUrl]) lewat browser, jadi user tidak perlu mencari halaman rilis dulu.
 * Channel sendiri dengan tingkat biasa (tanpa pop-up): kabar pembaruan tidak mendesak.
 */
class UpdateNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Pembaruan", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Pemberitahuan saat ada versi baru Nofollow"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Izin notifikasi diberikan dan notifikasi tidak dimatikan user. */
    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // izin dicek di canPost()
    fun notify(update: AppUpdate) {
        if (!canPost()) return
        val open = Intent(Intent.ACTION_VIEW, update.downloadUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(
            context, REQUEST_CODE, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Nofollow ${update.version} tersedia")
            .setContentText("Ketuk untuk mengunduh pembaruan")
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "app_update"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_CODE = 42
    }
}