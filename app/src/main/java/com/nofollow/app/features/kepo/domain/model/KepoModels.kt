package com.nofollow.app.features.kepo.domain.model

import com.nofollow.app.core.common.AppError
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.features.tracker.domain.model.SyncConfig

/** Satu akun di daftar Kepo: pernah menonton story kita (dan mungkin like / membalas). */
data class KepoEntry(
    val pk: String,
    val username: String,
    val fullName: String,
    val avatarPath: String?,
    val followedBy: Boolean,
    /** Apakah kita mengikuti akun ini; null = daftar following belum pernah terbaca penuh. */
    val iFollow: Boolean?,
    val likeCount: Int,
    val replyCount: Int,
    /** Jumlah story aktif yang ditonton (dari catatan penonton; story yang sudah hilang tidak dihitung). */
    val watchCount: Int,
    /** Urutan di daftar penonton teratas polling terakhir (urutan dari Instagram); null = tidak masuk. */
    val topRank: Int?,
    val lastAt: Long,
    /** Interaksi baru yang belum dilihat di layar Kepo. */
    val isNew: Boolean,
) {
    val interactionCount: Int get() = likeCount + replyCount

    /** Bukan follower tapi like story dua kali atau lebih. */
    val isSecretAdmirer: Boolean get() = !followedBy && likeCount >= 2

    /** Tidak kita follow, tapi menonton story kita. */
    val isGhostAdmirer: Boolean get() = iFollow == false && watchCount >= 1
}

/** Isi daftar Kepo plus penyebut "dari M story". */
data class KepoData(val entries: List<KepoEntry>, val storyCount: Int)

enum class KepoFilter(val label: String) {
    ALL("Semua"),
    INTERACTED("Interaksi"),
    NOT_FOLLOWER("Bukan follower"),
    GHOST("Ghost Admirer"),
    SECRET("Secret Admirer");

    /** ALL tidak dipakai di sini: isi "Semua" ditentukan [allList]. */
    fun matches(e: KepoEntry): Boolean = when (this) {
        ALL -> true
        INTERACTED -> e.interactionCount > 0
        NOT_FOLLOWER -> !e.followedBy
        GHOST -> e.isGhostAdmirer
        SECRET -> e.isSecretAdmirer
    }
}

/** Urutan peringkat: interaksi (like + balasan) dulu, lalu jumlah story ditonton, lalu yang terbaru. */
val KepoRanking: Comparator<KepoEntry> = compareByDescending<KepoEntry> { it.interactionCount }
    .thenByDescending { it.watchCount }
    .thenByDescending { it.lastAt }

fun List<KepoEntry>.ranked(): List<KepoEntry> = sortedWith(KepoRanking)

/** Kartu "Paling Sering Kepoin Kamu". Hanya muncul kalau ada interaksi atau nonton minimal dua story: bukan klaim kosong. */
fun List<KepoEntry>.hero(): KepoEntry? =
    minWithOrNull(KepoRanking)?.takeIf { it.interactionCount > 0 || it.watchCount >= 2 }

/**
 * Isi filter "Semua" dan angka di chip Beranda, dengan kuota total [quota]: semua yang berinteraksi masuk duluan
 * (urut peringkat, tidak pernah dipotong), sisa kuota diisi penonton tanpa interaksi yang ditandai saat polling
 * (close friend dulu, lalu urutan Instagram). Kalau yang berinteraksi sudah mencapai kuota, tidak ada pengisi.
 * Penonton lain hanya muncul di filter lain.
 */
fun List<KepoEntry>.allList(quota: Int = 5): List<KepoEntry> {
    val hot = filter { it.interactionCount > 0 }.sortedWith(KepoRanking)
    val fillers = filter { it.interactionCount == 0 && it.topRank != null }
        .sortedBy { it.topRank }
        .take((quota - hot.size).coerceAtLeast(0))
    return hot + fillers
}

/** Foto untuk kartu Beranda: lima pertama dari daftar "Semua". */
fun List<KepoEntry>.cardFaces(limit: Int = 5): List<KepoEntry> = allList().take(limit)

sealed interface KepoOutcome {
    /** Tidak ada request yang dikirim. */
    data class Skipped(val reason: SkipReason, val until: Long? = null) : KepoOutcome

    /** [newInteractions] = like/balasan yang baru dihitung; [activeStories] = story yang masih aktif. */
    data class Completed(val newInteractions: Int, val activeStories: Int) : KepoOutcome

    data class Halted(val reason: String) : KepoOutcome

    data class Failed(val error: AppError, val retryAt: Long) : KepoOutcome
}

/** Kebijakan polling story. Waktu dalam milidetik. Cooldown dan halt memakai SyncConfig / sync_state yang sama dengan sweep. */
data class KepoConfig(
    /** Jarak minimal antar polling penonton untuk pemicu otomatis. */
    val autoPollGapMs: Long = 3 * SyncConfig.HOUR,
    /** Menjelang story hilang (24 jam), polling lebih rapat supaya like terakhir tidak terlewat. */
    val nearExpiryMs: Long = 90 * SyncConfig.MINUTE,
    val nearExpiryPollGapMs: Long = 30 * SyncConfig.MINUTE,
    /** reels_tray itu berat (~165 KB), jadi pemicu otomatis hanya memakainya sesekali. */
    val trayAutoGapMs: Long = 6 * SyncConfig.HOUR,
    /** Tombol manual tidak boleh diulang lebih cepat dari ini. */
    val manualMinGapMs: Long = 5 * SyncConfig.MINUTE,
    val maxStoriesPerRun: Int = 4,
    val maxPagesPerStory: Int = 6,
    val pageDelayMinMs: Long = 3_000,
    val pageDelayMaxMs: Long = 8_000,
    /**
     * Plafon request Kepo per hari, dua kantong sendiri (tidak memakan jatah sweep follower).
     * Satu jalan penuh = 1 tray + sampai 6 halaman x 4 story (maks 25 request, biasanya sekitar 10).
     * Otomatis: sampai 6 jalan/hari (Cepat) x maks 25 request x cadangan 1,3 = ±200. Sebelumnya: polling tiap 3 jam plus jelang story hilang. Manual: tombol Cek, jeda minimal 5 menit.
     */
    val autoDailyCap: Int = 200,
    val manualDailyCap: Int = 150,
    /** Maksimal foto baru yang diunduh per jalan. */
    val avatarBudget: Int = 12,
    /** Berapa calon pengisi slot (penonton tanpa interaksi, close friend dulu lalu urutan Instagram, tanpa spam) yang ditandai dari story terbaru. */
    val topViewerCount: Int = 5,
)