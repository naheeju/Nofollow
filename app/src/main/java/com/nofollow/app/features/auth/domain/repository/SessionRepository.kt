package com.nofollow.app.features.auth.domain.repository

import com.nofollow.app.features.auth.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSession(): Flow<Session?>

    suspend fun save(session: Session)

    suspend fun clear()
}