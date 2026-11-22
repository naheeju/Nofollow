package com.nofollow.app.features.tracker.data.repository

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppForeground
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.core.common.localDayNumber
import com.nofollow.app.core.common.nextLocalDayStart
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.avatar.AvatarSource
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.DailyUsageDao
import com.nofollow.app.features.tracker.data.local.DailyUsageEntity
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.local.TrackerDatabase
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.sync.ErrorPolicy
import com.nofollow.app.features.tracker.data.sync.FollowerPageParser
import com.nofollow.app.features.tracker.domain.model.RequestBucket
import com.nofollow.app.features.tracker.data.sync.SweepPacer
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.data.sync.SyncGate
import com.nofollow.app.features.tracker.data.sync.SyncPlanner
import com.nofollow.app.features.tracker.domain.model.FollowingOutcome
import com.nofollow.app.features.tracker.domain.model.FollowingUser
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.features.tracker.domain.repository.FollowingSyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sweep daftar following (friendships/{id}/following/), dipakai untuk mutual / fans / tidak follow balik.
 *
 * - Dicicil seperti sweep follower: cursor dan daftar parsial disimpan per halaman ([FollowingStore.saveProgress]),
 *   jadi sweep yang terputus (429, jaringan, batas halaman per jalan) dilanjutkan dari halaman terakhir, bukan dari nol.
 * - Daftar utuh yang dipakai UI baru diganti kalau sweep selesai penuh; tidak pernah setengah jadi.
 * - Disambung setelah follower, tidak pernah berbarengan: memakai kunci yang sama ([SyncGate]), menunggu sampai
 *   sweep follower tidak sedang berlangsung, dan baru mulai/lanjut setelah sweep follower terakhir selesai
 *   sedikitnya [SyncConfig.followingStartGapMs] (5 detik).
 * - Foto tiap akun diunduh per halaman ([AvatarStore.ensure]) selagi URL CDN-nya masih segar.
 * - Hanya jalan setelah baseline follower selesai. Pacing [SyncConfig.following] (1-3 detik + jeda mikir, rem latensi), plafon harian
 *   sendiri (kantong FOLLOWING), jam tenang dihormati untuk pemicu otomatis.
 * - Error memakai ErrorPolicy dan cooldown yang sama dengan sweep follower, jadi throttle di sini juga
 *   menghentikan sweep follower, dan sebaliknya.
 */
class FollowingSyncRepositoryImpl(
    private val api: InstagramApi,
    private val db: TrackerDatabase,
    private val syncDao: SyncStateDao,
    private val usageDao: DailyUsageDao,
    private val store: FollowingStore,
    private val avatars: AvatarStore,
    private val sessionStore: SessionStore,
    private val config: SyncConfig,
    private val gate: SyncGate,
) : FollowingSyncRepository {

    override suspend fun sync(manual: Boolean, full: Boolean): FollowingOutcome = gate.mutex.withLock {
        val owner = sessionStore.session.value?.userId ?: return FollowingOutcome.Halted("not_logged_in")
        val now = System.currentTimeMillis()

        val state = syncDao.get(owner)
        if (state == null || !state.baselineDone) return FollowingOutcome.Skipped(SkipReason.NOT_DUE)
        state.haltReason?.let { return FollowingOutcome.Halted(it) }
        if (now < state.cooldownUntil) return FollowingOutcome.Skipped(SkipReason.COOLING_DOWN, state.cooldownUntil)

        // Follower dulu, following sesudahnya: tunggu sweep follower selesai, lalu jeda singkat.
        if (state.sweepInProgress) return FollowingOutcome.Skipped(SkipReason.NOT_DUE)
        val followersDoneAt = state.lastCompletedSweepAt
        if (followersDoneAt == null || now - followersDoneAt < config.followingStartGapMs) {
            return FollowingOutcome.Skipped(SkipReason.NOT_DUE)
        }

        val previous = store.current()?.takeIf { it.ownerId == owner }
        // Tombol manual, atau daftar following pertama kali: ada orang yang menunggu, error jaringan tanpa cooldown.
        val userWaiting = manual || previous == null

        // Daftar following pertama kali ikut sinkron pertama: kalau app sedang terbuka (user menunggu), jam tenang
        // tidak berlaku, sama seperti baseline follower. Kalau tidak, sinkron pertama di malam hari berhenti di follower saja.
        val waitingUser = manual || (previous == null && AppForeground.isForeground)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!waitingUser && SyncPlanner.inQuietWindow(hour, config.quietStartHour, config.quietEndHour)) {
            return FollowingOutcome.Skipped(SkipReason.QUIET_HOURS)
        }

        // Lanjutkan sweep yang tersimpan kalau masih segar; selain itu mulai baru (kalau daftar lama sudah cukup tua).
        val saved = store.progress()?.takeIf { it.ownerId == owner && now - it.startedAt <= config.followingMaxSweepAgeMs }
        if (saved == null) {
            val minAge = if (full) config.followingManualMinAgeMs else config.followingAutoMinAgeMs
            if (previous != null && now - previous.syncedAt < minAge) {
                return FollowingOutcome.Skipped(SkipReason.NOT_DUE)
            }
        }

        val pacing = config.following
        val pacer = SweepPacer(pacing)
        val startedAt = saved?.startedAt ?: now
        val users = LinkedHashMap<String, FollowingUser>()
        saved?.users?.forEach { users[it.pk] = it }
        var cursor: String? = saved?.cursor
        var pages = saved?.pagesDone ?: 0
        var pagesThisRun = 0

        while (true) {
            if (pagesThisRun >= pacing.maxPagesPerRun) return FollowingOutcome.Progress(pages)
            if (pagesThisRun > 0) delay(pacer.nextDelayMs(pagesThisRun).milliseconds)
            if (pages >= MAX_TOTAL_PAGES) {
                store.clearProgress()
                return fail(owner, AppError.Unknown("terlalu banyak halaman following"))
            }
            if (users.size > config.maxAccountSize) return haltTooLarge(owner)
            if (!spendRequest(owner, previous?.users?.size ?: 0)) {
                Log.w(TAG, "plafon request harian tercapai (FOLLOWING), sweep following dilanjutkan besok")
                return FollowingOutcome.Skipped(SkipReason.DAILY_CAP, nextLocalDayStart(System.currentTimeMillis()))
            }

            val t0 = SystemClock.elapsedRealtime()
            val result = api.getChecked(followingUrl(owner, cursor))
            pacer.onLatency(SystemClock.elapsedRealtime() - t0)
            val body = when (result) {
                // Progres halaman sebelumnya sudah tersimpan; cooldown mengatur kapan boleh lanjut.
                is AppResult.Failure -> return fail(owner, result.error, userWaiting)
                is AppResult.Success -> result.data
            }
            val page = FollowerPageParser.parseFollowers(body)
            if (page == null) {
                // Format tidak dikenali: mengulang dari cursor yang sama hanya mengulang kesalahan.
                store.clearProgress()
                return fail(owner, AppError.Unknown("format daftar following tidak dikenali"))
            }
            Log.d(TAG, "following halaman ${pages + 1}: ${page.users.size} user, next=${page.nextMaxId != null}")

            // Foto diunduh per halaman selagi URL CDN-nya segar (bukan lewat API Instagram, tanpa cookie), supaya tab
            // Tidak follow balik langsung punya foto begitu daftar selesai. URL tidak disimpan di FollowingStore.
            try {
                avatars.ensure(page.users.mapNotNull { u -> u.picUrl?.let { AvatarSource(u.pk, it) } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "unduh foto following gagal: ${e.message}")
            }

            for (u in page.users) {
                users[u.pk] = FollowingUser(u.pk, u.username, u.fullName, u.isPrivate, u.isVerified)
            }
            pages++
            pagesThisRun++

            val next = page.nextMaxId ?: break
            // Cursor yang tidak maju = loop tak berujung; berhenti daripada menghajar server.
            if (next == cursor) {
                store.clearProgress()
                return fail(owner, AppError.Unknown("cursor following tidak maju"))
            }
            cursor = next
            store.saveProgress(FollowingStore.Progress(owner, startedAt, cursor, pages, users.values.toList()))
        }

        store.clearProgress()

        // Daftar baru jauh lebih kecil dari yang tersimpan: hampir pasti terbaca tidak lengkap. Jangan timpa.
        val before = previous?.users?.size ?: 0
        if (before >= SAFETY_MIN_PREVIOUS && users.size < before / 2) {
            Log.w(TAG, "daftar following ${users.size} vs tersimpan $before, tidak disimpan")
            return FollowingOutcome.Aborted(users.size, before)
        }

        store.save(owner, System.currentTimeMillis(), users.values.toList())
        Log.d(TAG, "sweep following selesai: ${users.size} akun, $pages request")
        FollowingOutcome.Completed(users.size)
    }

    private fun followingUrl(owner: String, cursor: String?): String {
        val base = "https://www.instagram.com/api/v1/friendships/$owner/following/?count=${config.following.pageSize}"
        return if (cursor == null) base else "$base&max_id=${Uri.encode(cursor)}"
    }

    /** Hitung satu request ke plafon harian kantong FOLLOWING. False = plafon tercapai, jangan kirim request. */
    private suspend fun spendRequest(owner: String, knownFollowing: Int): Boolean {
        val day = localDayNumber(System.currentTimeMillis())
        val key = config.usageKey(owner, RequestBucket.FOLLOWING)
        // Plafon dinamis menurut ukuran daftar following yang tersimpan; daftar pertama kali memakai lantai
        // (cukup untuk satu sapuan akun terbesar).
        val cap = config.followingCap(knownFollowing)
        return db.withTransaction {
            val used = usageDao.count(key, day) ?: 0
            if (used >= cap) {
                false
            } else {
                usageDao.ensure(DailyUsageEntity(key, day, 0))
                usageDao.increment(key, day)
                true
            }
        }
    }

    /** Sama dengan kebijakan sweep follower: cooldown / halt di sync_state, dibaca ulang supaya tidak menimpa data basi. */
    private suspend fun fail(owner: String, error: AppError, userWaiting: Boolean = false): FollowingOutcome {
        val s = syncDao.get(owner) ?: return FollowingOutcome.Failed(error, 0L)
        Log.w(TAG, "sweep following gagal: $error (level ${s.cooldownLevel})")
        val a = ErrorPolicy.onError(error, s.cooldownLevel, config, userWaiting)
        val now = System.currentTimeMillis()
        val until = now + a.cooldownMs
        val throttled = error == AppError.RateLimited || error == AppError.ActionBlocked
        syncDao.upsert(
            s.copy(
                cooldownUntil = until,
                cooldownLevel = a.nextLevel,
                haltReason = a.haltReason,
                throttleCount = s.throttleCount + if (throttled) 1 else 0,
                penaltyUntil = if (throttled) now + config.penaltyDurationMs else s.penaltyUntil,
            ),
        )
        return a.haltReason?.let { FollowingOutcome.Halted(it) } ?: FollowingOutcome.Failed(error, until)
    }

    /** Following melebihi batas akun yang didukung: berhenti sampai user bertindak, daftar parsial dibuang. */
    private suspend fun haltTooLarge(owner: String): FollowingOutcome {
        store.clearProgress()
        syncDao.get(owner)?.let { syncDao.upsert(it.copy(haltReason = ErrorPolicy.HALT_ACCOUNT_TOO_LARGE)) }
        return FollowingOutcome.Halted(ErrorPolicy.HALT_ACCOUNT_TOO_LARGE)
    }

    private companion object {
        const val TAG = "NofollowApi"
        const val SAFETY_MIN_PREVIOUS = 50
        /** Pengaman: 400 halaman x 25 = 10.000 akun following. Lebih dari itu dianggap loop. */
        const val MAX_TOTAL_PAGES = 400
    }
}