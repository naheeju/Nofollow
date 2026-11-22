package com.nofollow.app.features.auth.domain.model

data class Session(
    val userId: String,
    val sessionId: String,
    val cookieHeader: String
)
