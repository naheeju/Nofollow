package com.nofollow.app.features.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowerDao {
    @Query("SELECT * FROM followers WHERE ownerId = :ownerId AND pk IN (:pks)")
    suspend fun findByPks(ownerId: String, pks: List<String>): List<FollowerEntity>

    @Upsert
    suspend fun upsertAll(items: List<FollowerEntity>)

    /** Follower aktif yang tidak terlihat di sweep [sweepId]: kandidat unfollow, belum terkonfirmasi. */
    @Query(
        "SELECT * FROM followers " +
                "WHERE ownerId = :ownerId AND isActive = 1 AND lastSeenSweepId < :sweepId",
    )
    suspend fun findMissing(ownerId: String, sweepId: Long): List<FollowerEntity>

    @Query("UPDATE followers SET isActive = 0, goneAt = :now WHERE ownerId = :ownerId AND pk = :pk")
    suspend fun markGone(ownerId: String, pk: String, now: Long)

    /** Dipakai kalau kandidat ternyata masih follower setelah dikonfirmasi. */
    @Query(
        "UPDATE followers SET lastSeenSweepId = :sweepId, lastSeenAt = :now " +
                "WHERE ownerId = :ownerId AND pk = :pk",
    )
    suspend fun markSeen(ownerId: String, pk: String, sweepId: Long, now: Long)

    @Query("SELECT COUNT(*) FROM followers WHERE ownerId = :ownerId AND isActive = 1")
    suspend fun countActive(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM followers WHERE ownerId = :ownerId AND isActive = 1")
    fun observeActiveCount(ownerId: String): Flow<Int>

    /** Sama dengan [observeActivePks] tapi sekali baca; dipakai untuk menentukan foto mana yang dipertahankan. */
    @Query("SELECT pk FROM followers WHERE ownerId = :ownerId AND isActive = 1")
    suspend fun activePks(ownerId: String): List<String>

    /** PK semua follower aktif, dipakai untuk hitung mutual/fans secara lokal. */
    @Query("SELECT pk FROM followers WHERE ownerId = :ownerId AND isActive = 1")
    fun observeActivePks(ownerId: String): Flow<List<String>>

    /** Follower aktif lengkap dengan nama, dipakai untuk daftar Fans dan Mutual. Yang paling baru terlihat di atas. */
    @Query(
        "SELECT * FROM followers WHERE ownerId = :ownerId AND isActive = 1 " +
                "ORDER BY firstSeenAt DESC, username COLLATE NOCASE",
    )
    fun observeActive(ownerId: String): Flow<List<FollowerEntity>>

    @Query("DELETE FROM followers WHERE ownerId = :ownerId")
    suspend fun deleteAll(ownerId: String)
}

@Dao
interface FollowEventDao {
    @Insert
    suspend fun insertAll(events: List<FollowEventEntity>)

    @Query(
        "SELECT * FROM follow_events WHERE ownerId = :ownerId " +
                "ORDER BY occurredAt DESC, id DESC LIMIT :limit",
    )
    fun observeRecent(ownerId: String, limit: Int): Flow<List<FollowEventEntity>>

    /** Unfollow yang belum dibaca user, urut dari yang paling lama. */
    @Query(
        "SELECT * FROM follow_events " +
                "WHERE ownerId = :ownerId AND notified = 0 AND type IN ('UNFOLLOW', 'UNAVAILABLE') ORDER BY occurredAt",
    )
    suspend fun findUnnotifiedUnfollows(ownerId: String): List<FollowEventEntity>

    /** PK dari [limit] event terbaru; foto di luar daftar ini boleh dihapus dari penyimpanan. */
    @Query(
        "SELECT DISTINCT pk FROM (SELECT pk FROM follow_events WHERE ownerId = :ownerId " +
                "ORDER BY occurredAt DESC, id DESC LIMIT :limit)",
    )
    suspend fun recentPks(ownerId: String, limit: Int): List<String>

    /** Id kejadian terbaru milik [ownerId]; null kalau belum ada kejadian. Id naik terus (autoincrement). */
    @Query("SELECT MAX(id) FROM follow_events WHERE ownerId = :ownerId")
    suspend fun maxId(ownerId: String): Long?

    /**
     * Jumlah kejadian dengan id di atas [afterId]; sumber lencana Tracker. Pakai id (urutan masuk), bukan waktu,
     * jadi tidak terpengaruh jam perangkat maupun waktu kejadian yang dicatat lebih awal dari saat disimpan.
     */
    @Query("SELECT COUNT(*) FROM follow_events WHERE ownerId = :ownerId AND id > :afterId")
    fun observeCountAfter(ownerId: String, afterId: Long): Flow<Int>

    /** Jumlah unfollow (termasuk akun hilang/nonaktif) dengan id di atas [afterId]; sumber lencana menu Unfollowers di Beranda. */
    @Query(
        "SELECT COUNT(*) FROM follow_events " +
                "WHERE ownerId = :ownerId AND id > :afterId AND type IN ('UNFOLLOW', 'UNAVAILABLE')",
    )
    fun observeGoneCountAfter(ownerId: String, afterId: Long): Flow<Int>

    @Query("UPDATE follow_events SET notified = 1 WHERE ownerId = :ownerId AND notified = 0")
    suspend fun markAllNotified(ownerId: String)

    @Query("DELETE FROM follow_events WHERE ownerId = :ownerId")
    suspend fun deleteAll(ownerId: String)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE ownerId = :ownerId")
    suspend fun get(ownerId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE ownerId = :ownerId")
    fun observe(ownerId: String): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("UPDATE sync_state SET nextSweepAt = :nextSweepAt WHERE ownerId = :ownerId")
    suspend fun setNextSweepAt(ownerId: String, nextSweepAt: Long)

    @Query("DELETE FROM sync_state WHERE ownerId = :ownerId")
    suspend fun delete(ownerId: String)
}

@Dao
interface DailyUsageDao {
    @Query("SELECT requests FROM daily_usage WHERE ownerId = :ownerId AND day = :day")
    suspend fun count(ownerId: String, day: Long): Int?

    @Query("SELECT requests FROM daily_usage WHERE ownerId = :ownerId AND day = :day")
    fun observe(ownerId: String, day: Long): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(row: DailyUsageEntity)

    @Query("UPDATE daily_usage SET requests = requests + 1 WHERE ownerId = :ownerId AND day = :day")
    suspend fun increment(ownerId: String, day: Long)

    @Query("DELETE FROM daily_usage WHERE ownerId = :ownerId AND day < :beforeDay")
    suspend fun pruneBefore(ownerId: String, beforeDay: Long)
}

@Dao
interface FollowerHistoryDao {
    @Upsert
    suspend fun upsert(row: FollowerHistoryEntity)

    /** Urut dari hari paling lama. */
    @Query("SELECT * FROM follower_history WHERE ownerId = :ownerId AND day >= :fromDay ORDER BY day")
    fun observeSince(ownerId: String, fromDay: Long): Flow<List<FollowerHistoryEntity>>

    @Query("DELETE FROM follower_history WHERE ownerId = :ownerId AND day < :beforeDay")
    suspend fun pruneBefore(ownerId: String, beforeDay: Long)
}