package com.nofollow.app.features.tracker.domain.repository

import com.nofollow.app.features.tracker.domain.model.AudienceSummary
import kotlinx.coroutines.flow.Flow

interface AudienceRepository {
    /** Null selama belum login. */
    val summary: Flow<AudienceSummary?>
}