package com.nofollow.app.features.update.domain.model

/**
 * Pembaruan yang tersedia.
 * [downloadUrl] langsung mengunduh APK yang cocok dengan HP ini; kalau arsitektur HP tidak dikenali,
 * isinya sama dengan [releaseUrl] (halaman rilis, user pilih file sendiri).
 */
data class AppUpdate(
    val version: AppVersion,
    val downloadUrl: String,
    val releaseUrl: String,
)