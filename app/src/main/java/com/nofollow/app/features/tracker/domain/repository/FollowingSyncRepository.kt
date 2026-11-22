package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.FollowingOutcome

interface FollowingSyncRepository {
    /**
     * Sapu daftar following, dicicil lintas jalan lewat cursor yang tersimpan. Hanya jalan kalau sweep follower
     * tidak sedang berlangsung dan sudah selesai sedikitnya 30 detik (tidak pernah berbarengan). Daftar lama baru
     * diganti kalau sweep selesai penuh. Aman dipanggil berulang: kalau belum waktunya, tidak ada request.
     *
     * [manual] = tombol Sinkron (jam tenang tidak berlaku, error jaringan tanpa cooldown). [full] = ikut aturan umur
     * daftar milik tombol manual (disapu ulang tiap sinkron), tanpa mengubah perlakuan jam tenang dan error.
     * Sinkron otomatis memakai full = true supaya following ikut tiap sapuan seperti tombol Sinkron.
     */
    suspend fun sync(manual: Boolean, full: Boolean = manual): FollowingOutcome
}