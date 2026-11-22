package com.nofollow.app.features.auth.domain.usecase

import com.nofollow.app.features.auth.domain.model.Session
import com.nofollow.app.features.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

class ObserveLoginStateUseCase (
    private val repo: SessionRepository
){
    operator fun invoke(): Flow<Session?> = repo.observeSession()
}