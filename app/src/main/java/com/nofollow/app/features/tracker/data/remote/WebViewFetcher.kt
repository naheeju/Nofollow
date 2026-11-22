package com.nofollow.app.features.tracker.data.remote

import android.annotation.SuppressLint
import android.util.Log
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nofollow.app.core.common.AppForeground
import com.nofollow.app.core.common.DispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Menjalankan fetch() GET di dalam WebView tersembunyi yang berada di origin instagram.com,
 * sehingga request keluar lewat jaringan Chromium asli (TLS, header, cookie sah).
 *
 * Kegagalan di level transport (offline, timeout, origin gagal dimuat) dikembalikan sebagai
 * [InstagramApi.Raw] dengan code = -1, bukan exception.
 */
class WebViewFetcher(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    // webView, originReady, dan pageLoad hanya disentuh dari main thread.
    private var webView: WebView? = null
    private var originReady = false
    private var pageLoad: CompletableDeferred<Boolean>? = null

    private val pending = ConcurrentHashMap<String, CompletableDeferred<InstagramApi.Raw>>()
    private val mutex = Mutex()

    inner class Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int, body: String) {
            pending.remove(id)?.complete(InstagramApi.Raw(status, body))
        }

        /** Diagnosis: header cache dari respons stream (age, cache-control, date). Bukan isi respons. */
        @JavascriptInterface
        fun onInfo(text: String) {
            Log.i("NofollowApi", "stream info: $text")
        }

        @JavascriptInterface
        fun onError(id: String, message: String) {
            pending.remove(id)?.complete(InstagramApi.Raw(-1, message))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView =
        WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            // Tanpa ini, renderer WebView yang tidak tampil diturunkan prioritasnya (dan bisa dibekukan) begitu app
            // masuk background, sehingga fetch() menggantung sampai timeout. Dengan prioritas IMPORTANT dan
            // waivedWhenNotVisible=false, renderer tetap hidup selama proses app hidup (mis. sinkron foreground).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            }
            resumeTimers()
            webViewClient = object : WebViewClient() {
                // Urutan saat gagal load: onReceivedError lalu onPageFinished.
                // Deferred hanya bisa selesai sekali, jadi hasil "false" dari error tidak tertimpa.
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoad?.complete(true)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) pageLoad?.complete(false)
                }

                // Render process bisa dimatikan sistem (memori). Tanpa handler ini, app crash.
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    view.destroy()
                    webView = null
                    originReady = false
                    pageLoad?.complete(false)
                    pending.values.forEach { it.complete(InstagramApi.Raw(-1, "render process gone")) }
                    pending.clear()
                    return true
                }
            }
        }

    /** Pastikan WebView ada dan sedang berada di origin instagram.com; muat ulang kalau belum. */
    private suspend fun ensureReady(): WebView = withContext(dispatchers.main) {
        val wv = webView ?: createWebView().also { webView = it }
        if (originReady) return@withContext wv

        val load = CompletableDeferred<Boolean>()
        pageLoad = load
        wv.loadUrl(ANCHOR_URL)
        val ok = try {
            load.await()
        } finally {
            pageLoad = null
        }
        originReady = ok
        if (!ok) throw IOException("Gagal memuat origin instagram.com")
        wv
    }

    suspend fun fetch(url: String): InstagramApi.Raw = run { id -> script(id, url) }

    /**
     * Buang WebView sekarang; request berikutnya membuat yang baru dan memuat ulang origin. Dipakai setelah timeout
     * atau origin gagal dimuat (renderer beku saat app dibuang dari recents). Menunggu [mutex], jadi tidak pernah
     * memotong fetch yang sedang berjalan.
     */
    suspend fun reset() = mutex.withLock {
        withContext(dispatchers.main) {
            webView?.let {
                it.stopLoading()
                it.destroy()
            }
            webView = null
            originReady = false
            pageLoad?.complete(false)
        }
        Log.w("NofollowApi", "WebView dibuat ulang")
    }

    /**
     * GET yang dibaca sebagai stream dan DIBATALKAN begitu teks yang diterima cocok dengan [stopRegex] (sintaks
     * JavaScript) atau sudah melewati [maxChars] karakter. Body yang dikembalikan hanya bagian yang sempat dibaca,
     * jadi cocok untuk mengambil satu tag di bagian atas halaman besar tanpa mengunduh semuanya.
     */
    suspend fun fetchUntil(url: String, stopRegex: String, maxChars: Int): InstagramApi.Raw =
        run { id -> streamScript(id, url, stopRegex, maxChars) }

    private suspend fun run(buildScript: (id: String) -> String): InstagramApi.Raw = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<InstagramApi.Raw>()
        try {
            val raw = withTimeoutOrNull(TIMEOUT_MS.milliseconds) {
                val wv = ensureReady()
                pending[id] = result
                withContext(dispatchers.main) {
                    wv.evaluateJavascript(buildScript(id), null)
                }
                result.await()
            }
            if (raw == null) {
                // Diagnosis: timeout saat app di background menandakan renderer WebView dibekukan sistem.
                Log.w("NofollowApi", "fetch timeout (app tampil=${AppForeground.isForeground})")
            }
            raw ?: InstagramApi.Raw(-1, "timeout")
        } catch (e: IOException) {
            InstagramApi.Raw(-1, e.message.orEmpty())
        } finally {
            pending.remove(id)
        }
    }

    private fun script(id: String, url: String): String = """
        (function() {
          var m = document.cookie.match(/csrftoken=([^;]+)/);
          var h = {
            'X-IG-App-ID': '$APP_ID',
            'X-ASBD-ID': '$ASBD_ID',
            'X-IG-WWW-Claim': '0',
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': '*/*'
          };
          if (m) h['X-CSRFToken'] = m[1];
          var o = {credentials: 'include', headers: h, referrer: 'https://www.instagram.com/'};
          fetch(${JSONObject.quote(url)}, o)
            .then(function(r) { return r.text().then(function(t) { Android.onResult(${JSONObject.quote(id)}, r.status, t); }); })
            .catch(function(e) { Android.onError(${JSONObject.quote(id)}, String(e)); });
        })();
    """.trimIndent()

    private fun streamScript(id: String, url: String, stopRegex: String, maxChars: Int): String = """
        (function() {
          var m = document.cookie.match(/csrftoken=([^;]+)/);
          var h = {
            'X-IG-App-ID': '$APP_ID',
            'X-ASBD-ID': '$ASBD_ID',
            'X-IG-WWW-Claim': '0',
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': '*/*'
          };
          if (m) h['X-CSRFToken'] = m[1];
          var o = {credentials: 'include', headers: h, referrer: 'https://www.instagram.com/', cache: 'no-store'};
          var stop = new RegExp(${JSONObject.quote(stopRegex)});
          fetch(${JSONObject.quote(url)}, o).then(function(r) {
            var status = r.status;
            try {
              Android.onInfo('age=' + r.headers.get('age') + ' cache-control=' + r.headers.get('cache-control') + ' date=' + r.headers.get('date'));
            } catch (e) {}
            if (!r.body || !r.body.getReader) {
              return r.text().then(function(t) { Android.onResult(${JSONObject.quote(id)}, status, t); });
            }
            var reader = r.body.getReader();
            var dec = new TextDecoder('utf-8');
            var acc = '';
            function pump() {
              return reader.read().then(function(x) {
                if (x.done) { Android.onResult(${JSONObject.quote(id)}, status, acc); return; }
                acc += dec.decode(x.value, {stream: true});
                if (stop.test(acc) || acc.length > $maxChars) {
                  reader.cancel();
                  Android.onResult(${JSONObject.quote(id)}, status, acc);
                  return;
                }
                return pump();
              });
            }
            return pump();
          }).catch(function(e) { Android.onError(${JSONObject.quote(id)}, String(e)); });
        })();
    """.trimIndent()

    private companion object {
        const val ANCHOR_URL = "https://www.instagram.com/robots.txt"
        const val APP_ID = "1217981644879628"
        const val ASBD_ID = "359341"
        const val TIMEOUT_MS = 30_000L
    }
}