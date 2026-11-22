package com.nofollow.app.features.setting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.tracker.domain.model.SyncConfig
import com.nofollow.app.features.tracker.domain.model.SyncMode
import com.nofollow.app.features.tracker.domain.model.TrackerStatus
import com.nofollow.app.features.tracker.domain.usecase.ObserveProbeEnabledUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTrackerStatusUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetProbeEnabledUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetSyncModeUseCase
import com.nofollow.app.features.tracker.domain.usecase.SyncFollowersUseCase
import com.nofollow.app.features.tracker.domain.usecase.TestCountEndpointUseCase
import com.nofollow.app.features.tracker.domain.usecase.TestStoryViewersUseCase
import com.nofollow.app.features.update.domain.repository.UpdateRepository
import com.nofollow.app.features.update.domain.usecase.ObserveUpdateEnabledUseCase
import com.nofollow.app.features.update.domain.usecase.SetUpdateEnabledUseCase
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

/** Perkiraan per mode untuk akun ini: berapa sapuan terjadwal per hari dan plafon request latar belakangnya. */
data class ModeEstimate(val mode: SyncMode, val sweepsPerDay: Int, val requestCap: Int)

data class SettingsUiState(
    val testing: Boolean = false,
    /** Hasil tes endpoint; tampil sebagai dialog sampai ditutup. */
    val result: AppMessage? = null,
    val status: TrackerStatus? = null,
    val mode: SyncMode = SyncMode.DEFAULT,
    /** Pantau latar belakang (foreground service) aktif atau tidak. */
    val probeEnabled: Boolean = false,
    val estimates: Map<SyncMode, ModeEstimate> = emptyMap(),
    /** Cek pembaruan lewat GitHub aktif atau tidak. */
    val updateEnabled: Boolean = true,
    /** Versi app yang sedang terpasang, mis. "1.0.0". */
    val installedVersion: String = "",
)

class SettingsViewModel(
    observeStatus: ObserveTrackerStatusUseCase,
    observeProbeEnabled: ObserveProbeEnabledUseCase,
    observeUpdateEnabled: ObserveUpdateEnabledUseCase,
    private val setUpdateEnabled: SetUpdateEnabledUseCase,
    updateRepository: UpdateRepository,
    private val setSyncMode: SetSyncModeUseCase,
    private val setProbeEnabled: SetProbeEnabledUseCase,
    private val syncFollowers: SyncFollowersUseCase,
    private val config: SyncConfig,
    private val testCountEndpoint: TestCountEndpointUseCase,
    private val testStoryViewers: TestStoryViewersUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(installedVersion = updateRepository.installedVersion().toString()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Status bernilai null kalau belum ada sesi; mode dan perkiraan lama dipertahankan di kasus itu.
        observeStatus().onEach { s ->
            _state.update {
                it.copy(
                    status = s,
                    mode = s?.mode ?: it.mode,
                    estimates = if (s != null) estimatesFor(s.activeFollowers) else it.estimates,
                )
            }
        }.launchIn(viewModelScope)
        observeProbeEnabled().onEach { e -> _state.update { it.copy(probeEnabled = e) } }.launchIn(viewModelScope)
        observeUpdateEnabled().onEach { e -> _state.update { it.copy(updateEnabled = e) } }.launchIn(viewModelScope)
    }

    private fun estimatesFor(activeFollowers: Int): Map<SyncMode, ModeEstimate> =
        SyncMode.entries.associateWith {
            ModeEstimate(it, config.sweepsPerDay(it), config.backgroundCap(it, activeFollowers))
        }

    fun selectMode(mode: SyncMode) {
        viewModelScope.launch { setSyncMode(mode) }
    }

    fun setProbe(enabled: Boolean) {
        viewModelScope.launch { setProbeEnabled(enabled) }
    }

    fun setUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { setUpdateEnabled(enabled) }
    }

    fun clearHalt() {
        viewModelScope.launch { syncFollowers.clearHalt() }
    }

    fun runCountTest() = runTest("Hasil tes endpoint") { testCountEndpoint() }

    fun runStoryTest() = runTest("Hasil tes penonton story") { testStoryViewers() }

    /** Satu tes jalan sekali pada satu waktu; hasilnya tampil sebagai dialog. */
    private fun runTest(title: String, block: suspend () -> List<String>) {
        if (_state.value.testing) return
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val lines = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listOf("Gagal menjalankan tes: ${e.message}")
            }
            _state.update {
                it.copy(
                    testing = false,
                    result = AppMessage(MessageKind.INFO, title, lines.joinToString("\n")),
                )
            }
        }
    }

    fun dismissResult() = _state.update { it.copy(result = null) }
}