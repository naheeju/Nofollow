package com.nofollow.app.features.tracker.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.nofollow.app.core.common.DispatcherProvider
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.SyncStateDao
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.settings.AccountProfileStore
import com.nofollow.app.features.tracker.domain.model.AccountProfile
import com.nofollow.app.features.tracker.domain.repository.AccountProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

/**
 * Username dan foto akun sendiri, untuk Beranda.
 *
 * Username: accounts/edit/web_form_data/ (terbukti 200 dari sesi saja). Hanya field username yang dibaca,
 * isi lain (email, nomor, bio) tidak disimpan dan tidak dicatat.
 * Foto: meta og:image dari halaman profil instagram.com/{username}/ (terbukti 200), lalu diunduh dari CDN
 * dan disimpan sebagai file supaya tidak bergantung pada URL yang kedaluwarsa.
 *
 * Maksimal 2 request ke Instagram tiap 14 hari, dengan jeda 6-10 detik di antaranya. Tidak jalan kalau
 * sweep sedang halt atau cooldown. Kalau gagal, tidak ada request profil lagi selama beberapa jam.
 */
class AccountProfileRepositoryImpl(
    private val context: Context,
    private val api: InstagramApi,
    private val store: AccountProfileStore,
    private val syncDao: SyncStateDao,
    private val sessionStore: SessionStore,
    private val dispatchers: DispatcherProvider,
) : AccountProfileRepository {

    private val mutex = Mutex()
    private val avatarFile: File get() = File(context.filesDir, AVATAR_FILE)

    override val profile: Flow<AccountProfile?> =
        combine(sessionStore.session, store.snapshot) { session, snap ->
            val owner = session?.userId
            val username = snap.username
            if (owner == null || snap.ownerId != owner || username == null) {
                null
            } else {
                val file = avatarFile
                val hasAvatar = snap.avatarAt > 0 && file.exists()
                AccountProfile(
                    username = username,
                    avatarPath = if (hasAvatar) file.absolutePath else null,
                    avatarVersion = snap.avatarAt,
                )
            }
        }.distinctUntilChanged().flowOn(dispatchers.io)

    override suspend fun refresh() {
        mutex.withLock {
            val owner = sessionStore.session.value?.userId ?: return
            val now = System.currentTimeMillis()

            // Ikut diam kalau sweep sedang berhenti atau cooldown.
            val state = syncDao.get(owner)
            if (state?.haltReason != null || now < (state?.cooldownUntil ?: 0L)) return

            var snap = store.current()
            if (snap.ownerId != owner) {
                store.reset(owner)
                avatarFile.delete()
                snap = store.current()
            }
            if (now < snap.backoffUntil) return

            var username = snap.username
            var requested = false

            if (username == null || now - snap.usernameAt > TTL_MS) {
                requested = true
                when (val r = fetchUsername()) {
                    is Fetch.Ok -> {
                        username = r.value
                        store.setUsername(r.value, now)
                        Log.d(TAG, "username diperbarui: ${r.value}")
                    }
                    is Fetch.Failed -> {
                        Log.w(TAG, "ambil username gagal (HTTP ${r.code}), coba lagi nanti")
                        backoff(r.code)
                        return
                    }
                }
            }
            val name = username

            val avatarStale = snap.avatarAt == 0L || !avatarFile.exists() || now - snap.avatarAt > TTL_MS
            if (!avatarStale) return
            if (requested) delay(Random.nextLong(6_000, 10_001).milliseconds)

            when (val r = fetchAvatarUrl(name)) {
                is Fetch.Failed -> {
                    Log.w(TAG, "ambil halaman profil gagal (HTTP ${r.code}), coba lagi nanti")
                    backoff(r.code)
                }
                is Fetch.Ok -> {
                    if (downloadAvatar(r.value)) {
                        store.setAvatarAt(System.currentTimeMillis())
                        Log.d(TAG, "foto diperbarui")
                    } else {
                        Log.w(TAG, "unduh foto gagal, coba lagi nanti")
                        store.setBackoffUntil(System.currentTimeMillis() + DOWNLOAD_FAIL_BACKOFF_MS)
                    }
                }
            }
        }
    }

    private suspend fun fetchUsername(): Fetch<String> {
        val raw = api.get(FORM_URL, logBody = false)
        if (raw.code !in 200..299) return Fetch.Failed(raw.code)
        val name = runCatching {
            JSONObject(raw.body).optJSONObject("form_data")?.optString("username")
        }.getOrNull()?.takeIf { USERNAME.matches(it) }
        return if (name == null) Fetch.Failed(raw.code) else Fetch.Ok(name)
    }

    private suspend fun fetchAvatarUrl(username: String): Fetch<String> {
        val raw = api.get("https://www.instagram.com/$username/", logBody = false)
        if (raw.code !in 200..299) return Fetch.Failed(raw.code)
        val url = OG_IMAGE.find(raw.body)?.groupValues?.get(1)?.replace("&amp;", "&")
        return if (url == null) Fetch.Failed(raw.code) else Fetch.Ok(url)
    }

    /** Unduh dari CDN (bukan API Instagram, tanpa cookie). Hanya host CDN yang dikenal, dan harus gambar valid. */
    private suspend fun downloadAvatar(url: String): Boolean = withContext(dispatchers.io) {
        runCatching {
            val host = url.toUri().host.orEmpty()
            if (!url.startsWith("https://") || CDN_HOSTS.none { host.endsWith(it) }) {
                return@runCatching false
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                if (conn.responseCode != 200) return@runCatching false
                if (conn.contentLength > MAX_AVATAR_BYTES) return@runCatching false
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return@runCatching false

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return@runCatching false

                val tmp = File(context.filesDir, "$AVATAR_FILE.tmp")
                tmp.writeBytes(bytes)
                avatarFile.delete()
                tmp.renameTo(avatarFile)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private suspend fun backoff(code: Int) {
        val ms = if (code == -1) NETWORK_BACKOFF_MS else FAIL_BACKOFF_MS
        store.setBackoffUntil(System.currentTimeMillis() + ms)
    }

    private sealed interface Fetch<out T> {
        data class Ok<out T>(val value: T) : Fetch<T>
        data class Failed(val code: Int) : Fetch<Nothing>
    }

    private companion object {
        const val TAG = "NofollowProfile"
        const val AVATAR_FILE = "avatar.jpg"
        const val FORM_URL = "https://www.instagram.com/api/v1/accounts/edit/web_form_data/"
        const val TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val NETWORK_BACKOFF_MS = 30L * 60 * 1000
        const val FAIL_BACKOFF_MS = 24L * 60 * 60 * 1000
        const val DOWNLOAD_FAIL_BACKOFF_MS = 6L * 60 * 60 * 1000
        const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
        val CDN_HOSTS = listOf(".fbcdn.net", ".cdninstagram.com")
        val USERNAME = Regex("^[A-Za-z0-9._]{1,30}$")
        val OG_IMAGE = Regex("property=\"og:image\"\\s+content=\"([^\"]+)\"")
    }
}