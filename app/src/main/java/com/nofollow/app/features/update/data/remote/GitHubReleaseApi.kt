package com.nofollow.app.features.update.data.remote

import android.net.Uri
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.core.common.DispatcherProvider
import com.nofollow.app.features.update.domain.model.UpdateConfig
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Membaca tag rilis terbaru dari redirect `github.com/<owner>/<repo>/releases/latest` (HEAD, redirect tidak diikuti):
 * GitHub menjawab 302 dengan header Location ke `/releases/tag/<tag>`. Sengaja bukan API GitHub, karena API publik
 * dibatasi 60 request/jam per IP dan banyak pengguna berbagi IP lewat data seluler, sedangkan redirect ini tidak.
 *
 * Tidak mengirim cookie atau data apa pun milik user.
 */
class GitHubReleaseApi(
    private val config: UpdateConfig,
    private val dispatchers: DispatcherProvider,
) {
    /** Tag mentah (mis. "v1.0.1"), atau Failure: jaringan, repo belum publik (404), atau belum ada rilis. */
    suspend fun latestTag(): AppResult<String> = withContext(dispatchers.io) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(config.latestReleaseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            when {
                code in 300..399 -> {
                    val tag = connection.getHeaderField("Location")
                        ?.substringAfter("/releases/tag/", "")
                        ?.substringBefore('?')
                        ?.takeIf { it.isNotBlank() }
                    // Tanpa "/tag/": repo ada tapi belum punya rilis (GitHub mengarahkan ke daftar rilis).
                    if (tag != null) AppResult.Success(Uri.decode(tag)) else AppResult.Failure(AppError.Unknown("belum ada rilis"))
                }
                code >= 500 -> AppResult.Failure(AppError.Network)
                else -> AppResult.Failure(AppError.Unknown("HTTP $code"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message))
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val USER_AGENT = "Nofollow-Android"
    }
}