package com.nofollow.app.features.home.domain.usecase

import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.model.HomeSyncSkip
import com.nofollow.app.features.kepo.domain.model.KepoOutcome
import com.nofollow.app.features.kepo.domain.usecase.SyncKepoUseCase
import com.nofollow.app.features.tracker.domain.model.FollowingOutcome
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.features.tracker.domain.model.SyncOutcome
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import com.nofollow.app.features.tracker.domain.usecase.SyncFollowersUseCase
import com.nofollow.app.features.tracker.domain.usecase.SyncFollowingUseCase
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sweep follower dulu, lalu following, lalu Kepo (berurutan, tidak paralel) dengan jeda [SyncConfig.followingStartGapMs]
 * di antaranya. Following sendiri yang menolak kalau sweep follower belum selesai atau jedanya belum cukup.
 * Masing-masing repository juga menolak kalau belum baseline, sedang cooldown, atau belum cukup lama sejak
 * sweep terakhir.
 *
 * Sinkron otomatis terjadwal menyapu semuanya seperti tombol Sinkron (following dan Kepo tidak menunggu umur data).
 * Kepo ikut untuk tombol Sinkron, sinkron pertama, dan sinkron otomatis terjadwal (jarak polling, jam tenang,
 * cooldown, dan plafonnya diatur KepoRepository sendiri). Pengecekan saat Beranda dibuka tetap jalan sendiri lewat
 * KepoViewModel, supaya buka app tidak ikut menahan tombol dan kartu progres.
 * Kepo terisolasi: kegagalannya tidak mengubah hasil sinkron follower dan following.
 *
 * [trigger]: MANUAL = tombol Sinkron; APP_OPEN = app dibuka; SCHEDULED = sinkron otomatis sesuai mode.
 */
class SyncAllUseCase(
    private val syncFollowers: SyncFollowersUseCase,
    private val syncFollowing: SyncFollowingUseCase,
    private val syncKepo: SyncKepoUseCase,
    private val config: SyncConfig,
) {
    suspend operator fun invoke(trigger: SyncTrigger): HomeSyncResult {
        val manual = trigger == SyncTrigger.MANUAL
        // Sinkron otomatis menyapu following dan Kepo selengkap tombol Sinkron; bedanya hanya jam tenang, penanganan
        // error, dan kantong request yang tetap versi otomatis.
        val full = manual || trigger == SyncTrigger.SCHEDULED
        val followers = syncFollowers(trigger)
        // Disambung: kalau sweep follower baru saja selesai, tunggu jeda singkat (30 detik) lalu lanjut ke following.
        if (followers is SyncOutcome.Completed) delay(config.followingStartGapMs.milliseconds)
        val following = try {
            syncFollowing(manual, full)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        val firstSync = followers is SyncOutcome.Completed && followers.baseline
        val troubled = followers is SyncOutcome.Halted || followers is SyncOutcome.Failed
        val kepo = if ((manual || firstSync || trigger == SyncTrigger.SCHEDULED) && !troubled) {
            if (following is FollowingOutcome.Completed) delay(config.followingStartGapMs.milliseconds)
            try {
                syncKepo(manual = manual, full = full)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return summarize(followers, following, kepo)
    }

    private fun summarize(followers: SyncOutcome, following: FollowingOutcome?, kepo: KepoOutcome?): HomeSyncResult = when {
        followers is SyncOutcome.Halted -> HomeSyncResult.Halted(followers.reason)
        followers is SyncOutcome.Failed -> HomeSyncResult.Failed
        followers is SyncOutcome.Aborted -> HomeSyncResult.Aborted
        followers is SyncOutcome.Progress -> HomeSyncResult.InProgress
        following is FollowingOutcome.Progress -> HomeSyncResult.InProgress
        following is FollowingOutcome.Completed -> HomeSyncResult.Synced(kepo.newInteractions())
        followers is SyncOutcome.Skipped -> HomeSyncResult.Skipped(followers.reason.toHome(), followers.until)
        else -> HomeSyncResult.Synced(kepo.newInteractions())
    }

    private fun KepoOutcome?.newInteractions(): Int? = (this as? KepoOutcome.Completed)?.newInteractions

    private fun SkipReason.toHome() = when (this) {
        SkipReason.COOLING_DOWN -> HomeSyncSkip.COOLING_DOWN
        SkipReason.QUIET_HOURS -> HomeSyncSkip.QUIET_HOURS
        SkipReason.NOT_DUE -> HomeSyncSkip.NOT_DUE
        SkipReason.TOO_SOON -> HomeSyncSkip.TOO_SOON
        SkipReason.DAILY_CAP -> HomeSyncSkip.DAILY_CAP
    }
}