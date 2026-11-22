package com.nofollow.app.features.auth.domain.usecase

import com.nofollow.app.features.auth.domain.model.Session
import com.nofollow.app.features.auth.domain.repository.SessionRepository

class SaveSessionUseCase(
    private val repo: SessionRepository
) {
    suspend operator fun invoke(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false

        val cookies = cookieHeader
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate { part ->
                part.substringBefore("=") to part.substringAfter("=")
            }

        val sessionId = cookies["sessionid"]?.takeIf { it.isNotBlank() } ?: return false
        val userId = cookies["ds_user_id"]?.takeIf { it.isNotBlank() } ?: return false

        repo.save(
            Session(
                userId = userId,
                sessionId = sessionId,
                cookieHeader = cookieHeader
            )
        )
        return true
    }
}