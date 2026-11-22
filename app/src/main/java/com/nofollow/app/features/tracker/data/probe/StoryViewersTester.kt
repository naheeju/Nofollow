package com.nofollow.app.features.tracker.data.probe

import android.util.Log
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.kepo.data.remote.KepoParser
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.domain.probe.StoryViewersProbe
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Tes sekali jalan untuk Kepo: apakah story aktif dan halaman pertama penontonnya terbaca oleh [KepoParser], dan
 * seperti apa pengisi slot yang akan dipilih (close friend dulu, urutan Instagram, tanpa spam)?
 * Hasil ke logcat (tag NofollowVisitTest) dan dikembalikan sebagai baris untuk dialog.
 *
 * Hanya endpoint yang sudah terbukti (reels_tray dan list_reel_media_viewer): 2 request GET, jeda acak 2-4 detik,
 * berhenti di 429. Tidak ada tebakan endpoint: Instagram tidak membuka data kunjungan profil ke sesi web
 * (insight story = NONE, endpoint insight 404, endpoint gaya mobile ditolak sebagai spam).
 *
 * Privasi: tidak ada username / nama yang dicatat, hanya posisi dan hitungan.
 */
class StoryViewersTester(
    private val api: InstagramApi,
    private val sessionStore: SessionStore,
) : StoryViewersProbe {

    override suspend fun run(): List<String> {
        val lines = mutableListOf<String>()
        fun say(text: String) {
            Log.i(TAG, text)
            lines += text
        }

        val owner = sessionStore.session.value?.userId
        if (owner.isNullOrBlank()) {
            say("Belum login.")
            return lines
        }

        val tray = api.get("https://www.instagram.com/api/v1/feed/reels_tray/", logBody = false)
        say("1. reels_tray -> HTTP ${tray.code}, ${tray.body.length} karakter")
        if (tray.code == 429) return lines.also { say("  Kena 429. Berhenti. Jangan diulang cepat-cepat.") }
        if (tray.code !in 200..299) return lines.also { say("  Berhenti di sini.") }
        val stories = KepoParser.parseStories(tray.body, owner)
        if (stories == null) return lines.also { say("  Format tray tidak dikenali.") }
        if (stories.isEmpty()) return lines.also { say("  Tidak ada story aktif. Posting story dulu, lalu tes lagi.") }
        val newest = stories.maxByOrNull { it.takenAt }!!
        say("  Story aktif: ${stories.size}")

        delay(Random.nextLong(2_000, 4_001))
        val res = api.get("https://www.instagram.com/api/v1/media/${newest.pk}/list_reel_media_viewer/", logBody = false)
        say("2. Penonton story terbaru -> HTTP ${res.code}, ${res.body.length} karakter")
        if (res.code == 429) return lines.also { say("  Kena 429. Berhenti. Jangan diulang cepat-cepat.") }
        if (res.code !in 200..299) return lines.also { say("  Berhenti di sini.") }
        val page = KepoParser.parseViewers(res.body)
        if (page == null) return lines.also { say("  Format penonton tidak dikenali.") }

        val all = page.viewers
        val clean = all.filterNot { it.isSpam }
        say("  Halaman pertama: ${all.size} penonton, spam=${all.count { it.isSpam }}, bestie=${clean.count { it.isBestie }}, follower=${clean.count { it.followedBy }}")
        say("  Posisi (mulai 0) like/balas: ${clean.indices.filter { clean[it].hasLiked || clean[it].replied }}")
        val fillers = clean.filter { !it.hasLiked && !it.replied }.sortedByDescending { it.isBestie }.take(5)
        say("  Calon pengisi slot (urutan akhir): ${fillers.map { v -> "#${clean.indexOf(v)}${if (v.isBestie) "*" else ""}" }}  (* = close friend)")
        say("Selesai (2 request).")
        return lines
    }

    private companion object {
        const val TAG = "NofollowVisitTest"
    }
}