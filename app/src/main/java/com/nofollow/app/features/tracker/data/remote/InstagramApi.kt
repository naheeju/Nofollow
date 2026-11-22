package com.nofollow.app.features.tracker.data.remote

import android.util.Log
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppResult
import kotlinx.coroutines.delay

class InstagramApi(private val fetcher: WebViewFetcher) {
    /** code == -1 berarti gagal di level transport (offline, timeout, origin gagal dimuat). */
    data class Raw(val code: Int, val body: String)

    /** [logBody] false untuk respons yang isinya pribadi (mis. data form profil): hanya kode dan ukuran yang dicatat. */
    suspend fun get(url: String, logBody: Boolean = true): Raw = withTransportRetry(url) { fetcher.fetch(url) }.also {
        // Cuplikan kecil supaya kegagalan bisa didiagnosis dari logcat (tag:NofollowApi).
        val preview = if (logBody) it.body.take(160).replace('\n', ' ') else "(${it.body.length} byte, isi tidak dicatat)"
        Log.d("NofollowApi", "GET ${url.substringBefore('?')} -> ${it.code} $preview")
    }

    /** Seperti [get] tapi dibaca sebagai stream dan berhenti dini; lihat [WebViewFetcher.fetchUntil]. */
    suspend fun getUntil(url: String, stopRegex: String, maxChars: Int = 300_000): Raw =
        withTransportRetry(url) { fetcher.fetchUntil(url, stopRegex, maxChars) }.also {
            Log.d("NofollowApi", "GET(stream) ${url.substringBefore('?')} -> ${it.code} (${it.body.length} karakter dibaca)")
        }

    /** Status dan body dipetakan ke [AppResult]. Body hanya dikembalikan jika 2xx. */
    suspend fun getChecked(url: String, logBody: Boolean = true): AppResult<String> = get(url, logBody).toResult()

    /** Seperti [getChecked], tapi 404 menjadi Success(null) (akun tidak ada / tidak bisa dibuka). */
    suspend fun getAllowingNotFound(url: String): AppResult<String?> {
        val raw = get(url)
        return if (raw.code == 404) AppResult.Success(null) else raw.toResult()
    }

    /**
     * Kegagalan transport (code == -1: offline, timeout, WebView beku) diulang beberapa kali dengan jeda pendek.
     * Aman karena semua request di sini GET dan belum ada respons dari Instagram. Timeout dan origin gagal dimuat
     * biasanya berarti WebView-nya macet (mis. renderer dibekukan saat app dibuang dari recents), jadi WebView
     * dibangun ulang dulu sebelum mencoba lagi, bukan menunggu timeout 30 detik lagi dengan WebView yang sama.
     */
    private suspend fun withTransportRetry(url: String, call: suspend () -> Raw): Raw {
        var raw = call()
        var attempt = 0
        while (raw.code == -1 && attempt < RETRY_DELAYS_MS.size) {
            val wait = RETRY_DELAYS_MS[attempt]
            attempt++
            Log.w(
                "NofollowApi",
                "GET ${url.substringBefore('?')} gagal di transport (${raw.body.take(80)}), " +
                        "coba lagi $attempt/${RETRY_DELAYS_MS.size} dalam ${wait / 1000} dtk",
            )
            if (raw.body == "timeout" || raw.body.contains("origin")) fetcher.reset()
            delay(wait)
            raw = call()
        }
        return raw
    }

    private fun Raw.toResult(): AppResult<String> {
        if (code == -1) return AppResult.Failure(AppError.Network)
        if (code in 200..299) return AppResult.Success(body)

        val hint = body.take(HINT_LENGTH).lowercase()
        val error = when {
            // Instagram kadang membalas throttle dengan 401 + require_login:true, dan teksnya mengikuti
            // bahasa akun ("Harap tunggu beberapa menit..."). Itu bukan sesi habis.
            code == 429 || RATE_LIMIT_HINTS.any { it in hint } -> AppError.RateLimited
            "checkpoint_required" in hint || "challenge_required" in hint -> AppError.Checkpoint
            "login_required" in hint || code == 401 -> AppError.SessionExpired
            "feedback_required" in hint -> AppError.ActionBlocked
            code >= 500 -> AppError.Network
            else -> AppError.Unknown("HTTP $code")
        }
        Log.w("NofollowApi", "HTTP $code dipetakan ke $error")
        return AppResult.Failure(error)
    }

    private companion object {
        const val HINT_LENGTH = 2_000
        val RATE_LIMIT_HINTS = listOf("wait a few minutes", "tunggu beberapa menit")

        /** Jeda sebelum percobaan ulang ke-1, ke-2, ke-3. Total sekitar 17 detik di luar waktu request. */
        val RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)
    }
}