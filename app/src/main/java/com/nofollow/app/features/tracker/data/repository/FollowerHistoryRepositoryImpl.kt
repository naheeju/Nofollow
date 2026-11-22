package com.nofollow.app.features.tracker.data.repository

import com.nofollow.app.core.common.localDayNumber
import com.nofollow.app.core.session.SessionStore
import com.nofollow.app.features.tracker.data.local.FollowerHistoryDao
import com.nofollow.app.features.tracker.domain.model.FollowerHistoryPoint
import com.nofollow.app.features.tracker.domain.repository.FollowerHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FollowerHistoryRepositoryImpl(
    private val sessionStore: SessionStore,
    private val historyDao: FollowerHistoryDao,
) : FollowerHistoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(days: Int): Flow<List<FollowerHistoryPoint>> =
        sessionStore.session
            .map { it?.userId }
            .distinctUntilChanged()
            .flatMapLatest { owner ->
                if (owner == null) {
                    flowOf(emptyList())
                } else {
                    val from = localDayNumber(System.currentTimeMillis()) - days
                    historyDao.observeSince(owner, from).map { rows ->
                        rows.map { FollowerHistoryPoint(day = it.day, followers = it.followerCount) }
                    }
                }
            }
}