package com.nofollow.app.features.tracker.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nofollow.app.R
import com.nofollow.app.core.common.formatStamp
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowEventEntity
import com.nofollow.app.features.tracker.data.local.FollowEventType

/**
 * Satu notifikasi kumulatif: tiap deteksi baru membunyikan alert lagi, isinya semua unfollow yang
 * belum dibaca. "Dibaca" = user membuka app (lihat TrackerStatusRepository.markEventsRead).
 */
class UnfollowNotifier(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val eventDao: FollowEventDao,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // Tingkat penting channel tidak bisa dinaikkan lewat kode setelah dibuat, jadi channel lama (DEFAULT, tanpa
        // heads-up) dihapus dan diganti channel baru HIGH supaya notifikasi unfollow muncul sebagai pop-up.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(CHANNEL_ID, "Unfollow", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pemberitahuan saat ada akun yang berhenti mengikuti kamu"
        }
        manager.createNotificationChannel(channel)
    }

    /** Tampilkan notifikasi kalau ada unfollow yang terdeteksi sejak [since] (epoch ms). */
    @SuppressLint("MissingPermission") // izin dicek di canPost()
    suspend fun notifyIfNew(since: Long) {
        val owner = sessionStore.session.value?.userId ?: return
        val unread = eventDao.findUnnotifiedUnfollows(owner)
        if (unread.none { it.occurredAt >= since }) return
        if (!canPost()) return

        val newestFirst = unread.asReversed()
        val title = if (unread.size == 1) "@${unread[0].username} unfollow kamu" else "${unread.size} orang unfollow kamu"
        val style = NotificationCompat.InboxStyle()
        newestFirst.take(MAX_LINES).forEach { style.addLine(line(it)) }
        if (newestFirst.size > MAX_LINES) style.setSummaryText("+${newestFirst.size - MAX_LINES} lainnya")

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unfollow)
            .setContentTitle(title)
            .setContentText(line(newestFirst.first()))
            .setStyle(style)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun line(e: FollowEventEntity): String {
        val seen = e.lastSeenAt?.let { "terlihat sampai ${formatStamp(it)}, " }.orEmpty()
        val tag = if (e.type == FollowEventType.UNAVAILABLE) " (hilang/nonaktif)" else ""
        return "@${e.username}$tag: ${seen}terdeteksi ${formatStamp(e.occurredAt)}"
    }

    private companion object {
        const val CHANNEL_ID = "unfollow_alert"
        const val LEGACY_CHANNEL_ID = "unfollow"
        const val NOTIFICATION_ID = 1001
        const val MAX_LINES = 6
    }
}