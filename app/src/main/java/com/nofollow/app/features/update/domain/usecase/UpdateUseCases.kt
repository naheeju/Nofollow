package com.nofollow.app.features.update.domain.usecase

import com.nofollow.app.core.common.AppResult
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.features.update.domain.model.AppVersion
import com.nofollow.app.features.update.domain.model.UpdateConfig
import com.nofollow.app.features.update.domain.repository.UpdateRepository
import com.nofollow.app.features.update.domain.repository.UpdateSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Cek rilis terbaru di GitHub dan simpan hasilnya. Tidak menyentuh Instagram sama sekali.
 *
 * Kalau toggle "Cek pembaruan" mati, tidak ada cek. Selain itu cek dibatasi satu kali per
 * [UpdateConfig.checkIntervalMs] (termasuk percobaan yang gagal, supaya tidak mengetuk terus saat offline).
 * [ignoreInterval] dipakai worker harian, yang sudah punya jadwal sendiri.
 *
 * Hasil: update yang tersedia, atau null kalau sudah terbaru atau cek dilewati. Failure = cek gagal; diam-diam.
 */
class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
    private val settings: UpdateSettings,
    private val config: UpdateConfig,
) {
    suspend operator fun invoke(ignoreInterval: Boolean = false): AppResult<AppUpdate?> {
        if (!settings.enabled.first()) return AppResult.Success(null)
        val now = System.currentTimeMillis()
        val since = now - settings.lastCheckedAt()
        // since negatif = jam perangkat dimundurkan: anggap sudah waktunya.
        if (!ignoreInterval && since in 0 until config.checkIntervalMs) return AppResult.Success(null)

        return when (val result = repository.fetchLatestVersion()) {
            is AppResult.Failure -> {
                settings.recordCheck(now, null)
                result
            }
            is AppResult.Success -> {
                settings.recordCheck(now, result.data)
                val newer = result.data.takeIf { it > repository.installedVersion() }
                AppResult.Success(newer?.let(repository::updateFor))
            }
        }
    }
}

/** Pembaruan yang tersedia (lebih baru dari yang terpasang), atau null. Hilang sendiri setelah app diperbarui. */
class ObserveAvailableUpdateUseCase(
    private val repository: UpdateRepository,
    private val settings: UpdateSettings,
) {
    operator fun invoke(): Flow<AppUpdate?> = settings.availableVersion.map { version ->
        version?.takeIf { it > repository.installedVersion() }?.let(repository::updateFor)
    }
}

/** Pembaruan yang dialognya perlu ditampilkan: tersedia dan belum ditutup user dengan "Nanti". */
class ObserveUpdatePromptUseCase(
    private val available: ObserveAvailableUpdateUseCase,
    private val settings: UpdateSettings,
) {
    operator fun invoke(): Flow<AppUpdate?> =
        combine(available(), settings.dismissedVersion) { update, dismissed ->
            update?.takeIf { it.version != dismissed }
        }
}

class DismissUpdatePromptUseCase(private val settings: UpdateSettings) {
    suspend operator fun invoke(version: AppVersion) = settings.setDismissed(version)
}

/**
 * Klaim hak mengirim notifikasi untuk [update]: true kalau versi ini belum pernah diberitahukan
 * (sekaligus dicatat), false kalau sudah. Satu versi, satu notifikasi.
 */
class ClaimUpdateNotificationUseCase(private val settings: UpdateSettings) {
    suspend operator fun invoke(update: AppUpdate): Boolean {
        if (settings.notifiedVersion() == update.version) return false
        settings.setNotified(update.version)
        return true
    }
}

class ObserveUpdateEnabledUseCase(private val settings: UpdateSettings) {
    operator fun invoke(): Flow<Boolean> = settings.enabled
}

class SetUpdateEnabledUseCase(private val settings: UpdateSettings) {
    suspend operator fun invoke(on: Boolean) = settings.setEnabled(on)
}