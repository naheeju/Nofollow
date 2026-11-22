package com.nofollow.app.features.kepo.data.repository

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.core.common.localDayNumber
import com.nofollow.app.core.common.nextLocalDayStart
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.kepo.data.local.KepoDao
import com.nofollow.app.features.kepo.data.local.KepoHitEntity
import com.nofollow.app.features.kepo.data.local.KepoPersonEntity
import com.nofollow.app.features.kepo.data.local.KepoViewEntity
import com.nofollow.app.features.kepo.data.remote.KepoParser
import com.nofollow.app.features.kepo.data.settings.KepoStateStore
import com.nofollow.app.features.kepo.domain.model.KepoConfig
import com.nofollow.app.features.kepo.domain.model.KepoData
import com.nofollow.app.features.kepo.domain.model.KepoEntry
import com.nofollow.app.features.kepo.domain.model.KepoOutcome
import com.nofollow.app.features.kepo.domain.repository.KepoRepository
import com.nofollow.app.features.tracker.data.avatar.AvatarStore
import com.nofollow.app.features.tracker.data.local.DailyUsageDao
import com.nofollow.app.features.tracker.data.local.DailyUsageEntity
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.local.TrackerDatabase
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.settings.FollowingStore
import com.nofollow.app.features.tracker.data.sync.ErrorPolicy
import com.nofollow.app.features.tracker.data.sync.SyncGate
import com.nofollow.app.features.tracker.data.sync.SyncPlanner
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Kepo = akun yang like (atau membalas) story kita. Dari instagram.com sendiri, bukan "pengunjung profil" sungguhan
 * (Instagram tidak memberi data itu).
 *
 * Alur satu jalan:
 * 1. reels_tray (berat, ~165 KB) hanya kalau sudah waktunya atau manual: cari story aktif + waktu kedaluwarsanya.
 * 2. list_reel_media_viewer per story aktif, halaman demi halaman dengan jeda acak. Semua penonton dicatat per story
 *    (dasar "Nonton N dari M story"), yang like / balasan dihitung sebagai interaksi, dan penonton teratas dari story
 *    terbaru (urutan Instagram) ditandai untuk kartu Beranda (close friend dulu, lalu urutan Instagram; penonton spam dibuang). Tanpa request tambahan: halaman yang sama sudah diambil.
 *
 * Data Kepo selalu cermin story yang MASIH AKTIF: begitu sebuah story hilang (dihapus, tidak ada di tray, 404, atau lewat
 * 24 jam), catatan nonton dan like/balasannya ikut dibuang ([purgeGone]), hitungan interaksi dihitung ulang, dan akun yang
 * tidak punya jejak lagi dihapus. Tidak ada riwayat lintas story yang menumpuk.
 *
 * Aturan keamanan sama dengan sweep: satu kunci [SyncGate] (tidak pernah berbarengan dengan sweep follower/following),
 * cooldown dan halt di sync_state dibagi bersama, jam tenang untuk pemicu otomatis, plafon request harian sendiri
 * (kunci "{owner}#kepo" di daily_usage), dan baru jalan setelah baseline follower selesai.
 */
class KepoRepositoryImpl(
    private val api: InstagramApi,
    private val db: TrackerDatabase,
    private val dao: KepoDao,
    private val syncDao: SyncStateDao,
    private val usageDao: DailyUsageDao,
    private val store: KepoStateStore,
    private val sessionStore: SessionStore,
    private val syncConfig: SyncConfig,
    private val config: KepoConfig,
    private val gate: SyncGate,
    private val avatars: AvatarStore,
    private val followingStore: FollowingStore,
) : KepoRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<KepoData> =
        sessionStore.session.map { it?.userId }.distinctUntilChanged().flatMapLatest { owner ->
            if (owner == null) {
                flowOf(KepoData(emptyList(), 0))
            } else {
                combine(dao.observeRows(owner), dao.observeStoryCount(owner), followingStore.snapshot) { rows, stories, following ->
                    // null = daftar following belum pernah terbaca penuh (atau milik akun lain): status "kita follow" tidak diketahui.
                    val followingPks = following?.takeIf { it.ownerId == owner }?.users?.mapTo(HashSet()) { it.pk }
                    KepoData(
                        entries = rows.map { r ->
                            val p = r.person
                            KepoEntry(
                                pk = p.pk,
                                username = p.username,
                                fullName = p.fullName,
                                avatarPath = avatars.pathOrNull(p.pk),
                                followedBy = p.followedBy,
                                iFollow = followingPks?.contains(p.pk),
                                likeCount = p.likeCount,
                                replyCount = p.replyCount,
                                watchCount = r.watchCount,
                                topRank = p.topRank,
                                lastAt = p.lastAt,
                                isNew = !p.seen,
                            )
                        },
                        storyCount = stories,
                    )
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun markSeen() {
        sessionStore.session.value?.userId?.let { dao.markAllSeen(it) }
    }

    override suspend fun sync(manual: Boolean, full: Boolean): KepoOutcome = gate.mutex.withLock {
        val owner = sessionStore.session.value?.userId ?: return KepoOutcome.Halted("not_logged_in")
        val now = System.currentTimeMillis()

        val state = syncDao.get(owner)
        if (state == null || !state.baselineDone) return KepoOutcome.Skipped(SkipReason.NOT_DUE)
        state.haltReason?.let { return KepoOutcome.Halted(it) }
        if (now < state.cooldownUntil) return KepoOutcome.Skipped(SkipReason.COOLING_DOWN, state.cooldownUntil)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!manual && SyncPlanner.inQuietWindow(hour, syncConfig.quietStartHour, syncConfig.quietEndHour)) {
            return KepoOutcome.Skipped(SkipReason.QUIET_HOURS)
        }

        val snap = store.load(owner)
        if (manual && snap != null) {
            val retryAt = maxOf(snap.pollAt, snap.trayAt) + config.manualMinGapMs
            if (now < retryAt) return KepoOutcome.Skipped(SkipReason.TOO_SOON, retryAt)
        }

        usageDao.pruneBefore(usageKey(owner, manual = false), localDayNumber(now) - 7)
        usageDao.pruneBefore(usageKey(owner, manual = true), localDayNumber(now) - 7)

        // ---- 1. story aktif
        var stories = snap?.stories.orEmpty()
        var trayAt = snap?.trayAt ?: 0L
        val lastPoll = snap?.pollAt ?: 0L
        val needTray = full || snap == null || now - snap.trayAt >= config.trayAutoGapMs
        var sentRequest = false
        if (needTray) {
            if (!spend(owner, manual)) return KepoOutcome.Skipped(SkipReason.DAILY_CAP, nextLocalDayStart(now))
            sentRequest = true
            val body = when (val result = api.getChecked(TRAY_URL, logBody = false)) {
                is AppResult.Failure -> return fail(owner, result.error)
                is AppResult.Success -> result.data
            }
            stories = KepoParser.parseStories(body, owner) ?: run {
                // Bentuk respons berubah bukan salah akun: jangan memicu cooldown bersama (yang ikut menahan sweep).
                // Cukup tunda Kepo sendiri; tray dicoba lagi setelah jeda otomatis.
                Log.w(TAG, "format reels_tray tidak dikenali")
                store.save(KepoStateStore.Snapshot(owner, now, lastPoll, snap?.stories.orEmpty()))
                return KepoOutcome.Failed(AppError.Unknown("format reels_tray tidak dikenali"), now + config.trayAutoGapMs)
            }
            trayAt = now
            Log.d(TAG, "tray: ${stories.size} story aktif")
        }

        val active = stories.filter { it.expiringAt * 1000 > now }
            .sortedByDescending { it.takenAt }
            .take(config.maxStoriesPerRun)
        val nearExpiry = active.any { it.expiringAt * 1000 - now <= config.nearExpiryMs }
        val needViewers = active.isNotEmpty() && (
                full ||
                        now - lastPoll >= config.autoPollGapMs ||
                        (nearExpiry && now - lastPoll >= config.nearExpiryPollGapMs)
                )
        if (!needTray && !needViewers) return KepoOutcome.Skipped(SkipReason.NOT_DUE)
        if (!needViewers) {
            store.save(KepoStateStore.Snapshot(owner, trayAt, lastPoll, active))
            // Tray baru saja dibaca: daftar story aktif sudah pasti, jadi data story yang hilang langsung dibersihkan.
            purgeGone(owner, active.mapTo(HashSet()) { it.pk })
            return KepoOutcome.Completed(0, active.size)
        }

        // ---- 2. penonton per story
        val dropped = mutableSetOf<String>()
        val budget = Budget(config.avatarBudget)
        var added = 0
        for ((storyIndex, story) in active.withIndex()) {
            var cursor: String? = null
            var pages = 0
            while (true) {
                if (sentRequest) delay(Random.nextLong(config.pageDelayMinMs, config.pageDelayMaxMs + 1).milliseconds)
                if (!spend(owner, manual)) {
                    store.save(KepoStateStore.Snapshot(owner, trayAt, lastPoll, stories.liveAt(now, dropped)))
                    return KepoOutcome.Skipped(SkipReason.DAILY_CAP, nextLocalDayStart(System.currentTimeMillis()))
                }
                sentRequest = true

                val body = when (val result = api.getChecked(viewersUrl(story.pk, cursor), logBody = false)) {
                    is AppResult.Failure -> {
                        // Story sudah dihapus / tidak ada lagi: lupakan story ini, jangan kena cooldown.
                        if (result.error.isNotFound()) {
                            dropped += story.pk
                            null
                        } else {
                            return fail(owner, result.error)
                        }
                    }
                    is AppResult.Success -> result.data
                } ?: break

                val page = KepoParser.parseViewers(body) ?: run {
                    Log.w(TAG, "format penonton story tidak dikenali")
                    store.save(KepoStateStore.Snapshot(owner, trayAt, now, stories.liveAt(now, dropped)))
                    return KepoOutcome.Failed(AppError.Unknown("format penonton story tidak dikenali"), now + config.autoPollGapMs)
                }
                val hot = page.viewers.count { it.hasLiked || it.replied }
                Log.d(
                    TAG,
                    "story …${story.pk.takeLast(4)} halaman ${pages + 1}: ${page.viewers.size} penonton, " +
                            "$hot berinteraksi, total=${page.total}, lanjut=${page.nextMaxId != null}",
                )
                // Penonton teratas diambil dari halaman pertama story terbaru saja (active sudah urut terbaru).
                added += apply(owner, story.pk, page.viewers, budget, rankTop = storyIndex == 0 && pages == 0)
                pages++

                val next = page.nextMaxId ?: break
                // Cursor tidak maju = loop; berhenti daripada menghajar server.
                if (next == cursor || pages >= config.maxPagesPerStory) break
                cursor = next
            }
        }

        store.save(KepoStateStore.Snapshot(owner, trayAt, now, stories.liveAt(now, dropped)))
        try {
            purgeGone(owner, active.filterNot { it.pk in dropped }.mapTo(HashSet()) { it.pk })
            avatars.prune(dao.allPks(owner).toSet())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "pangkas data kepo gagal: ${e.message}")
        }
        Log.d(TAG, "kepo selesai: $added interaksi baru, ${active.size - dropped.size} story dicek")
        KepoOutcome.Completed(added, active.size - dropped.size)
    }

    /**
     * Cermin story aktif: buang semua catatan nonton dan like/balasan dari story yang tidak ada di [keep], hitung ulang
     * like/balasan tiap akun dari catatan yang tersisa, lalu hapus akun yang tidak punya catatan nonton lagi.
     * Satu transaksi, jadi UI tidak pernah melihat keadaan setengah jadi. [keep] kosong = semua data Kepo dikosongkan.
     */
    private suspend fun purgeGone(owner: String, keep: Set<String>) {
        val alive = keep.toList()
        db.withTransaction {
            dao.deleteViewsExcept(owner, alive)
            dao.deleteHitsExcept(owner, alive)
            dao.recountInteractions(owner)
            dao.deleteWithoutViews(owner)
        }
    }

    /**
     * Simpan satu halaman penonton. Semua penonton dicatat (orang + catatan nonton per story); like / balasan dihitung
     * sebagai interaksi (penonton spam dibuang). [rankTop] = halaman ini menentukan pengisi slot untuk kartu Beranda.
     * Return: jumlah like/balasan yang BARU dihitung.
     */
    private suspend fun apply(owner: String, storyPk: String, rawViewers: List<KepoParser.Viewer>, budget: Budget, rankTop: Boolean): Int {
        // Penonton yang ditandai spam oleh Instagram dibuang: tidak dicatat dan tidak mengisi slot.
        val viewers = rawViewers.filterNot { it.isSpam }
        if (viewers.isEmpty()) return 0
        // Pengisi slot = penonton tanpa interaksi (yang berinteraksi sudah punya tempat sendiri). Close friend naik ke atas;
        // sisanya tetap urutan Instagram (sortedByDescending stabil). Ini tebakan "paling kepo", bukan data kunjungan profil.
        val top = if (rankTop) {
            viewers.filter { !it.hasLiked && !it.replied }.sortedByDescending { it.isBestie }.take(config.topViewerCount)
        } else {
            emptyList()
        }
        // Foto hanya untuk yang berinteraksi dan penonton teratas; penonton biasa cukup huruf awal.
        // Diunduh sebelum baris disimpan, supaya daftar langsung tampil lengkap dengan fotonya.
        val withPhoto = (viewers.filter { it.hasLiked || it.replied } + top).distinctBy { it.pk }
        for (v in withPhoto) {
            val url = v.picUrl ?: continue
            if (budget.left > 0 && avatars.pathOrNull(v.pk) == null) {
                budget.left--
                avatars.download(v.pk, url)
            }
        }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            var added = 0
            if (rankTop) dao.clearTopRank(owner)
            for (v in viewers) {
                val hot = v.hasLiked || v.replied
                dao.insertPerson(
                    // Penonton biasa langsung dianggap sudah dilihat: label "Baru" hanya untuk interaksi.
                    KepoPersonEntity(owner, v.pk, v.username, v.fullName, v.picUrl, v.followedBy, now, now, 0, 0, seen = !hot),
                )
                dao.updateProfile(owner, v.pk, v.username, v.fullName, v.picUrl, v.followedBy)
                dao.insertView(KepoViewEntity(owner, storyPk, v.pk, now))
                if (v.hasLiked && dao.insertHit(KepoHitEntity(owner, storyPk, v.pk, KIND_LIKE)) != -1L) {
                    dao.addLike(owner, v.pk, now)
                    added++
                }
                if (v.replied && dao.insertHit(KepoHitEntity(owner, storyPk, v.pk, KIND_REPLY)) != -1L) {
                    dao.addReply(owner, v.pk, now)
                    added++
                }
            }
            top.forEachIndexed { i, v -> dao.setTopRank(owner, v.pk, i) }
            added
        }
    }

    private fun viewersUrl(storyPk: String, cursor: String?): String {
        val base = "https://www.instagram.com/api/v1/media/$storyPk/list_reel_media_viewer/"
        return if (cursor == null) base else "$base?max_id=${Uri.encode(cursor)}"
    }

    /** Dua kantong: otomatis (latar belakang) dan manual (tombol Cek), supaya yang satu tidak menghabiskan jatah yang lain. */
    private fun usageKey(owner: String, manual: Boolean) = if (manual) "$owner#kepo-manual" else "$owner#kepo"

    /** Hitung satu request ke plafon harian Kepo (kantong sesuai pemicu). False = plafon tercapai, jangan kirim request. */
    private suspend fun spend(owner: String, manual: Boolean): Boolean {
        val day = localDayNumber(System.currentTimeMillis())
        val key = usageKey(owner, manual)
        val cap = if (manual) config.manualDailyCap else config.autoDailyCap
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

    /** Kebijakan error yang sama dengan sweep: cooldown / halt di sync_state, dibaca ulang supaya tidak menimpa data basi. */
    private suspend fun fail(owner: String, error: AppError): KepoOutcome {
        val s = syncDao.get(owner) ?: return KepoOutcome.Failed(error, 0L)
        Log.w(TAG, "kepo gagal: $error (level ${s.cooldownLevel})")
        val a = ErrorPolicy.onError(error, s.cooldownLevel, syncConfig)
        val now = System.currentTimeMillis()
        val until = now + a.cooldownMs
        val throttled = error == AppError.RateLimited || error == AppError.ActionBlocked
        syncDao.upsert(
            s.copy(
                cooldownUntil = until,
                cooldownLevel = a.nextLevel,
                haltReason = a.haltReason,
                throttleCount = s.throttleCount + if (throttled) 1 else 0,
                penaltyUntil = if (throttled) now + syncConfig.penaltyDurationMs else s.penaltyUntil,
            ),
        )
        return a.haltReason?.let { KepoOutcome.Halted(it) } ?: KepoOutcome.Failed(error, until)
    }

    private fun AppError.isNotFound(): Boolean = this is AppError.Unknown && toString().contains("404")

    private fun List<KepoParser.Story>.liveAt(now: Long, dropped: Set<String>) =
        filter { it.expiringAt * 1000 > now && it.pk !in dropped }

    private class Budget(var left: Int)

    private companion object {
        const val TAG = "NofollowKepo"
        const val TRAY_URL = "https://www.instagram.com/api/v1/feed/reels_tray/"
        const val KIND_LIKE = "L"
        const val KIND_REPLY = "R"
    }
}