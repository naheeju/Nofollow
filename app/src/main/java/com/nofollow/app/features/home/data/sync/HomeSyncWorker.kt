package com.nofollow.app.features.home.data.sync

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nofollow.app.core.common.AppForeground
import com.nofollow.app.features.home.data.notify.HomeSyncNotifier
import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.usecase.SyncAllUseCase
import com.nofollow.app.features.tracker.data.notify.UnfollowNotifier
import com.nofollow.app.features.tracker.data.work.AutoSyncScheduler
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Satu-satunya jalur sinkron latar belakang: tombol Sinkron, sinkron pertama, dan sinkron otomatis terjadwal
 * ([SyncTrigger.SCHEDULED], dipicu [AutoSyncScheduler]) semuanya lewat sini. Worker foreground dengan notifikasi
 * "Menyinkron data", jadi tidak terputus saat app dibuang dari recents.
 * Memanggil [SyncAllUseCase]; pacing, cooldown, plafon harian, jam tenang, dan kunci SyncGate mengikuti trigger.
 *
 * Selesai: hasil disimpan ([HomeSyncResultStore]) supaya tampil sebagai dialog saat app dibuka lagi, dan kalau app
 * sedang tidak terbuka muncul notifikasi "selesai". Yang dilaporkan bergantung trigger:
 * - MANUAL: semua hasil.
 * - SCHEDULED: hanya yang perlu tindakan user (berhenti / daftar tidak lengkap). "Selesai" tiap beberapa jam akan
 *   jadi spam; unfollow yang ketemu tetap diberitahukan lewat [UnfollowNotifier].
 * - APP_OPEN: semua kecuali "dilewati".
 *
 * Apa pun hasilnya (juga kalau dibatalkan), jadwal sinkron otomatis berikutnya dihitung ulang di akhir.
 */
class HomeSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncAll: SyncAllUseCase by inject()
    private val unfollowNotifier: UnfollowNotifier by inject()
    private val notifier: HomeSyncNotifier by inject()
    private val store: HomeSyncResultStore by inject()
    private val scheduler: AutoSyncScheduler by inject()
    private val config: SyncConfig by inject()

    private fun trigger(): SyncTrigger =
        inputData.getString(KEY_TRIGGER)?.let { name -> SyncTrigger.entries.firstOrNull { it.name == name } }
        // Pekerjaan lama yang sudah antre sebelum update masih membawa KEY_MANUAL.
            ?: if (inputData.getBoolean(KEY_MANUAL, false)) SyncTrigger.MANUAL else SyncTrigger.APP_OPEN

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifier.foregroundInfo(id, automatic = trigger() == SyncTrigger.SCHEDULED)

    override suspend fun doWork(): Result {
        val trigger = trigger()
        val startedAt = System.currentTimeMillis()
        // null = belum selesai (dibatalkan di tengah jalan).
        var result: HomeSyncResult? = null
        try {
            try {
                setForeground(getForegroundInfo())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tidak boleh jadi foreground (mis. dibatasi sistem): tetap jalan, hanya tanpa jaminan saat app ditutup.
                Log.w(TAG, "tidak bisa jadi foreground: ${e.message}")
            }

            // Timer jeda antar halaman berhenti kalau CPU tidur, jadi CPU ditahan selama sinkron (dilepas di finally).
            val wakeLock = applicationContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nofollow:home-sync")
                .apply { setReferenceCounted(false); acquire(WAKELOCK_MS) }
            result = try {
                syncAll(trigger)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sinkron gagal", e)
                HomeSyncResult.Failed
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
            Log.d(TAG, "sinkron beranda selesai ($trigger): $result")

            // Unfollow yang terdeteksi di jalan ini tetap diberitahukan seperti biasa.
            try {
                unfollowNotifier.notifyIfNew(since = startedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "notifikasi unfollow gagal", e)
            }

            val report = when (trigger) {
                SyncTrigger.MANUAL -> true
                SyncTrigger.SCHEDULED -> result is HomeSyncResult.Halted || result is HomeSyncResult.Aborted
                else -> result !is HomeSyncResult.Skipped
            }
            if (report) {
                store.put(result)
                if (!AppForeground.isForeground) notifier.notifyDone(result)
            }
        } finally {
            // Selesai, gagal, atau dibatalkan (tombol Batal / manual mendahului): jadwal otomatis selalu dihitung ulang.
            scheduler.requestReschedule(minDelayMs = retryDelayMs(result))
        }
        return Result.success()
    }

    /** Jeda minimal sebelum bangun berikutnya, supaya hasil yang tidak selesai tidak langsung diulang. */
    private fun retryDelayMs(result: HomeSyncResult?): Long = when (result) {
        is HomeSyncResult.Skipped -> ((result.until ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
        HomeSyncResult.InProgress -> Random.nextLong(config.autoResumeMinMs, config.autoResumeMaxMs + 1)
        HomeSyncResult.Failed, null -> config.autoResumeMinMs
        else -> 0L
    }

    companion object {
        const val KEY_TRIGGER = "trigger"

        /** Kunci lama (boolean); dibaca supaya pekerjaan yang sudah antre sebelum update tetap jalan. */
        const val KEY_MANUAL = "manual"
        private const val TAG = "NofollowHomeSync"
        private const val WAKELOCK_MS = 2 * 60 * 60_000L
    }
}