package com.nofollow.app.features.kepo.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Satu baris per akun yang pernah menonton story kita (yang like / membalas punya hitungan interaksi).
 * Identitas = pk (ID numerik), jadi ganti username tidak membuat baris ganda.
 */
@Entity(tableName = "kepo_people", primaryKeys = ["ownerId", "pk"])
data class KepoPersonEntity(
    /** ds_user_id akun yang sedang kita pantau. */
    val ownerId: String,
    val pk: String,
    val username: String,
    val fullName: String,
    /** URL foto dari polling terakhir; kedaluwarsa dalam hitungan hari, hanya bahan unduhan. */
    val picUrl: String?,
    /** Apakah akun ini mengikuti kita (friendship_status.followed_by). */
    val followedBy: Boolean,
    val firstAt: Long,
    /** Terakhir kali interaksi BARU terdeteksi. */
    val lastAt: Long,
    val likeCount: Int,
    val replyCount: Int,
    /** False sampai layar Kepo dibuka. Penonton tanpa interaksi langsung true (bukan "baru"). */
    val seen: Boolean,
    /** Urutan di daftar penonton teratas polling terakhir (0 = paling atas, urutan dari Instagram); null = tidak masuk. */
    val topRank: Int? = null,
)

/** Satu penonton pada satu story. Dasar hitungan "Nonton N dari M story"; hilang bersama story-nya. */
@Entity(
    tableName = "kepo_views",
    primaryKeys = ["ownerId", "storyPk", "pk"],
    indices = [Index(value = ["ownerId", "pk"])],
)
data class KepoViewEntity(
    val ownerId: String,
    val storyPk: String,
    val pk: String,
    val seenAt: Long,
)

/** Baris kepo_people plus jumlah story yang ditonton (dari kepo_views). */
data class KepoRow(
    @Embedded val person: KepoPersonEntity,
    val watchCount: Int,
)

/** Catatan interaksi yang sudah dihitung, supaya polling berulang tidak menghitung like yang sama dua kali. */
@Entity(tableName = "kepo_hits", primaryKeys = ["ownerId", "storyPk", "pk", "kind"])
data class KepoHitEntity(
    val ownerId: String,
    val storyPk: String,
    val pk: String,
    /** "L" = like story, "R" = balasan. */
    val kind: String,
)

@Dao
interface KepoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHit(hit: KepoHitEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(person: KepoPersonEntity): Long

    @Query(
        "UPDATE kepo_people SET username = :username, fullName = :fullName, picUrl = :picUrl, followedBy = :followedBy " +
                "WHERE ownerId = :ownerId AND pk = :pk",
    )
    suspend fun updateProfile(ownerId: String, pk: String, username: String, fullName: String, picUrl: String?, followedBy: Boolean)

    @Query("UPDATE kepo_people SET likeCount = likeCount + 1, lastAt = :now, seen = 0 WHERE ownerId = :ownerId AND pk = :pk")
    suspend fun addLike(ownerId: String, pk: String, now: Long)

    @Query("UPDATE kepo_people SET replyCount = replyCount + 1, lastAt = :now, seen = 0 WHERE ownerId = :ownerId AND pk = :pk")
    suspend fun addReply(ownerId: String, pk: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertView(view: KepoViewEntity): Long

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM kepo_views v WHERE v.ownerId = p.ownerId AND v.pk = p.pk) AS watchCount " +
                "FROM kepo_people p WHERE p.ownerId = :ownerId",
    )
    fun observeRows(ownerId: String): Flow<List<KepoRow>>

    /** Jumlah story aktif yang tercatat penontonnya (penyebut "dari M story"); story yang hilang ikut hilang dari hitungan. */
    @Query("SELECT COUNT(DISTINCT storyPk) FROM kepo_views WHERE ownerId = :ownerId")
    fun observeStoryCount(ownerId: String): Flow<Int>

    @Query("UPDATE kepo_people SET topRank = NULL WHERE ownerId = :ownerId AND topRank IS NOT NULL")
    suspend fun clearTopRank(ownerId: String)

    @Query("UPDATE kepo_people SET topRank = :rank WHERE ownerId = :ownerId AND pk = :pk")
    suspend fun setTopRank(ownerId: String, pk: String, rank: Int)

    /** Buang catatan nonton semua story yang tidak ada di [keep] (dihapus atau sudah kedaluwarsa). */
    @Query("DELETE FROM kepo_views WHERE ownerId = :ownerId AND storyPk NOT IN (:keep)")
    suspend fun deleteViewsExcept(ownerId: String, keep: List<String>)

    /** Buang catatan like/balasan semua story yang tidak ada di [keep]. */
    @Query("DELETE FROM kepo_hits WHERE ownerId = :ownerId AND storyPk NOT IN (:keep)")
    suspend fun deleteHitsExcept(ownerId: String, keep: List<String>)

    /** Hitung ulang like dan balasan dari kepo_hits yang tersisa, supaya angkanya selalu cermin story yang masih aktif. */
    @Query(
        "UPDATE kepo_people SET " +
                "likeCount = (SELECT COUNT(*) FROM kepo_hits h WHERE h.ownerId = kepo_people.ownerId AND h.pk = kepo_people.pk AND h.kind = 'L'), " +
                "replyCount = (SELECT COUNT(*) FROM kepo_hits h WHERE h.ownerId = kepo_people.ownerId AND h.pk = kepo_people.pk AND h.kind = 'R') " +
                "WHERE ownerId = :ownerId",
    )
    suspend fun recountInteractions(ownerId: String)

    /** Buang akun yang tidak punya catatan nonton lagi (like/balasan selalu ikut punya catatan nonton di story yang sama). */
    @Query(
        "DELETE FROM kepo_people WHERE ownerId = :ownerId " +
                "AND pk NOT IN (SELECT pk FROM kepo_views WHERE ownerId = :ownerId)",
    )
    suspend fun deleteWithoutViews(ownerId: String)

    @Query("SELECT pk FROM kepo_people WHERE ownerId = :ownerId")
    suspend fun allPks(ownerId: String): List<String>

    @Query("UPDATE kepo_people SET seen = 1 WHERE ownerId = :ownerId AND seen = 0")
    suspend fun markAllSeen(ownerId: String)
}