package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nofollow.app.features.tracker.domain.model.FollowingUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.followingDataStore by preferencesDataStore(name = "following_list")

/** Sweep following yang belum selesai (cursor + daftar parsial). Terpisah dari daftar utuh supaya yang utuh tidak pernah setengah-setengah. */
private val Context.followingProgressDataStore by preferencesDataStore(name = "following_progress")

/**
 * Daftar following terakhir yang terbaca penuh, disimpan sebagai satu blok JSON. Sengaja bukan tabel Room:
 * database sekarang memakai fallbackToDestructiveMigration, jadi menambah tabel akan menghapus riwayat follower.
 * Daftar ditulis ulang utuh tiap sweep selesai (tidak pernah setengah-setengah).
 */
class FollowingStore(private val context: Context) {

    data class Snapshot(
        val ownerId: String,
        val syncedAt: Long,
        val users: List<FollowingUser>,
    )

    val snapshot: Flow<Snapshot?> = context.followingDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSnapshot() }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()

    suspend fun current(): Snapshot? = snapshot.first()

    suspend fun save(owner: String, syncedAt: Long, users: List<FollowingUser>) {
        context.followingDataStore.edit {
            it[KEY_OWNER] = owner
            it[KEY_SYNCED_AT] = syncedAt
            it[KEY_USERS] = encode(users)
        }
    }

    /** Sweep yang sedang dicicil. [cursor] null = belum ada halaman yang selesai. */
    data class Progress(
        val ownerId: String,
        val startedAt: Long,
        val cursor: String?,
        val pagesDone: Int,
        val users: List<FollowingUser>,
    )

    suspend fun progress(): Progress? {
        val p = context.followingProgressDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val owner = p[KEY_P_OWNER] ?: return null
        val users = decode(p[KEY_P_USERS] ?: return null) ?: return null
        return Progress(owner, p[KEY_P_STARTED] ?: 0L, p[KEY_P_CURSOR], p[KEY_P_PAGES] ?: 0, users)
    }

    suspend fun saveProgress(progress: Progress) {
        context.followingProgressDataStore.edit {
            it[KEY_P_OWNER] = progress.ownerId
            it[KEY_P_STARTED] = progress.startedAt
            if (progress.cursor == null) it.remove(KEY_P_CURSOR) else it[KEY_P_CURSOR] = progress.cursor
            it[KEY_P_PAGES] = progress.pagesDone
            it[KEY_P_USERS] = encode(progress.users)
        }
    }

    suspend fun clearProgress() {
        context.followingProgressDataStore.edit { it.clear() }
    }

    private fun encode(users: List<FollowingUser>): String = JSONArray().also { arr ->
        users.forEach { u ->
            arr.put(
                JSONObject()
                    .put("pk", u.pk)
                    .put("u", u.username)
                    .put("n", u.fullName)
                    .put("p", u.isPrivate)
                    .put("v", u.isVerified),
            )
        }
    }.toString()

    private fun decode(raw: String): List<FollowingUser>? = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            FollowingUser(
                pk = o.getString("pk"),
                username = o.getString("u"),
                fullName = o.optString("n"),
                isPrivate = o.optBoolean("p"),
                isVerified = o.optBoolean("v"),
            )
        }
    }.getOrNull()

    private fun Preferences.toSnapshot(): Snapshot? {
        val owner = this[KEY_OWNER] ?: return null
        val raw = this[KEY_USERS] ?: return null
        val users = decode(raw) ?: return null
        return Snapshot(owner, this[KEY_SYNCED_AT] ?: 0L, users)
    }

    private companion object {
        val KEY_OWNER = stringPreferencesKey("owner_id")
        val KEY_SYNCED_AT = longPreferencesKey("synced_at")
        val KEY_USERS = stringPreferencesKey("users_json")

        val KEY_P_OWNER = stringPreferencesKey("p_owner_id")
        val KEY_P_STARTED = longPreferencesKey("p_started_at")
        val KEY_P_CURSOR = stringPreferencesKey("p_cursor")
        val KEY_P_PAGES = intPreferencesKey("p_pages_done")
        val KEY_P_USERS = stringPreferencesKey("p_users_json")
    }
}