package com.nofollow.app.features.kepo.domain.usecase

import com.nofollow.app.features.kepo.domain.repository.KepoRepository

/** Cek story dan penontonnya. [manual] = dipicu tombol Sinkron di Beranda (jam tenang tidak berlaku). */
class SyncKepoUseCase(private val repository: KepoRepository) {
    suspend operator fun invoke(manual: Boolean, full: Boolean = manual) = repository.sync(manual, full)
}