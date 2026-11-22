package com.nofollow.app.features.auth.domain.usecase

import com.nofollow.app.features.auth.domain.repository.SessionRepository

class LogoutUseCase(
    private val repo: SessionRepository
) {
    suspend operator fun invoke() = repo.clear()
}