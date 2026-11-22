package com.nofollow.app.features.tracker.data.avatar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.nofollow.app.core.common.DispatcherProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import androidx.core.net.toUri

/** Satu foto yang mau diunduh: ID akun + URL CDN-nya. */
data class AvatarSource(val pk: String, val url: String)

/**
 * Foto akun lain (untuk Aktivitas Terbaru, Fans, Mutual, dan Tidak follow balik) sebagai file di filesDir/avatars/{pk}.jpg.
 * Room hanya menyimpan URL; foto sengaja bukan BLOB supaya database tetap kecil dan ikut backup ringan.
 *
 * Unduhan langsung dari CDN tanpa cookie, jadi tidak menyentuh API Instagram dan tidak membebani akun.
 */
class AvatarStore(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    /** Nama folder di filesDir; instance lain (mis. Kepo) memakai folder sendiri supaya prune() tidak saling menghapus. */
    private val dirName: String = "avatars",
) {
    private val dir: File get() = File(context.filesDir, dirName)

    /** Path file foto, atau null kalau belum ada. */
    fun pathOrNull(pk: String): String? = fileFor(pk)?.takeIf { it.exists() }?.absolutePath

    /** True kalau file foto [pk] ada dan umurnya kurang dari [maxAgeMs]. */
    fun isFresh(pk: String, maxAgeMs: Long): Boolean {
        val file = fileFor(pk)?.takeIf { it.exists() } ?: return false
        return System.currentTimeMillis() - file.lastModified() < maxAgeMs
    }

    /**
     * Unduh semua foto di [sources] yang belum ada atau sudah lebih tua dari [maxAgeMs], beberapa sekaligus.
     * Dipanggil per halaman sweep selagi URL CDN-nya masih segar. Yang gagal dilewati (UI jatuh ke huruf awal)
     * dan dicoba lagi di sweep berikutnya. Mengembalikan jumlah foto yang berhasil diunduh.
     */
    suspend fun ensure(sources: List<AvatarSource>, maxAgeMs: Long = MAX_AGE_MS): Int {
        val todo = withContext(dispatchers.io) {
            sources.distinctBy { it.pk }.filterNot { isFresh(it.pk, maxAgeMs) }
        }
        if (todo.isEmpty()) return 0
        val gate = Semaphore(PARALLEL)
        return coroutineScope {
            todo.map { s -> async { gate.withPermit { download(s.pk, s.url) } } }.awaitAll().count { it }
        }
    }

    /**
     * Unduh [url] untuk [pk], menimpa file lama (dipanggil hanya saat ada event baru, jadi foto selalu segar).
     * Menolak URL yang bukan https, bukan host CDN, sudah kedaluwarsa, terlalu besar, atau bukan gambar.
     */
    suspend fun download(pk: String, url: String): Boolean = withContext(dispatchers.io) {
        val target = fileFor(pk) ?: return@withContext false
        try {
            val uri = url.toUri()
            val host = uri.host.orEmpty()
            if (!url.startsWith("https://") || CDN_HOSTS.none { host.endsWith(it) }) {
                Log.w(TAG, "host tidak dikenal ($host), dilewati")
                return@withContext false
            }
            // Parameter oe = waktu kedaluwarsa (epoch detik, hex).
            val expiry = uri.getQueryParameter("oe")?.toLongOrNull(16)
            if (expiry != null && expiry * 1000 < System.currentTimeMillis()) {
                Log.w(TAG, "URL $pk sudah kedaluwarsa, dilewati")
                return@withContext false
            }

            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                if (conn.responseCode != 200) {
                    Log.w(TAG, "CDN HTTP ${conn.responseCode} untuk $pk")
                    return@withContext false
                }
                if (conn.contentLength > MAX_BYTES) return@withContext false
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext false

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return@withContext false

                dir.mkdirs()
                val tmp = File(dir, "$pk.tmp")
                tmp.writeBytes(bytes)
                target.delete()
                val ok = tmp.renameTo(target)
                Log.d(TAG, "foto $pk tersimpan (${bytes.size / 1024} KB, ${bounds.outWidth}x${bounds.outHeight})")
                ok
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "unduh foto $pk gagal: ${e.message}")
            false
        }
    }

    /** Hapus semua foto yang PK-nya tidak ada di [keep]. */
    suspend fun prune(keep: Set<String>) = withContext(dispatchers.io) {
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".tmp") || f.nameWithoutExtension !in keep) f.delete()
        }
    }

    /** PK dari API berupa angka; menolak apa pun selain itu supaya tidak bisa jadi path lain. */
    private fun fileFor(pk: String): File? = pk.takeIf { PK.matches(it) }?.let { File(dir, "$it.jpg") }

    private companion object {
        const val TAG = "NofollowAvatar"
        const val MAX_BYTES = 1024 * 1024
        /** Foto yang lebih tua dari ini diunduh ulang di sweep berikutnya (orang ganti foto profil). */
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        /** Unduhan CDN bersamaan; sedikit saja supaya tidak terlihat seperti salvo. */
        const val PARALLEL = 4
        val PK = Regex("^[0-9]{1,24}$")
        val CDN_HOSTS = listOf(".fbcdn.net", ".cdninstagram.com")
    }
}