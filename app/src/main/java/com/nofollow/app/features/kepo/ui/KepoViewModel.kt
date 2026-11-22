package com.nofollow.app.features.kepo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.core.common.AppError
import com.nofollow.app.features.kepo.domain.model.KepoEntry
import com.nofollow.app.features.kepo.domain.model.KepoFilter
import com.nofollow.app.features.kepo.domain.model.KepoOutcome
import com.nofollow.app.features.kepo.domain.repository.KepoRepository
import com.nofollow.app.features.tracker.domain.model.SkipReason
import com.nofollow.app.ui.components.AppMessage
import com.nofollow.app.ui.components.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class KepoUiState(
    val entries: List<KepoEntry> = emptyList(),
    /** Jumlah story yang tercatat penontonnya (penyebut "Nonton N dari M story"). */
    val storyCount: Int = 0,
    val filter: KepoFilter = KepoFilter.ALL,
    val loaded: Boolean = false,
    val checking: Boolean = false,
    val message: AppMessage? = null,
)

/** Dipakai kartu di Beranda dan layar Kepo; keduanya hanya membaca daftar dari database lokal. */
class KepoViewModel(private val repository: KepoRepository) : ViewModel() {

    private val _state = MutableStateFlow(KepoUiState())
    val state: StateFlow<KepoUiState> = _state.asStateFlow()

    init {
        repository.observe()
            .onEach { data -> _state.update { it.copy(entries = data.entries, storyCount = data.storyCount, loaded = true) } }
            .launchIn(viewModelScope)
    }

    /** Beranda tampil: cek otomatis (kebijakan jarak, jam tenang, dan plafon ada di repository). */
    fun onHomeStarted() {
        viewModelScope.launch {
            try {
                repository.sync(manual = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Gagal diam-diam; polling berikutnya mencoba lagi.
            }
        }
    }

    fun checkNow() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val message = try {
                repository.sync(manual = true).toMessage()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppMessage(MessageKind.ERROR, "Gagal", "Pengecekan gagal. Coba lagi nanti.")
            }
            _state.update { it.copy(checking = false, message = message) }
        }
    }

    fun setFilter(filter: KepoFilter) = _state.update { it.copy(filter = filter) }

    fun markSeen() {
        viewModelScope.launch { repository.markSeen() }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun KepoOutcome.toMessage(): AppMessage = when (this) {
        is KepoOutcome.Completed -> when {
            activeStories == 0 ->
                AppMessage(MessageKind.INFO, "Belum ada yang bisa dikepoin", "Belum ada postingan aktif (24 jam terakhir) yang bisa dicek. Posting dulu, siapa tau ada yang kepo.")
            newInteractions > 0 ->
                AppMessage(MessageKind.SUCCESS, "Ada yang baru", "Cieee, ada $newInteractions interaksi baru. Ada yang diam-diam kepo sama akunmu.")
            else ->
                AppMessage(MessageKind.INFO, "Belum ada yang baru", "Sudah dicek, belum ada yang kepo lagi. Daftar kepo tetap diperbarui.")
        }
        is KepoOutcome.Skipped -> when (reason) {
            SkipReason.COOLING_DOWN ->
                AppMessage(MessageKind.WARNING, "Sedang istirahat", "Instagram membatasi permintaan. Tunggu dulu supaya akun aman.", until)
            SkipReason.TOO_SOON ->
                AppMessage(MessageKind.INFO, "Baru saja dicek", "Tunggu beberapa menit sebelum cek lagi.", until)
            SkipReason.DAILY_CAP ->
                AppMessage(MessageKind.WARNING, "Jatah harian habis", "Pengecekan Kepo dilanjutkan besok.", until)
            else ->
                AppMessage(MessageKind.INFO, "Belum waktunya", "Selesaikan sinkron pertama dulu, atau coba lagi sebentar lagi.")
        }
        is KepoOutcome.Halted ->
            AppMessage(MessageKind.ERROR, "Berhenti", "Sesi perlu dicek ulang di Instagram ($reason).")
        is KepoOutcome.Failed -> when (error) {
            AppError.RateLimited ->
                AppMessage(MessageKind.WARNING, "Kena batas", "Instagram membatasi permintaan. Dicoba lagi otomatis nanti.", retryAt)
            else ->
                AppMessage(MessageKind.ERROR, "Gagal", "Pengecekan gagal. Dicoba lagi otomatis nanti.")
        }
    }
}