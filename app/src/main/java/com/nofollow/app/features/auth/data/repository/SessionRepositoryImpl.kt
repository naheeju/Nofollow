package com.nofollow.app.features.auth.data.repository

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.nofollow.app.core.common.DispatcherProvider
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.core.session.StoredSession
import com.nofollow.app.features.auth.domain.model.Session
import com.nofollow.app.features.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class SessionRepositoryImpl(
    private val store: SessionStore,
    private val dispatcher: DispatcherProvider
) : SessionRepository {
    override fun observeSession(): Flow<Session?> =
        store.session.map{ stored ->
            stored?.let { Session(it.userId, it.sessionId, it.cookieHeader) }
        }

    override suspend fun save(session: Session) = withContext(dispatcher.io) {
        store.save(StoredSession(session.userId, session.sessionId, session.cookieHeader))
    }

    /**
     * Logout asli di sisi app: cookie WebView dan penyimpanan webnya dihapus, lalu sesi di app dilupakan. Urutannya
     * penting: cookie dulu, baru sesi. Kalau sesi dihapus lebih dulu, layar login langsung termuat dengan cookie lama
     * masih hidup dan masuk lagi otomatis ke akun yang sama.
     *
     * Ini tidak memanggil logout Instagram, jadi sesi lama tetap sah di server sampai dicabut dari "tempat kamu login"
     * di Instagram. Konsekuensi lain: login berikutnya terlihat sebagai perangkat baru (identitas perangkat di cookie
     * ikut terhapus), jadi Instagram bisa mengirim notifikasi login atau meminta verifikasi.
     */
    override suspend fun clear() {
        withContext(dispatcher.main) {
            val cookies = CookieManager.getInstance()
            suspendCancellableCoroutine { cont -> cookies.removeAllCookies { cont.resume(Unit) } }
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
        }
        withContext(dispatcher.io) { store.clear() }
    }

}