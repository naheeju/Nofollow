package com.nofollow.app.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.home.domain.model.HomeOverview
import com.nofollow.app.features.home.domain.model.HomeSyncResult
import com.nofollow.app.features.home.domain.model.HomeSyncSkip
import com.nofollow.app.features.home.domain.repository.HomeSyncJobs
import com.nofollow.app.features.home.domain.usecase.ObserveHomeOverviewUseCase
import com.nofollow.app.features.home.domain.usecase.OnHomeOpenedUseCase
import com.nofollow.app.features.home.domain.usecase.SyncAllUseCase
import com.nofollow.app.features.tracker.domain.model.SyncTrigger
import com.nofollow.app.ui.components.AppMessage
import com.nofollow.app.ui.components.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val overview: HomeOverview? = null,
    val syncing: Boolean = false,
    /** Pesan hasil sinkron manual; tampil sebagai dialog sampai user menekan tombolnya. */
    val message: AppMessage? = null,
)

/**
 * Beranda hanya menampilkan [HomeOverview]; satu-satunya aksi adalah tombol Sinkron.
 *
 * Tombol Sinkron dan sinkron pertama (baseline) berjalan sebagai worker foreground ([HomeSyncJobs]) supaya tidak
 * terputus saat app dibuang dari recents; hasilnya tampil sebagai dialog begitu app dibuka lagi. Pengecekan ringan
 * saat app dibuka (setelah baseline) tetap berjalan langsung di sini.
 *
 * Sinkron otomatis terjadwal memakai worker yang sama tapi tidak dianggap "sedang menyinkron" di layar (progresnya
 * ada di notifikasi), jadi tombol Sinkron tetap bisa ditekan: manual mendahului sinkron otomatis.
 */
class HomeViewModel(
    observeOverview: ObserveHomeOverviewUseCase,
    private val syncAll: SyncAllUseCase,
    private val onHomeOpened: OnHomeOpenedUseCase,
    private val syncJobs: HomeSyncJobs,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var inlineSyncing = false
    private var jobRunning = false
    private var jobAutomatic = false
    private var launching = false

    init {
        observeOverview().onEach { o -> _state.update { it.copy(overview = o) } }.launchIn(viewModelScope)
        syncJobs.running.onEach { running ->
            jobRunning = running
            publishSyncing()
        }.launchIn(viewModelScope)
        syncJobs.automaticRunning.onEach { automatic ->
            jobAutomatic = automatic
            publishSyncing()
        }.launchIn(viewModelScope)
        // Hasil sinkron latar belakang yang belum dilihat: tampil begitu ada (termasuk yang selesai saat app ditutup).
        syncJobs.pendingResult.onEach { result ->
            if (result != null) _state.update { it.copy(message = message(result)) }
        }.launchIn(viewModelScope)
    }

    private fun publishSyncing() = _state.update { it.copy(syncing = inlineSyncing || (jobRunning && !jobAutomatic)) }

    /** Dipanggil tiap layar tampil (ON_START). */
    fun onScreenStarted() {
        viewModelScope.launch { onHomeOpened() }
        runSync(manual = false)
    }

    fun syncNow() = runSync(manual = true)

    private fun runSync(manual: Boolean) {
        // Sinkron otomatis sedang jalan: buka app tidak memulai apa pun (inline-nya akan menunggu kunci sweep).
        // Tombol manual tetap lolos dan mendahului yang otomatis.
        if (!manual && jobAutomatic) return
        if (_state.value.syncing || launching) return
        launching = true
        viewModelScope.launch {
            try {
                if (manual || needsBackgroundSync()) {
                    syncJobs.start(if (manual) SyncTrigger.MANUAL else SyncTrigger.APP_OPEN)
                } else {
                    inlineSyncing = true
                    publishSyncing()
                    try {
                        syncAll(SyncTrigger.APP_OPEN)
                    } finally {
                        inlineSyncing = false
                        publishSyncing()
                    }
                }
            } finally {
                launching = false
            }
        }
    }

    /** Sinkron pertama (baseline) paling lama, jadi berjalan di latar belakang; status belum terbaca dianggap belum selesai. */
    private suspend fun needsBackgroundSync(): Boolean {
        val overview = withTimeoutOrNull(OVERVIEW_WAIT_MS) { _state.first { it.overview != null } }?.overview
        return overview?.syncProgress?.baselineDone != true
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
        viewModelScope.launch { syncJobs.consumeResult() }
    }

    private fun message(result: HomeSyncResult): AppMessage = when (result) {
        is HomeSyncResult.Synced -> AppMessage(
            MessageKind.SUCCESS, "Sinkron selesai", syncedText(result.kepoInteractions),
        )
        HomeSyncResult.InProgress -> AppMessage(
            MessageKind.INFO, "Belum selesai", "Batas per sesi tercapai. Sisanya dilanjutkan otomatis.",
        )
        HomeSyncResult.Aborted -> AppMessage(
            MessageKind.ERROR, "Sinkron dibatalkan",
            "Daftar follower yang terbaca tampak tidak lengkap, jadi tidak ada data yang diubah.",
        )
        HomeSyncResult.Failed -> AppMessage(
            MessageKind.ERROR, "Gagal menyinkron", "Terjadi kesalahan. Akan dicoba lagi otomatis nanti.",
        )
        is HomeSyncResult.Halted -> AppMessage(
            MessageKind.WARNING, "Sinkron berhenti",
            "${result.reason}. Selesaikan dulu di Instagram atau login ulang.",
        )
        is HomeSyncResult.Skipped -> when (result.reason) {
            HomeSyncSkip.COOLING_DOWN -> AppMessage(
                MessageKind.INFO, "Sedang cooldown", "Sinkron dijeda dulu supaya akun tetap aman.", result.until,
            )
            HomeSyncSkip.QUIET_HOURS -> AppMessage(MessageKind.INFO, "Jam tenang", "Tidak ada request otomatis di jam ini.")
            HomeSyncSkip.NOT_DUE -> AppMessage(MessageKind.INFO, "Belum waktunya", "Belum waktunya sinkron.")
            HomeSyncSkip.TOO_SOON -> AppMessage(
                MessageKind.INFO, "Terlalu cepat",
                "Sinkron manual punya jeda 10 menit supaya akun tetap aman dari spam request.", result.until,
            )
            HomeSyncSkip.DAILY_CAP -> AppMessage(
                MessageKind.WARNING, "Batas harian tercapai", "Plafon request harian sudah habis.", result.until,
            )
        }
    }

    private companion object {
        fun syncedText(kepoInteractions: Int?): String {
            val base = "Data follower dan following sudah diperbarui."
            return when {
                kepoInteractions == null -> base
                kepoInteractions > 0 -> "$base Kepo: ada $kepoInteractions interaksi baru, ada yang diam-diam kepo."
                else -> "$base Kepo ikut diperbarui."
            }
        }

        const val OVERVIEW_WAIT_MS = 3_000L
    }
}