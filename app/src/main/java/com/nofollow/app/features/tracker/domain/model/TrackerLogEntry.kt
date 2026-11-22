package com.nofollow.app.features.tracker.domain.model

/** Satu baris di layar Tracker: satu kejadian (unfollow, follow, dst.) lengkap dengan nama dan status following. */
data class TrackerLogEntry(
    val id: Long,
    /** ID numerik akun; kunci untuk foto, tetap walau username berganti. */
    val pk: String,
    val kind: EventKind,
    /** Username pada saat kejadian (untuk RENAME: username baru). */
    val username: String,
    /** Nama tampilan dari data follower; kosong kalau tidak ada. */
    val fullName: String,
    /** Hanya RENAME. */
    val previousUsername: String?,
    /** Waktu terdeteksi. */
    val occurredAt: Long,
    /** Path file foto akun ini; null kalau belum ada, UI jatuh ke huruf awal. */
    val avatarPath: String?,
    /** Apakah kamu mengikuti akun ini (dari daftar following tersimpan); null kalau daftar itu belum pernah diambil. */
    val iFollow: Boolean?,
) {
    /**
     * Kunci tanda "sudah dicek". Sengaja bukan [id]: kalau database pernah dikosongkan, id dipakai ulang dan
     * kejadian baru akan terlihat sudah dicek. pk + waktu terdeteksi tidak berulang.
     */
    val checkKey: String get() = "$pk@$occurredAt"
}