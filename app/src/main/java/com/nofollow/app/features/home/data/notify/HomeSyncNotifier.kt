package com.nofollow.app.features.home.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.nofollow.app.R
import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.model.HomeSyncSkip
import java.util.UUID

/**
 * Dua notifikasi sinkron Beranda: yang menetap selama sinkron berjalan di latar belakang (wajib dari Android untuk
 * kerja panjang, ada tombol Batal), dan satu notifikasi biasa saat selesai kalau app sedang tidak terbuka.
 */
class HomeSyncNotifier(private val context: Context) {

    fun foregroundInfo(workId: UUID, automatic: Boolean = false): ForegroundInfo {
        ensureChannels()
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val notification = NotificationCompat.Builder(context, RUNNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_unfollow)
            .setContentTitle("Menyinkron data")
            .setContentText(
                if (automatic) "Sinkron otomatis berjalan di latar belakang. Boleh ditinggal."
                else "Boleh ditinggal. Nanti diberi tahu kalau sudah selesai.",
            )
            .setContentIntent(openApp())
            .addAction(0, "Batal", cancel)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return foreground(notification)
    }

    @SuppressLint("MissingPermission") // izin dicek di canPost()
    fun notifyDone(result: HomeSyncResult) {
        if (!canPost()) return
        ensureChannels()
        val (title, text) = result.summary()
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_unfollow)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(DONE_ID, notification)
    }

    private fun foreground(notification: Notification): ForegroundInfo =
        ForegroundInfo(RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun HomeSyncResult.summary(): Pair<String, String> = when (this) {
        is HomeSyncResult.Synced -> "Sinkron selesai" to "Data follower dan following sudah diperbarui."
        HomeSyncResult.InProgress -> "Sinkron sebagian" to "Batas per sesi tercapai. Sisanya dilanjutkan otomatis."
        HomeSyncResult.Aborted -> "Sinkron dibatalkan" to "Daftar follower yang terbaca tampak tidak lengkap, jadi tidak ada data yang diubah."
        HomeSyncResult.Failed -> "Gagal menyinkron" to "Terjadi kesalahan. Akan dicoba lagi otomatis nanti."
        is HomeSyncResult.Halted -> "Sinkron berhenti" to "$reason. Selesaikan dulu di Instagram atau login ulang."
        is HomeSyncResult.Skipped -> when (reason) {
            HomeSyncSkip.DAILY_CAP -> "Jatah harian habis" to "Jatah request hari ini sudah terpakai. Sisanya dilanjutkan otomatis nanti."
            HomeSyncSkip.COOLING_DOWN -> "Sinkron dijeda" to "Sedang cooldown. Dilanjutkan otomatis setelah waktunya lewat."
            HomeSyncSkip.QUIET_HOURS -> "Jam tenang" to "Tidak ada request otomatis di jam ini."
            HomeSyncSkip.TOO_SOON -> "Terlalu cepat" to "Sinkron manual punya jeda supaya akun tetap aman."
            HomeSyncSkip.NOT_DUE -> "Belum waktunya" to "Belum waktunya sinkron."
        }
    }

    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(RUNNING_CHANNEL, "Sinkron berjalan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tampil selama sinkron berjalan di latar belakang"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Sinkron selesai", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pemberitahuan saat sinkron di latar belakang selesai"
            },
        )
    }

    private fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private companion object {
        const val RUNNING_CHANNEL = "home_sync_running"
        const val DONE_CHANNEL = "home_sync_done"
        const val RUNNING_ID = 1003
        const val DONE_ID = 1004
    }
}