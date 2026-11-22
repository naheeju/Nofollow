package com.nofollow.app.navigation


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.auth.domain.usecase.LogoutUseCase
import com.nofollow.app.features.auth.domain.usecase.ObserveLoginStateUseCase
import com.nofollow.app.features.onboarding.domain.usecase.MarkOnboardingSeenUseCase
import com.nofollow.app.features.onboarding.domain.usecase.ObserveOnboardingSeenUseCase
import com.nofollow.app.features.tracker.domain.usecase.MarkTrackerSeenUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveUnseenTrackerCountUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthGate {
    data object Loading : AuthGate
    /** Belum login dan belum pernah melihat pengenalan app. */
    data object Onboarding : AuthGate
    data object LoggedOut : AuthGate
    data class LoggedIn(val userId: String) : AuthGate
}

class AppViewModel(
    observeLoginState: ObserveLoginStateUseCase,
    observeOnboardingSeen: ObserveOnboardingSeenUseCase,
    private val markOnboardingSeenUseCase: MarkOnboardingSeenUseCase,
    observeUnseenTracker: ObserveUnseenTrackerCountUseCase,
    private val markTrackerSeenUseCase: MarkTrackerSeenUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    val gate: StateFlow<AuthGate> = combine(observeLoginState(), observeOnboardingSeen()) { session, seen ->
        when {
            session != null -> AuthGate.LoggedIn(session.userId)
            seen -> AuthGate.LoggedOut
            else -> AuthGate.Onboarding
        }
    }
        // Eagerly: splash screen membaca nilainya sebelum ada UI yang berlangganan.
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthGate.Loading)

    /** Jumlah kejadian baru sejak Tracker terakhir dilihat; isi lencana di bilah navigasi. */
    val trackerBadge: StateFlow<Int> = observeUnseenTracker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Dipanggil selama layar Tracker tampil: semua kejadian sampai sekarang dianggap sudah dilihat. */
    fun markTrackerSeen() {
        viewModelScope.launch { markTrackerSeenUseCase() }
    }

    /** Pengenalan selesai atau dilewati: lanjut ke login dan tidak ditampilkan lagi. */
    fun finishOnboarding() {
        viewModelScope.launch { markOnboardingSeenUseCase() }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }
}