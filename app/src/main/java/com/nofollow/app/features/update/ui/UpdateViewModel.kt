package com.nofollow.app.features.update.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.features.update.domain.model.AppVersion
import com.nofollow.app.features.update.domain.usecase.CheckForUpdateUseCase
import com.nofollow.app.features.update.domain.usecase.DismissUpdatePromptUseCase
import com.nofollow.app.features.update.domain.usecase.ObserveAvailableUpdateUseCase
import com.nofollow.app.features.update.domain.usecase.ObserveUpdatePromptUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdateViewModel(
    observeAvailable: ObserveAvailableUpdateUseCase,
    observePrompt: ObserveUpdatePromptUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val dismissPrompt: DismissUpdatePromptUseCase,
) : ViewModel() {

    /** Pembaruan yang tersedia (lebih baru dari yang terpasang), atau null. Dipakai kartu pengingat di Beranda. */
    val available: StateFlow<AppUpdate?> = observeAvailable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pembaruan yang dialognya perlu tampil sekarang (tersedia dan belum ditutup dengan "Nanti"), atau null. */
    val prompt: StateFlow<AppUpdate?> = observePrompt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Dibuat saat app dibuka: cek rilis terbaru (dibatasi satu kali per 24 jam di use case, gagal = diam-diam).
        viewModelScope.launch { checkForUpdate() }
    }

    /** "Nanti" atau setelah memilih unduh: dialog tidak muncul lagi untuk [version] ini. */
    fun dismiss(version: AppVersion) {
        viewModelScope.launch { dismissPrompt(version) }
    }
}