package com.nofollow.app.features.tracker.data.probe

import android.util.Log
import com.nofollow.app.features.tracker.data.remote.InstagramApi
import com.nofollow.app.features.tracker.data.settings.AccountProfileStore
import com.nofollow.app.features.tracker.domain.probe.CountEndpointProbe

/**
 * Tes sekali jalan: seberapa bisa jumlah follower dibaca dari luar? Hasil ke logcat (tag NofollowCountTest) dan
 * dikembalikan sebagai daftar baris untuk dialog.
 *
 * Request tetap memakai SESI akun yang sedang login (akun tes), tapi yang dibuka adalah halaman publik
 * [testUsername]. Isi [testUsername] dengan username lain untuk menguji akun yang lebih besar tanpa login
 * dengan akun itu; null = pakai akun yang sedang login.
 *
 * Hasil tes sebelumnya (akun tes, 0-2 follower): halaman profil 200 tapi og:description tertahan di 0 padahal
 * sinkron membaca 2 follower (lebih dari 10 menit); tidak ada angka follower tertanam di HTML; web_profile_info
 * 404; daftar follower tanpa total.
 *
 * Satu request: halaman profil dibaca sebagai stream, berhenti begitu og:description diterima.
 * web_profile_info sudah dibuktikan 404 (endpoint mati) dan dibuang dari tes.
 *
 * Untuk mengukur jeda angka: ubah hitungan di akun target (follow/unfollow), catat jamnya, lalu jalankan tes
 * ini beberapa kali dengan jarak cukup lama (mis. +15, +30, +60 menit) sampai angkanya berubah. Satu request per
 * jalan. Hanya angka hasil ekstraksi yang dicatat, bukan isi respons.
 */
class CountEndpointTester(
    private val api: InstagramApi,
    private val profileStore: AccountProfileStore,
) : CountEndpointProbe {
    /** Akun yang dibuka halaman publiknya. null = akun yang sedang login. */
    private val testUsername: String? = null

    override suspend fun run(): List<String> {
        val lines = mutableListOf<String>()
        fun say(text: String) {
            Log.i(TAG, text)
            lines += text
        }

        val username = testUsername ?: profileStore.current().username
        if (username.isNullOrBlank()) {
            say("Username belum tersimpan. Buka Beranda sekali dulu, lalu coba lagi.")
            return lines
        }
        say("Target: @$username")

        // ---- 1. halaman profil, stream
        val started = System.currentTimeMillis()
        val page = api.getUntil("https://www.instagram.com/$username/", STOP_REGEX)
        say("1. Halaman profil (stream) -> HTTP ${page.code}, ${page.body.length} karakter, ${System.currentTimeMillis() - started} ms")
        if (!continues(page.code, ::say)) return lines
        val description = OG_DESCRIPTION.find(page.body)?.groupValues?.get(1)
        val followers = description?.let { FOLLOWERS_TEXT.find(it) }?.groupValues?.get(1)?.trim()
        say(
            when {
                description == null -> "  og:description tidak ketemu sebelum batas baca (bukan berarti 0 follower)"
                followers == null -> "  og:description ada, tapi angka pengikut tidak terbaca"
                else -> "  og:description: pengikut = $followers"
            },
        )

        say("Selesai. Bandingkan dengan jumlah di app Instagram dan catat jam perubahan terakhir.")
        return lines
    }

    /** True kalau lanjut: 2xx atau 404. 429 dan lainnya berhenti. */
    private fun continues(code: Int, say: (String) -> Unit): Boolean {
        if (code == 429) {
            say("  Kena 429. Berhenti. Jangan diulang cepat-cepat.")
            return false
        }
        if (code !in 200..299 && code != 404) {
            say("  Berhenti di sini.")
            return false
        }
        return true
    }

    private companion object {
        const val TAG = "NofollowCountTest"

        /** Sintaks JavaScript: berhenti begitu seluruh tag og:description sudah diterima. */
        const val STOP_REGEX = "property=\"og:description\"\\s+content=\"[^\"]*\""
        val OG_DESCRIPTION = Regex("property=\"og:description\"\\s+content=\"([^\"]*)\"")
        val FOLLOWERS_TEXT = Regex("([\\d.,]+\\s?[KkMmBb]?)\\s+(?:Followers|Pengikut)", RegexOption.IGNORE_CASE)
    }
}