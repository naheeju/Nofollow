package com.nofollow.app.features.tracker.domain.model

/**
 * Kecepatan satu jalan sweep. Semua profil pelan dan acak: tidak ada lagi profil "cepat".
 * [SyncConfig.baseline] (sync pertama, dicicil lintas jalan), [SyncConfig.manual] (tombol Sinkron),
 * [SyncConfig.auto] (worker / buka app), [SyncConfig.probe] (pantau cepat) dan [SyncConfig.following].
 */
data class Pacing(
    /** Jumlah follower per request. Instagram membatasi ~25 per halaman. */
    val pageSize: Int,
    val pageDelayMinMs: Long,
    val pageDelayMaxMs: Long,
    /** Setiap sekian halaman, istirahat lebih panjang. 0 = tidak ada istirahat panjang. */
    val longPauseEveryPages: Int,
    val longPauseMinMs: Long,
    val longPauseMaxMs: Long,
    /** Batas halaman per satu kali jalan; sisanya dilanjutkan di jalan berikutnya lewat cursor. */
    val maxPagesPerRun: Int,
    val confirmDelayMinMs: Long,
    val confirmDelayMaxMs: Long,
    val maxConfirmPerRun: Int,
    /** Peluang jeda "mikir" tambahan setelah sebuah halaman (0 = mati), supaya ritme tidak terlalu mulus. */
    val thinkPauseChance: Double = 0.0,
    val thinkPauseMinMs: Long = 0,
    val thinkPauseMaxMs: Long = 0,
)

/**
 * Kantong plafon harian. Saling tidak memakan:
 * - MANUAL: tombol Sinkron setelah baseline selesai.
 * - BACKGROUND: worker, buka app, dan probe.
 * - BASELINE: semua sweep follower selama baseline belum selesai (dicicil berhari-hari).
 * - FOLLOWING: sweep daftar following.
 */
enum class RequestBucket { MANUAL, BACKGROUND, BASELINE, FOLLOWING }

/**
 * Semua angka kebijakan "aman" ada di sini supaya gampang diatur dan diuji.
 * Waktu dalam milidetik. Interval sweep ada di SyncMode.
 */
data class SyncConfig(
    /**
     * Sweep (setelah baseline) yang menggantung lebih lama dari ini dibuang dan dimulai ulang (daftar sudah
     * terlalu basi untuk mendeteksi unfollow). Tidak berlaku untuk baseline: baseline tidak mendeteksi apa pun.
     */
    val maxSweepAgeMs: Long = 12 * HOUR,
    /** Jam [quietStartHour, quietEndHour) tidak ada request otomatis (kecuali tombol manual). */
    val quietStartHour: Int = 0,
    val quietEndHour: Int = 7,

    /** Jadwal sweep berikutnya digeser acak ±fraksi ini supaya pola tidak kaku. */
    val intervalJitter: Double = 0.25,
    /** Setelah kena throttle, interval dilipat [penaltyIntervalMultiplier] selama durasi ini. */
    val penaltyDurationMs: Long = 24 * HOUR,
    val penaltyIntervalMultiplier: Int = 2,
    /** Jeda minimal antar sweep untuk tombol manual. */
    val manualMinGapMs: Long = 10 * MINUTE,
    /** Jeda minimal antar jalan baseline (buka app berulang tidak melipatgandakan request). */
    val baselineRunMinGapMs: Long = 10 * MINUTE,

    /**
     * Plafon request ke Instagram per hari (zona waktu perangkat), dipisah per kantong supaya yang satu
     * tidak bisa menghabiskan jatah yang lain. Satu halaman 16-25 user, jadi akun ~700 follower butuh sekitar
     * 30-45 request per sweep penuh, jadi 200 request muat sekitar empat sweep manual sehari plus konfirmasi unfollow.
     * Angka di bawah dihitung untuk akun terbesar yang didukung ([maxAccountSize] follower dan following) di mode
     * Cepat (6 sapuan/hari): satu sapuan penuh ±314 request per daftar. Akun kecil tidak ikut longgar karena plafon
     * latar belakang dan following dihitung dinamis dari ukuran akun (lantai dan langit-langit di bawah).
     * Kantong manual: tiga sapuan penuh akun terbesar per hari. Pengaman 429 (cooldown 6 jam dan seterusnya) tetap berlaku.
     */
    val manualDailyCap: Int = 1_000,
    /** Akun dengan follower atau following di atas ini tidak didukung: sinkron berhenti, supaya plafon harian tidak jebol. */
    val maxAccountSize: Int = 5_000,
    /**
     * Lantai plafon latar belakang. Plafon sebenarnya mengikuti mode sinkron dan jumlah follower, lihat
     * [backgroundCap]; angka ini dipakai kalau hitungannya lebih kecil (mis. akun hampir kosong).
     */
    val backgroundDailyCap: Int = 200,
    /** Langit-langit plafon latar belakang berapa pun mode dan jumlah follower-nya. */
    val backgroundCapCeiling: Int = 2_500,
    /** Cadangan di atas kebutuhan murni: jitter jadwal, sweep yang dilanjutkan, dan konfirmasi unfollow. */
    val backgroundCapHeadroom: Double = 1.3,
    /** Sinkron pertama akun terbesar (±314 request) harus selesai di hari pertama. */
    val baselineDailyCap: Int = 400,
    /** Lantai plafon following; plafon sebenarnya dinamis, lihat [followingCap]. */
    val followingDailyCap: Int = 400,
    /** Langit-langit plafon following (sapuan otomatis Cepat plus dua sapuan manual untuk akun terbesar). */
    val followingCapCeiling: Int = 3_300,

    /**
     * Probe = sweep pelan berkala di latar belakang (notifikasi bonus). Default fitur ini mati.
     * Jarak antar sweep = max([probeBaseIntervalMs], jarak yang muat di [backgroundDailyCap]), dijepit
     * ke [probeMaxIntervalMs], lalu diberi jitter [probeJitter].
     */
    val probeBaseIntervalMs: Long = 6 * HOUR,
    val probeMaxIntervalMs: Long = 12 * HOUR,
    val probeJitter: Double = 0.33,
    /** Jam aktif probe per hari (di luar [probeQuietStartHour, probeQuietEndHour)); dipakai menghitung anggaran. */
    val probeActiveHoursPerDay: Int = 20,
    /** Rata-rata user nyata per halaman (Instagram membatasi ~25 dan sering menyaring), untuk memperkirakan request per sweep. */
    val effectiveUsersPerPage: Int = 16,
    /** Probe tidak memulai sweep baru kalau sweep terakhir (dari pemicu apa pun, termasuk manual) lebih baru dari ini. */
    val probeSweepMinGapMs: Long = 3 * HOUR,
    /** Batas bawah jeda cek ulang service probe saat ditunda; juga lantai jam alarm. */
    val probeMinIntervalMs: Long = 5 * MINUTE,
    /** Jam [probeQuietStartHour, probeQuietEndHour) probe istirahat (notif tengah malam tidak berguna). */
    val probeQuietStartHour: Int = 1,
    val probeQuietEndHour: Int = 5,
    /** Probe yang ditunda (jam tenang, cooldown, plafon) dicek lagi paling lambat setelah ini. */
    val probeIdleRecheckMs: Long = 30 * MINUTE,
    /** Sweep probe yang terpotong batas halaman dilanjutkan setelah jeda acak di antara dua nilai ini. */
    val probeResumeMinMs: Long = 10 * MINUTE,
    val probeResumeMaxMs: Long = 20 * MINUTE,

    // ------------------------------------------------------------ following

    /**
     * Following disambung setelah follower: baru boleh mulai/lanjut kalau sweep follower terakhir selesai sedikitnya
     * sejauh ini. Sinkron manual dan worker menunggu selama ini di antara keduanya (tidak pernah berbarengan).
     */
    val followingStartGapMs: Long = 5 * SECOND,
    /** Umur minimal daftar following sebelum disapu ulang. Manual pendek supaya selalu ikut tersambung. */
    val followingAutoMinAgeMs: Long = 24 * HOUR,
    val followingManualMinAgeMs: Long = 10 * MINUTE,
    /** Sweep following yang menggantung lebih lama dari ini dibuang dan dimulai ulang. */
    val followingMaxSweepAgeMs: Long = 48 * HOUR,

    // --------------------------------------------------------------- pacing

    /**
     * Sync pertama (baseline): sekali seumur akun dan user menunggu di layar, jadi dibuat cepat tapi tetap acak
     * dan tidak salvo: 0,5-1,0 detik per halaman plus istirahat 4-6 detik tiap 25 halaman. Akun ~700 follower
     * (±45 request) selesai sekitar 1,5 menit. Tanpa batas halaman per jalan supaya akun kecil selesai sekali jalan.
     * Kalau terputus (429, jaringan) atau plafon harian tercapai, dilanjutkan dari cursor yang tersimpan.
     * Instagram membatasi halaman ~25 user, jadi pageSize 25 (bukan 100). Rem latensi otomatis di SweepPacer tetap aktif.
     * (Profil sebelumnya: 800-1.600 ms, istirahat 6-10 detik tiap 20 halaman.)
     */
    val baseline: Pacing = Pacing(
        pageSize = 25,
        pageDelayMinMs = 500,
        pageDelayMaxMs = 1_000,
        longPauseEveryPages = 25,
        longPauseMinMs = 4_000,
        longPauseMaxMs = 6_000,
        maxPagesPerRun = Int.MAX_VALUE,
        confirmDelayMinMs = 3_000,
        confirmDelayMaxMs = 7_000,
        maxConfirmPerRun = 20,
    ),
    /**
     * Tombol Sinkron: user menunggu di layar, jadi ritmenya sama dengan baseline (0,5-1,0 detik per halaman, istirahat
     * 4-6 detik tiap 25 halaman), tetap acak dan bukan salvo. Jeda "mikir" dimatikan demi kecepatan. Akun ~700 follower
     * selesai sekitar 1,5 menit. Satu jalan cukup untuk satu sweep penuh.
     * (Profil sebelumnya: 800-1.600 ms, istirahat 6-10 detik tiap 20 halaman, jeda mikir 5-10 detik peluang 10%.)
     */
    val manual: Pacing = Pacing(
        pageSize = 25,
        pageDelayMinMs = 500,
        pageDelayMaxMs = 1_000,
        longPauseEveryPages = 25,
        longPauseMinMs = 4_000,
        longPauseMaxMs = 6_000,
        maxPagesPerRun = Int.MAX_VALUE,
        confirmDelayMinMs = 1_500,
        confirmDelayMaxMs = 3_500,
        maxConfirmPerRun = 40,
    ),
    /**
     * Diff otomatis (worker / buka app) setelah baseline: pelan dan acak (3-8 detik per halaman, lebih pelan dari
     * tombol manual). Halaman 25 user seperti manual dan baseline; 12 membuat satu sweep ~60-80 request.
     */
    val auto: Pacing = Pacing(
        pageSize = 25,
        pageDelayMinMs = 3_000,
        pageDelayMaxMs = 8_000,
        longPauseEveryPages = 10,
        longPauseMinMs = 20_000,
        longPauseMaxMs = 40_000,
        maxPagesPerRun = Int.MAX_VALUE,
        confirmDelayMinMs = 2_000,
        confirmDelayMaxMs = 5_000,
        maxConfirmPerRun = 40,
    ),
    /** Probe latar belakang: pelan dan acak, tidak ada yang menunggu. Sisanya dilanjutkan di siklus berikutnya. */
    val probe: Pacing = Pacing(
        pageSize = 25,
        pageDelayMinMs = 3_000,
        pageDelayMaxMs = 8_000,
        longPauseEveryPages = 10,
        longPauseMinMs = 20_000,
        longPauseMaxMs = 40_000,
        maxPagesPerRun = 60,
        confirmDelayMinMs = 3_000,
        confirmDelayMaxMs = 7_000,
        maxConfirmPerRun = 40,
    ),
    /**
     * Sweep following: ritme sama dengan baseline (0,5-1,0 detik per halaman, istirahat 4-6 detik tiap 25 halaman),
     * supaya sinkron penuh total sekitar 2 menit. Konfirmasi tidak dipakai.
     * (Profil sebelumnya: 800-1.600 ms, istirahat 6-10 detik tiap 20 halaman.)
     */
    val following: Pacing = Pacing(
        pageSize = 25,
        pageDelayMinMs = 500,
        pageDelayMaxMs = 1_000,
        longPauseEveryPages = 25,
        longPauseMinMs = 4_000,
        longPauseMaxMs = 6_000,
        maxPagesPerRun = Int.MAX_VALUE,
        confirmDelayMinMs = 0,
        confirmDelayMaxMs = 0,
        maxConfirmPerRun = 0,
    ),

    /** Kalau kandidat hilang lebih dari max([safetyMinMissing], aktif * [safetyMissingFraction]) daftar dianggap rusak. */
    val safetyMinMissing: Int = 50,
    val safetyMissingFraction: Double = 0.5,

    /** Tangga cooldown untuk 429 berturut-turut. Mencoba lagi cepat setelah 429 hanya memperburuk keadaan. */
    val rateLimitLadderMs: List<Long> = listOf(6 * HOUR, 24 * HOUR, 48 * HOUR),
    val actionBlockedCooldownMs: Long = 48 * HOUR,
    /**
     * Error jaringan murni (offline, WebView beku, timeout) bukan sinyal dari Instagram. Sinkron pertama dan tombol
     * manual tidak kena cooldown ini sama sekali; sinkron otomatis kena yang pendek ini supaya tidak mengetuk terus
     * saat sinyal jelek.
     */
    val autoNetworkCooldownMs: Long = 3 * MINUTE,
    val unknownCooldownMs: Long = 30 * MINUTE,

    // ------------------------------------------------------- sinkron otomatis

    /** Alarm sinkron otomatis tidak dijadwalkan lebih cepat dari ini (lantai jarak bangun). */
    val autoMinTickMs: Long = 5 * MINUTE,
    /** Sinkron otomatis yang terpotong batas per jalan (atau gagal) dilanjutkan setelah jeda acak di antara dua nilai ini. */
    val autoResumeMinMs: Long = 10 * MINUTE,
    val autoResumeMaxMs: Long = 20 * MINUTE,
) {
    /** Baseline belum selesai: semua pemicu memakai profil baseline. Setelahnya: manual, probe, atau auto. */
    fun pacingFor(trigger: SyncTrigger, baselineDone: Boolean): Pacing = when {
        !baselineDone -> baseline
        trigger == SyncTrigger.MANUAL -> manual
        trigger == SyncTrigger.PROBE -> probe
        else -> auto
    }

    fun bucketFor(trigger: SyncTrigger, baselineDone: Boolean): RequestBucket = when {
        !baselineDone -> RequestBucket.BASELINE
        trigger == SyncTrigger.MANUAL -> RequestBucket.MANUAL
        else -> RequestBucket.BACKGROUND
    }

    fun capFor(bucket: RequestBucket): Int = when (bucket) {
        RequestBucket.MANUAL -> manualDailyCap
        RequestBucket.BACKGROUND -> backgroundDailyCap
        RequestBucket.BASELINE -> baselineDailyCap
        RequestBucket.FOLLOWING -> followingDailyCap
    }

    /** Jam aktif per hari untuk sweep terjadwal (di luar jam tenang). */
    private val scheduledActiveHours: Int
        get() = if (quietStartHour < quietEndHour) 24 - (quietEndHour - quietStartHour) else quietStartHour - quietEndHour

    /** Perkiraan jumlah sweep terjadwal per hari untuk [mode]. */
    fun sweepsPerDay(mode: SyncMode): Int =
        ((scheduledActiveHours * HOUR + mode.intervalMs - 1) / mode.intervalMs).toInt().coerceAtLeast(1)

    /** Perkiraan request untuk satu sweep penuh: satu per halaman ditambah satu halaman penutup. */
    fun requestsPerSweep(activeFollowers: Int): Int =
        (activeFollowers + effectiveUsersPerPage - 1) / effectiveUsersPerPage + 1

    /**
     * Plafon kantong BACKGROUND hari ini: muat semua sweep jadwal [mode] untuk [activeFollowers] akun,
     * dengan cadangan [backgroundCapHeadroom], dijepit antara [backgroundDailyCap] dan [backgroundCapCeiling].
     * Tanpa ini plafon tetap 100 membuat mode Normal/Cepat tidak pernah tercapai untuk akun ratusan follower.
     */
    fun backgroundCap(mode: SyncMode, activeFollowers: Int): Int {
        val need = kotlin.math.ceil(sweepsPerDay(mode) * requestsPerSweep(activeFollowers) * backgroundCapHeadroom).toInt()
        return need.coerceIn(backgroundDailyCap, backgroundCapCeiling)
    }

    /**
     * Plafon kantong FOLLOWING hari ini. Kantong ini dipakai sapuan otomatis dan manual, jadi dihitung untuk
     * sapuan terjadwal mode Cepat (paling banyak) ditambah dua sapuan manual, dengan cadangan [backgroundCapHeadroom],
     * dijepit antara [followingDailyCap] dan [followingCapCeiling].
     */
    fun followingCap(followingCount: Int): Int {
        val sweeps = sweepsPerDay(SyncMode.CEPAT) + 2
        val need = kotlin.math.ceil(sweeps * requestsPerSweep(followingCount) * backgroundCapHeadroom).toInt()
        return need.coerceIn(followingDailyCap, followingCapCeiling)
    }

    /** Total plafon maksimum semua kantong, untuk tampilan status. */
    val totalDailyCap: Int get() = manualDailyCap + backgroundCapCeiling + baselineDailyCap + followingCapCeiling

    /** Kunci baris daily_usage per kantong (kantong latar belakang memakai ownerId apa adanya). */
    fun usageKey(owner: String, bucket: RequestBucket): String = when (bucket) {
        RequestBucket.BACKGROUND -> owner
        RequestBucket.MANUAL -> "$owner#manual"
        RequestBucket.BASELINE -> "$owner#baseline"
        RequestBucket.FOLLOWING -> "$owner#following"
    }

    companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
        const val HOUR = 60 * MINUTE
    }
}