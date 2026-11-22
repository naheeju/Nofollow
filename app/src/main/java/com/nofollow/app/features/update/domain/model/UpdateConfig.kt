package com.nofollow.app.features.update.domain.model

/**
 * Semua yang menentukan dari mana pembaruan dicek, supaya gampang diganti di satu tempat.
 *
 * Aturan rilis di GitHub (wajib diikuti tiap rilis, kalau tidak, cek dan unduhan otomatis gagal):
 * - Tag rilis: "v<versi>", mis. v1.0.1 (sama dengan appVersionName di app/build.gradle.kts).
 * - Nama file APK di Release TETAP, tanpa nomor versi: "<apkPrefix>-<abi>.apk",
 *   mis. nofollow-arm64-v8a.apk dan nofollow-armeabi-v7a.apk.
 * Rilis ditandai "Pre-release" tidak dianggap rilis terbaru oleh GitHub, jadi tidak ikut terdeteksi.
 */
data class UpdateConfig(
    val repoOwner: String = "naheeju",
    val repoName: String = "Nofollow",
    val apkPrefix: String = "nofollow",
    /** Arsitektur yang APK-nya dipublikasikan (sama dengan splits abi di build.gradle.kts). */
    val supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    /** Jarak minimal antar cek dari pembukaan app. Worker harian tidak terikat ini. */
    val checkIntervalMs: Long = 24 * 60 * 60 * 1000L,
) {
    private val repoUrl: String get() = "https://github.com/$repoOwner/$repoName"

    /** Mengarah (redirect) ke /releases/tag/<tag> rilis terbaru; tag dibaca dari header Location. */
    val latestReleaseUrl: String get() = "$repoUrl/releases/latest"

    fun releasePageUrl(version: AppVersion): String = "$repoUrl/releases/tag/v$version"

    /** Mengunduh APK [abi] dari rilis terbaru; GitHub mengarahkannya ke file aset. */
    fun apkDownloadUrl(abi: String): String = "$repoUrl/releases/latest/download/$apkPrefix-$abi.apk"
}