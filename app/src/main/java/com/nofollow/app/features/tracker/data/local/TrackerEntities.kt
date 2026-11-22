package com.nofollow.app.features.tracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu baris per akun yang pernah terlihat sebagai follower.
 * Identitas = pk (ID numerik), bukan username, jadi ganti username tidak dianggap unfollow.
 */
@Entity(
    tableName = "followers",
    primaryKeys = ["ownerId", "pk"],
    indices = [Index(value = ["ownerId", "isActive"])],
)
data class FollowerEntity(
    /** ds_user_id akun yang sedang kita pantau. */
    val ownerId: String,
    val pk: String,
    val username: String,
    val fullName: String,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val isActive: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    /** Sweep terakhir yang melihat akun ini. Yang tertinggal di sweep sebelumnya = kandidat unfollow. */
    val lastSeenSweepId: Long,
    /** Waktu unfollow terkonfirmasi; null selama masih follower. */
    val goneAt: Long? = null,
    /** URL foto dari sweep terakhir yang melihat akun ini. Kedaluwarsa dalam hitungan hari; hanya bahan unduhan, bukan foto itu sendiri. */
    val picUrl: String? = null,
)

enum class FollowEventType {
    UNFOLLOW,
    FOLLOW,
    REFOLLOW,
    RENAME,

    /** Akun hilang dari daftar dan profilnya tidak bisa dibuka (404): dihapus, dinonaktifkan, atau memblokir kita. */
    UNAVAILABLE,
}

/** Riwayat perubahan, dipakai untuk daftar di UI dan untuk menandai mana yang sudah dibaca user. */
@Entity(
    tableName = "follow_events",
    indices = [
        Index(value = ["ownerId", "occurredAt"]),
        Index(value = ["ownerId", "notified"]),
    ],
)
data class FollowEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val pk: String,
    val type: FollowEventType,
    /** Username pada saat kejadian. */
    val username: String,
    /** Hanya untuk RENAME: username sebelumnya. */
    val previousUsername: String? = null,
    /** Waktu terdeteksi. */
    val occurredAt: Long,
    /** Hanya untuk UNFOLLOW/UNAVAILABLE: terakhir kali akun ini terlihat sebagai follower. */
    val lastSeenAt: Long? = null,
    /** False sampai user membuka app (notifikasi dianggap sudah dibaca). */
    val notified: Boolean = false,
)

/** Satu baris per akun: posisi sweep, jadwal, cooldown, dan status berhenti. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val ownerId: String,
    /** Naik 1 setiap sweep baru dimulai. */
    val sweepId: Long = 0,
    val sweepInProgress: Boolean = false,
    /** next_max_id untuk halaman berikutnya (string utuh, jangan diubah). Null = mulai dari awal. */
    val sweepCursor: String? = null,
    val sweepPagesDone: Int = 0,
    /** True kalau seluruh daftar sudah terbaca; sisanya tinggal tahap konfirmasi kandidat. */
    val sweepListDone: Boolean = false,
    val sweepStartedAt: Long? = null,
    val lastCompletedSweepAt: Long? = null,
    /** Rentang waktu (mulai sampai selesai) sweep terakhir, termasuk jeda antar jalan. */
    val lastSweepDurationMs: Long? = null,
    /** Jadwal sweep otomatis berikutnya (sudah termasuk jitter). Null = langsung boleh. */
    val nextSweepAt: Long? = null,
    /** False sampai sweep pertama selesai. Sweep pertama hanya membuat baseline tanpa event. */
    val baselineDone: Boolean = false,
    /** Tidak boleh ada request ke Instagram sebelum waktu ini (epoch ms). */
    val cooldownUntil: Long = 0,
    /** Naik tiap kena 429 berturut-turut, reset setelah sukses. */
    val cooldownLevel: Int = 0,
    /** Selama sekarang < nilai ini, interval sweep dilipat dua (setelah kena throttle). */
    val penaltyUntil: Long = 0,
    /** Total throttle (429 / action blocked) yang pernah tercatat. */
    val throttleCount: Int = 0,
    /** Berhenti total sampai user bertindak (mis. "checkpoint", "session_expired"). Null = normal. */
    val haltReason: String? = null,
)

/** Jumlah request ke Instagram per hari (zona waktu perangkat), untuk plafon harian. */
@Entity(tableName = "daily_usage", primaryKeys = ["ownerId", "day"])
data class DailyUsageEntity(
    val ownerId: String,
    val day: Long,
    val requests: Int,
)

/**
 * Jumlah follower aktif, satu baris per hari (zona waktu perangkat). Ditulis tiap sweep selesai;
 * sweep lain di hari yang sama menimpa baris hari itu. Sumber grafik "Pertumbuhan Akun".
 */
@Entity(tableName = "follower_history", primaryKeys = ["ownerId", "day"])
data class FollowerHistoryEntity(
    val ownerId: String,
    /** Nomor hari lokal, lihat localDayNumber. */
    val day: Long,
    val followerCount: Int,
)