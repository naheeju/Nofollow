package com.nofollow.app.features.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.tracker.domain.model.AudienceEntry
import com.nofollow.app.features.tracker.domain.model.EventKind
import com.nofollow.app.features.tracker.domain.model.NotFollowingBackEntry
import com.nofollow.app.features.tracker.domain.model.TabBadges
import com.nofollow.app.features.tracker.domain.model.TrackerLogEntry
import com.nofollow.app.features.tracker.domain.model.TrackerTab
import com.nofollow.app.features.tracker.domain.usecase.ObserveAudienceListsUseCase
import com.nofollow.app.features.tracker.domain.usecase.MarkTabSeenUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveCheckedEntriesUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveNotFollowingBackUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTabBadgesUseCase
import com.nofollow.app.features.tracker.domain.usecase.ObserveTrackerLogUseCase
import com.nofollow.app.features.tracker.domain.usecase.SetEntryCheckedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackerLogUiState(
    val query: String = "",
    /** Kategori terpilih (halaman pager yang sedang tampil). */
    val filter: TrackerTab = TrackerTab.UNFOLLOWERS,
    /** Jumlah per kategori, tidak terpengaruh pencarian maupun filter. */
    val counts: Map<TrackerTab, Int> = emptyMap(),
    /** Kejadian per kategori setelah pencarian; tiap halaman pager membaca daftarnya sendiri. */
    val itemsByKind: Map<EventKind, List<TrackerLogEntry>> = emptyMap(),
    /** Tidak follow balik setelah pencarian; null = daftar following belum pernah diambil. */
    val notFollowingBack: List<AudienceEntry>? = null,
    /** Fans dan mutual setelah pencarian; null = daftar following belum pernah diambil. */
    val fans: List<AudienceEntry>? = null,
    val mutual: List<AudienceEntry>? = null,
    val checkedKeys: Set<String> = emptySet(),
    val loaded: Boolean = false,
)

class TrackerLogViewModel(
    observeLog: ObserveTrackerLogUseCase,
    observeChecked: ObserveCheckedEntriesUseCase,
    observeNotFollowingBack: ObserveNotFollowingBackUseCase,
    observeAudienceLists: ObserveAudienceListsUseCase,
    observeTabBadges: ObserveTabBadgesUseCase,
    private val setChecked: SetEntryCheckedUseCase,
    private val markTabSeen: MarkTabSeenUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TrackerTab.UNFOLLOWERS)

    /** Pencarian dan kategori digabung supaya combine di bawah tetap di lima aliran. */
    private val inputs = combine(query, filter) { q, f -> q to f }

    val state: StateFlow<TrackerLogUiState> = combine(
        observeLog(),
        observeChecked(),
        observeNotFollowingBack(),
        observeAudienceLists(),
        inputs,
    ) { entries, checked, notBack, audience, input ->
        val (q, f) = input
        val needle = q.trim()
        val matched = if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        val byKind = entries.groupingBy { it.kind }.eachCount()
        TrackerLogUiState(
            query = q,
            filter = f,
            counts = TrackerTab.entries.associateWith { tab ->
                val kind = tab.kind
                when (tab) {
                    TrackerTab.NOT_FOLLOWING_BACK -> notBack?.size ?: 0
                    TrackerTab.FANS -> audience?.fans?.size ?: 0
                    TrackerTab.MUTUAL -> audience?.mutual?.size ?: 0
                    else -> kind?.let { byKind[it] } ?: 0
                }
            },
            itemsByKind = matched.groupBy { it.kind },
            notFollowingBack = (if (needle.isEmpty()) notBack else notBack?.filter { it.matches(needle) })
                ?.map { AudienceEntry(it.pk, it.username, it.fullName, it.avatarPath) },
            fans = if (needle.isEmpty()) audience?.fans else audience?.fans?.filter { it.matches(needle) },
            mutual = if (needle.isEmpty()) audience?.mutual else audience?.mutual?.filter { it.matches(needle) },
            checkedKeys = checked,
            loaded = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerLogUiState())

    /** Data baru per kategori (lencana yang sama dengan menu Beranda); layar memakainya untuk tahu kapan harus menandai dilihat. */
    val badges: StateFlow<TabBadges> = observeTabBadges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabBadges())

    /** Kategori [tab] sedang dilihat user: lencananya di Beranda hilang. */
    fun onTabViewed(tab: TrackerTab) {
        viewModelScope.launch { markTabSeen(tab) }
    }

    fun onQueryChange(value: String) = query.update { value }

    /** Dipanggil saat halaman pager berpindah (geser atau ketuk tab). */
    fun onFilterSelected(tab: TrackerTab) = filter.update { tab }

    fun markChecked(entry: TrackerLogEntry) {
        viewModelScope.launch { setChecked(entry.checkKey, true) }
    }

    fun toggleChecked(entry: TrackerLogEntry, currentlyChecked: Boolean) {
        viewModelScope.launch { setChecked(entry.checkKey, !currentlyChecked) }
    }

    private fun NotFollowingBackEntry.matches(needle: String): Boolean =
        username.contains(needle, ignoreCase = true) || fullName.contains(needle, ignoreCase = true)

    private fun AudienceEntry.matches(needle: String): Boolean =
        username.contains(needle, ignoreCase = true) || fullName.contains(needle, ignoreCase = true)

    private fun TrackerLogEntry.matches(needle: String): Boolean =
        username.contains(needle, ignoreCase = true) ||
                fullName.contains(needle, ignoreCase = true) ||
                previousUsername?.contains(needle, ignoreCase = true) == true
}