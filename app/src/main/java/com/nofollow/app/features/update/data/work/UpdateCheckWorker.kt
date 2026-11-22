package com.nofollow.app.features.update.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nofollow.app.core.common.AppForeground
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.features.update.data.notify.UpdateNotifier
import com.nofollow.app.features.update.domain.usecase.CheckForUpdateUseCase
import com.nofollow.app.features.update.domain.usecase.ClaimUpdateNotificationUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cek pembaruan harian di latar belakang (dijadwalkan [UpdateCheckScheduler], hanya jalan kalau ada internet).
 * Tidak menyentuh Instagram. Kalau ada versi baru yang belum diberitahukan dan app sedang tertutup, kirim
 * notifikasi satu kali per versi. Kalau app sedang terbuka, dialog di dalam app yang memberi tahu.
 *
 * Selalu berakhir sukses: cek yang gagal (offline, repo belum publik) tidak perlu diulang, besok dicoba lagi.
 * Dibuat lewat factory bawaan WorkManager, jadi dilindungi aturan keep ListenableWorker di proguard-rules.pro.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val check: CheckForUpdateUseCase by inject()
    private val claim: ClaimUpdateNotificationUseCase by inject()
    private val notifier: UpdateNotifier by inject()

    override suspend fun doWork(): Result {
        try {
            val update = (check(ignoreInterval = true) as? AppResult.Success)?.data ?: return Result.success()
            if (AppForeground.isForeground) return Result.success()
            // Cek izin dulu sebelum mencatat "sudah diberitahu": kalau izin belum ada, versi ini belum dianggap terkirim.
            if (!notifier.canPost()) return Result.success()
            if (claim(update)) notifier.notify(update)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Diam-diam: dicoba lagi di jadwal berikutnya.
        }
        return Result.success()
    }
}