package com.nofollow.app.features.onboarding.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    /** True kalau user sudah menyelesaikan atau melewati pengenalan app. Satu kali seumur instalasi. */
    fun observeSeen(): Flow<Boolean>

    suspend fun markSeen()
}