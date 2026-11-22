package com.nofollow.app.features.tracker.data.repository

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.core.common.localDayNumber
import com.nofollow.app.core.common.nextLocalDayStart
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.avatar.AvatarSource
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.DailyUsageDao
import com.nofollow.app.features.tracker.data.local.DailyUsageEntity
import com.nofollow.app.features.tracker.data.local.FollowEventDao
import com.nofollow.app.features.tracker.data.local.FollowEventEntity
import com.nofollow.app.features.tracker.data.local.FollowEventType
import com.nofollow.app.features.tracker.data.local.FollowerDao
import com.nofollow.app.features.tracker.data.local.FollowerHistoryDao
import com.nofollow.app.features.tracker.data.local.FollowerHistoryEntity
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.local.SyncStateEntity
import com.nofollow.app.features.tracker.data.local.TrackerDatabase
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.settings.SyncSettings
import com.nofollow.app.features.tracker.data.sync.ErrorPolicy
import com.nofollow.app.features.tracker.data.sync.FollowerDiff
import com.nofollow.app.features.tracker.data.sync.FollowerPageParser
import com.nofollow.app.features.tracker.domain.model.Pacing
import com.nofollow.app.features.tracker.domain.model.RequestBucket
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.data.sync.SyncDecision
import com.nofollow.app.features.tracker.data.sync.SyncGate
import com.nofollow.app.features.tracker.data.sync.SweepPacer
import com.nofollow.app.features.tracker.data.sync.SyncPlanner
import com.nofollow.app.features.tracker.domain.model.ProbeResult
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.features.tracker.domain.model.SyncOutcome
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import com.nofollow.app.features.tracker.domain.repository.FollowerSyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class FollowerSyncRepositoryImpl(
    private val api: InstagramApi,
    private val db: TrackerDatabase,
    private val followerDao: FollowerDao,
    private val eventDao: FollowEventDao,
    private val syncDao: SyncStateDao,
    private val usageDao: DailyUsageDao,
    private val historyDao: FollowerHistoryDao,
    private val settings: SyncSettings,
    private val sessionStore: SessionStore,
    private val config: SyncConfig,
    private val avatars: AvatarStore,
    private val followingStore: FollowingStore,
    gate: SyncGate,
) : FollowerSyncRepository {

    /** Worker, probe, tombol manual, dan sweep following tidak boleh jalan bersamaan (kunci dibagi lewat [SyncGate]). */
    private val mutex = gate.mutex

    /** Waktu jalan baseline terakhir (memori saja), untuk jeda minimal antar jalan baseline. */
    @Volatile
    private var lastBaselineRunAt = 0L

    /**
     * Jumlah sinkron manual yang sedang menunggu mutex. Sweep latar belakang (probe/worker) yang sedang
     * jalan berhenti di titik aman berikutnya (state per halaman sudah tersimpan) dan manual
     * melanjutkannya dari cursor dengan kecepatan penuh, jadi tombol manual tidak pernah menunggu lama.
     */
    private val manualWaiting = AtomicInteger(0)

    /** Sisa jatah unduh foto untuk sweep yang sedang jalan. Diisi ulang tiap sweep baru. Dijaga [mutex]. */
    private var avatarBudget = AVATAR_PER_SWEEP

    override suspend fun sync(trigger: SyncTrigger): SyncOutcome {
        var waiting = trigger == SyncTrigger.MANUAL
        if (waiting) manualWaiting.incrementAndGet()
        try {
            return mutex.withLock {
                if (waiting) {
                    manualWaiting.decrementAndGet()
                    waiting = false
                }
                val owner = sessionStore.session.value?.userId ?: return SyncOutcome.Halted("not_logged_in")
                val now = System.currentTimeMillis()
                val state = syncDao.get(owner)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                val running = when (val d = SyncPlanner.decide(state, now, hour, trigger, config)) {
                    is SyncDecision.Halt -> return SyncOutcome.Halted(d.reason)
                    is SyncDecision.CoolingDown -> return SyncOutcome.Skipped(SkipReason.COOLING_DOWN, d.until)
                    is SyncDecision.TooSoon -> return SyncOutcome.Skipped(SkipReason.TOO_SOON, d.until)
                    SyncDecision.QuietHours -> return SyncOutcome.Skipped(SkipReason.QUIET_HOURS)
                    SyncDecision.Idle -> return SyncOutcome.Skipped(SkipReason.NOT_DUE)
                    SyncDecision.Resume -> checkNotNull(state)
                    is SyncDecision.Start -> startSweep(owner, state, now)
                }
                // Baseline dicicil: jalan berikutnya menunggu jeda minimal, apa pun pemicunya.
                if (!running.baselineDone) {
                    val next = lastBaselineRunAt + config.baselineRunMinGapMs
                    if (lastBaselineRunAt != 0L && now < next) return SyncOutcome.Skipped(SkipReason.TOO_SOON, next)
                    lastBaselineRunAt = now
                }
                runSweep(
                    owner = owner,
                    start = running,
                    pacing = config.pacingFor(trigger, running.baselineDone),
                    bucket = config.bucketFor(trigger, running.baselineDone),
                    yieldable = trigger != SyncTrigger.MANUAL,
                    trigger = trigger,
                )
            }
        } finally {
            // Dibatalkan saat masih antre: jangan tinggalkan hitungan menggantung.
            if (waiting) manualWaiting.decrementAndGet()
        }
    }

    override suspend fun clearHalt() {
        val owner = sessionStore.session.value?.userId ?: return
        val s = syncDao.get(owner) ?: return
        syncDao.upsert(s.copy(haltReason = null, cooldownUntil = 0, cooldownLevel = 0))
    }

    // ----------------------------------------------------------------- pantau cepat

    override suspend fun probe(): ProbeResult {
        val owner = sessionStore.session.value?.userId ?: return ProbeResult.Halted("not_logged_in")
        val now = System.currentTimeMillis()
        val state = syncDao.get(owner)
        if (state == null || !state.baselineDone) return ProbeResult.Skipped("no_baseline")
        state.haltReason?.let { return ProbeResult.Halted(it) }
        if (now < state.cooldownUntil) return ProbeResult.Skipped("cooling_down", state.cooldownUntil)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (SyncPlanner.inQuietWindow(hour, config.probeQuietStartHour, config.probeQuietEndHour)) {
            return ProbeResult.Skipped("quiet_hours")
        }
        return if (state.sweepInProgress) ProbeResult.SweepPending else ProbeResult.SweepDue
    }

    override suspend fun nextPulseDelayMs(): Long {
        val owner = sessionStore.session.value?.userId
        val active = owner?.let { followerDao.countActive(it) } ?: 0
        val penalty = owner?.let { syncDao.get(it)?.penaltyUntil } ?: 0L
        // Request per sweep: satu per halaman (user nyata per halaman rata-rata ~16, bukan 25) + 1 halaman penutup.
        val perPage = config.effectiveUsersPerPage
        val cost = (active + perPage - 1) / perPage + 1
        // Jarak terpendek yang masih muat di plafon latar belakang selama jam aktif (+15% cadangan konfirmasi).
        val mode = settings.mode.first()
        val cap = config.backgroundCap(mode, active)
        val budgetMs = (config.probeActiveHoursPerDay * SyncConfig.HOUR * cost.toDouble() / cap * 1.15).toLong()
        // Jarak dasar mengikuti mode yang dipilih user (Hemat 12 jam ... Kilat 2 jam), bukan angka tetap 6 jam.
        var base = maxOf(mode.intervalMs, budgetMs).coerceAtMost(config.probeMaxIntervalMs)
        if (System.currentTimeMillis() < penalty) base *= config.penaltyIntervalMultiplier
        val jitter = 1.0 + Random.nextDouble(-config.probeJitter, config.probeJitter)
        return (base * jitter).toLong()
    }

    // ---------------------------------------------------------------- sweep

    private suspend fun startSweep(owner: String, old: SyncStateEntity?, now: Long): SyncStateEntity {
        avatarBudget = AVATAR_PER_SWEEP
        val base = old ?: SyncStateEntity(ownerId = owner)
        val s = base.copy(
            sweepId = base.sweepId + 1,
            sweepInProgress = true,
            sweepCursor = null,
            sweepPagesDone = 0,
            sweepListDone = false,
            sweepStartedAt = now,
        )
        syncDao.upsert(s)
        val keepFrom = localDayNumber(now) - USAGE_KEEP_DAYS
        RequestBucket.entries.forEach { usageDao.pruneBefore(config.usageKey(owner, it), keepFrom) }
        return s
    }

    private suspend fun runSweep(
        owner: String,
        start: SyncStateEntity,
        pacing: Pacing,
        bucket: RequestBucket,
        yieldable: Boolean,
        trigger: SyncTrigger,
    ): SyncOutcome {
        var s = start
        var pagesThisRun = 0
        val pacer = SweepPacer(pacing)
        // Ada orang yang menunggu (tombol manual / sinkron pertama): error jaringan tidak boleh mengunci app sendiri.
        val userWaiting = trigger == SyncTrigger.MANUAL || !start.baselineDone

        while (!s.sweepListDone) {
            if (pagesThisRun >= pacing.maxPagesPerRun) {
                return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = 0)
            }
            // Sinkron manual menunggu: berhenti di sini, manual melanjutkan dari cursor yang sudah tersimpan.
            if (shouldYield(yieldable)) return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = 0)
            if (pagesThisRun > 0 && delayYielding(pacer.nextDelayMs(pagesThisRun), yieldable)) {
                return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = 0)
            }
            if (followerDao.countActive(owner) > config.maxAccountSize) {
                syncDao.upsert(s.copy(haltReason = ErrorPolicy.HALT_ACCOUNT_TOO_LARGE))
                return SyncOutcome.Halted(ErrorPolicy.HALT_ACCOUNT_TOO_LARGE)
            }
            if (!spendRequest(owner, bucket)) return capReached(bucket)

            val t0 = SystemClock.elapsedRealtime()
            val result = api.getChecked(followersUrl(owner, s.sweepCursor, pacing.pageSize))
            pacer.onLatency(SystemClock.elapsedRealtime() - t0)
            val body = when (result) {
                is AppResult.Failure -> return fail(s, result.error, userWaiting)
                is AppResult.Success -> result.data
            }
            val page = FollowerPageParser.parseFollowers(body)
                ?: return fail(s, AppError.Unknown("format daftar follower tidak dikenali"))
            Log.d("NofollowApi", "halaman ${s.sweepPagesDone + 1}: ${page.users.size} user (minta ${pacing.pageSize}), next=${page.nextMaxId != null}")
            // Cursor yang tidak maju = loop tak berujung; berhenti daripada menghajar server.
            if (page.nextMaxId != null && page.nextMaxId == s.sweepCursor) {
                return fail(s, AppError.Unknown("cursor tidak maju"))
            }
            s = applyPage(owner, s, page)
            pagesThisRun++
        }
        return confirmPhase(owner, s, pacing, bucket, yieldable, userWaiting)
    }

    private suspend fun applyPage(
        owner: String,
        s: SyncStateEntity,
        page: FollowerPageParser.Page,
    ): SyncStateEntity {
        val now = System.currentTimeMillis()
        val next = s.copy(
            sweepCursor = page.nextMaxId,
            sweepPagesDone = s.sweepPagesDone + 1,
            sweepListDone = page.nextMaxId == null,
            cooldownLevel = 0,
        )
        // Baca + diff + unduh foto dulu, baru tulis. Dengan begitu UI yang mengamati event langsung
        // melihat foto yang sudah ada (tidak ada emisi "tanpa foto"). Aman: hanya sinkron ini yang menulis follower.
        val existing = followerDao.findByPks(owner, page.users.map { it.pk }).associateBy { it.pk }
        val diff = FollowerDiff.diffPage(owner, page.users, existing, s.sweepId, s.baselineDone, now)
        val picByPk = diff.upserts.associate { it.pk to it.picUrl }
        // URL dari halaman ini paling segar: unduh untuk FOLLOW/REFOLLOW/RENAME.
        diff.events.forEach { fetchAvatar(it.pk, picByPk[it.pk]) }
        // Sisanya: semua follower di halaman ini yang fotonya belum ada (sync pertama) atau sudah lama. Yang baru
        // diunduh di atas dilewati karena masih segar. Gagal tidak apa-apa, dicoba lagi di sweep berikutnya.
        ensureAvatars(page.users.mapNotNull { u -> u.picUrl?.let { AvatarSource(u.pk, it) } })
        db.withTransaction {
            followerDao.upsertAll(diff.upserts)
            if (diff.events.isNotEmpty()) eventDao.insertAll(diff.events)
            syncDao.upsert(next)
        }
        return next
    }

    // ------------------------------------------------------------- konfirmasi

    private suspend fun confirmPhase(
        owner: String,
        s: SyncStateEntity,
        pacing: Pacing,
        bucket: RequestBucket,
        yieldable: Boolean,
        userWaiting: Boolean,
    ): SyncOutcome {
        // Sweep pertama = baseline: tidak ada pembanding, jadi tidak ada unfollow untuk dikonfirmasi.
        if (!s.baselineDone) return finish(s, baseline = true, emptyList(), emptyList())

        val missing = followerDao.findMissing(owner, s.sweepId)
        val active = followerDao.countActive(owner)
        val limit = maxOf(config.safetyMinMissing, (active * config.safetyMissingFraction).toInt())
        if (missing.size > limit) {
            // Hampir pasti daftar terbaca tidak lengkap, bukan massal unfollow. Jangan ubah apa pun.
            syncDao.upsert(endSweep(s, System.currentTimeMillis()))
            return SyncOutcome.Aborted(missing.size, active)
        }

        val unfollowed = ArrayList<String>()
        val unavailable = ArrayList<String>()
        for ((checked, c) in missing.withIndex()) {
            if (checked >= pacing.maxConfirmPerRun) {
                return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = missing.size - checked)
            }
            if (shouldYield(yieldable)) return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = missing.size - checked)
            if (checked > 0 &&
                delayYielding(Random.nextLong(pacing.confirmDelayMinMs, pacing.confirmDelayMaxMs + 1), yieldable)
            ) {
                return SyncOutcome.Progress(s.sweepPagesDone, confirmLeft = missing.size - checked)
            }
            if (!spendRequest(owner, bucket)) return capReached(bucket)

            val url = "https://www.instagram.com/api/v1/friendships/show/${c.pk}/"
            val body = when (val r = api.getAllowingNotFound(url)) {
                is AppResult.Failure -> return fail(s, r.error, userWaiting)
                is AppResult.Success -> r.data
            }
            val now = System.currentTimeMillis()
            if (body == null) {
                Log.d("NofollowApi", "konfirmasi ${c.username} (${c.pk}): HTTP 404 -> UNAVAILABLE")
                fetchAvatar(c.pk, c.picUrl)
                recordGone(owner, c.pk, c.username, FollowEventType.UNAVAILABLE, now, c.lastSeenAt)
                unavailable += c.username
            } else {
                when (FollowerPageParser.parseFollowedBy(body)) {
                    true -> {
                        Log.d("NofollowApi", "konfirmasi ${c.username} (${c.pk}): followed_by=true -> masih follower")
                        followerDao.markSeen(owner, c.pk, s.sweepId, now)
                    }
                    false -> {
                        Log.d("NofollowApi", "konfirmasi ${c.username} (${c.pk}): 200 followed_by=false -> UNFOLLOW")
                        fetchAvatar(c.pk, c.picUrl)
                        recordGone(owner, c.pk, c.username, FollowEventType.UNFOLLOW, now, c.lastSeenAt)
                        unfollowed += c.username
                    }
                    null -> {
                        Log.w("NofollowApi", "konfirmasi ${c.username} (${c.pk}): followed_by tidak ada, body=${body.take(200)}")
                        return fail(s, AppError.Unknown("respons friendships/show tidak dikenali"))
                    }
                }
            }
        }
        return finish(s, baseline = false, unfollowed, unavailable)
    }

    private suspend fun recordGone(
        owner: String,
        pk: String,
        username: String,
        type: FollowEventType,
        now: Long,
        lastSeenAt: Long,
    ) {
        db.withTransaction {
            followerDao.markGone(owner, pk, now)
            eventDao.insertAll(
                listOf(
                    FollowEventEntity(
                        ownerId = owner,
                        pk = pk,
                        type = type,
                        username = username,
                        occurredAt = now,
                        lastSeenAt = lastSeenAt,
                    ),
                ),
            )
        }
    }

    private suspend fun finish(
        s: SyncStateEntity,
        baseline: Boolean,
        unfollowed: List<String>,
        unavailable: List<String>,
    ): SyncOutcome {
        syncDao.upsert(endSweep(s, System.currentTimeMillis()))
        pruneAvatars(s.ownerId)
        recordHistory(s.ownerId)
        return SyncOutcome.Completed(baseline, unfollowed, unavailable)
    }

    /** Menutup sweep: catat selesai, hitung rentang waktu, dan jadwalkan sweep berikutnya. */
    private suspend fun endSweep(s: SyncStateEntity, now: Long): SyncStateEntity {
        val mode = settings.mode.first()
        return s.copy(
            sweepInProgress = false,
            sweepListDone = false,
            sweepCursor = null,
            lastCompletedSweepAt = now,
            lastSweepDurationMs = s.sweepStartedAt?.let { now - it },
            nextSweepAt = SyncPlanner.nextSweepAt(now, mode, now < s.penaltyUntil, config),
            baselineDone = true,
            cooldownLevel = 0,
        )
    }

    // ------------------------------------------------------------- riwayat

    /** Catat jumlah follower aktif hari ini (menimpa baris hari yang sama) dan buang yang lebih dari 90 hari. */
    private suspend fun recordHistory(owner: String) {
        try {
            val day = localDayNumber(System.currentTimeMillis())
            historyDao.upsert(FollowerHistoryEntity(owner, day, followerDao.countActive(owner)))
            historyDao.pruneBefore(owner, day - HISTORY_KEEP_DAYS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NofollowApi", "catat riwayat follower gagal: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- foto

    /**
     * Unduh foto dari CDN (bukan API Instagram, tanpa cookie) selama jatah sweep masih ada.
     * Gagal atau jatah habis tidak apa-apa: UI jatuh ke huruf awal username dan sinkron tidak terganggu.
     */
    private suspend fun fetchAvatar(pk: String, url: String?) {
        if (url == null || avatarBudget <= 0) return
        avatarBudget--
        try {
            avatars.download(pk, url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NofollowAvatar", "unduh foto $pk gagal: ${e.message}")
        }
    }

    private suspend fun ensureAvatars(sources: List<AvatarSource>) {
        try {
            avatars.ensure(sources)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NofollowAvatar", "unduh foto per halaman gagal: ${e.message}")
        }
    }

    /**
     * Hapus foto yang tidak lagi dipakai. Yang dipertahankan: follower aktif (tab Fans dan Mutual), akun di daftar
     * following (tab Tidak follow balik, termasuk sweep following yang sedang dicicil), dan [AVATAR_KEEP_EVENTS]
     * kejadian terbaru (unfollow dst.).
     */
    private suspend fun pruneAvatars(owner: String) {
        try {
            val keep = HashSet<String>()
            keep += eventDao.recentPks(owner, AVATAR_KEEP_EVENTS)
            keep += followerDao.activePks(owner)
            followingStore.current()?.takeIf { it.ownerId == owner }?.users?.forEach { keep += it.pk }
            followingStore.progress()?.takeIf { it.ownerId == owner }?.users?.forEach { keep += it.pk }
            avatars.prune(keep)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NofollowAvatar", "pangkas foto gagal: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- helper

    private suspend fun fail(s: SyncStateEntity, error: AppError, userWaiting: Boolean = false): SyncOutcome {
        Log.w("NofollowApi", "sync gagal: $error (halaman ${s.sweepPagesDone}, level ${s.cooldownLevel})")
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
        return a.haltReason?.let { SyncOutcome.Halted(it) } ?: SyncOutcome.Failed(error, until)
    }

    /** Menghitung satu request ke plafon harian kantongnya. False = plafon tercapai, jangan kirim request. */
    private suspend fun spendRequest(owner: String, bucket: RequestBucket): Boolean {
        val day = localDayNumber(System.currentTimeMillis())
        val key = config.usageKey(owner, bucket)
        val cap = if (bucket == RequestBucket.BACKGROUND) {
            config.backgroundCap(settings.mode.first(), followerDao.countActive(owner))
        } else {
            config.capFor(bucket)
        }
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

    private fun capReached(bucket: RequestBucket): SyncOutcome {
        Log.w("NofollowApi", "plafon request harian tercapai ($bucket: ${config.capFor(bucket)})")
        return SyncOutcome.Skipped(SkipReason.DAILY_CAP, nextLocalDayStart(System.currentTimeMillis()))
    }

    /** True kalau ada sinkron manual yang menunggu dan jalan ini boleh mengalah (bukan manual itu sendiri). */
    private fun shouldYield(yieldable: Boolean): Boolean = yieldable && manualWaiting.get() > 0

    /** Delay yang dipotong per 250 ms supaya bisa mengalah cepat. True = dihentikan karena manual menunggu. */
    private suspend fun delayYielding(ms: Long, yieldable: Boolean): Boolean {
        var left = ms
        while (left > 0) {
            if (shouldYield(yieldable)) return true
            val step = minOf(left, YIELD_CHECK_MS)
            delay(step.milliseconds)
            left -= step
        }
        return false
    }

    private fun followersUrl(owner: String, cursor: String?, pageSize: Int): String {
        val base = "https://www.instagram.com/api/v1/friendships/$owner/followers/?count=$pageSize"
        return if (cursor == null) base else "$base&max_id=${Uri.encode(cursor)}"
    }

    private companion object {
        const val USAGE_KEEP_DAYS = 7L
        const val YIELD_CHECK_MS = 250L
        const val AVATAR_PER_SWEEP = 20
        const val AVATAR_KEEP_EVENTS = 500
        const val HISTORY_KEEP_DAYS = 90L
    }
}