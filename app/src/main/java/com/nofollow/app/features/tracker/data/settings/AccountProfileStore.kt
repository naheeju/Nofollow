package com.nofollow.app.features.tracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.profileDataStore by preferencesDataStore(name = "account_profile")

/** Username dan penanda waktu foto akun yang dipantau. Filenya sendiri ada di filesDir. */
class AccountProfileStore(private val context: Context) {

    data class Snapshot(
        /** ds_user_id pemilik data ini. Beda dengan sesi sekarang = data milik akun lain, diabaikan. */
        val ownerId: String? = null,
        val username: String? = null,
        val usernameAt: Long = 0L,
        val avatarAt: Long = 0L,
        /** Tidak ada request profil sebelum waktu ini (setelah gagal). */
        val backoffUntil: Long = 0L,
    )

    val snapshot: Flow<Snapshot> = context.profileDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSnapshot() }
        .distinctUntilChanged()

    suspend fun current(): Snapshot = snapshot.first()

    /** Ganti pemilik: data profil akun sebelumnya dibuang. */
    suspend fun reset(owner: String) {
        context.profileDataStore.edit {
            it.clear()
            it[KEY_OWNER] = owner
        }
    }

    suspend fun setUsername(username: String, now: Long) {
        context.profileDataStore.edit {
            it[KEY_USERNAME] = username
            it[KEY_USERNAME_AT] = now
        }
    }

    suspend fun setAvatarAt(now: Long) {
        context.profileDataStore.edit { it[KEY_AVATAR_AT] = now }
    }

    suspend fun setBackoffUntil(until: Long) {
        context.profileDataStore.edit { it[KEY_BACKOFF_UNTIL] = until }
    }

    private fun Preferences.toSnapshot() = Snapshot(
        ownerId = this[KEY_OWNER],
        username = this[KEY_USERNAME],
        usernameAt = this[KEY_USERNAME_AT] ?: 0L,
        avatarAt = this[KEY_AVATAR_AT] ?: 0L,
        backoffUntil = this[KEY_BACKOFF_UNTIL] ?: 0L,
    )

    private companion object {
        val KEY_OWNER = stringPreferencesKey("owner_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_USERNAME_AT = longPreferencesKey("username_at")
        val KEY_AVATAR_AT = longPreferencesKey("avatar_at")
        val KEY_BACKOFF_UNTIL = longPreferencesKey("backoff_until")
    }
}