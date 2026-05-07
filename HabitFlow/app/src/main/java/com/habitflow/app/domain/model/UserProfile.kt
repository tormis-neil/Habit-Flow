package com.habitflow.app.domain.model

data class UserProfile(
    val uid: String,
    val email: String,
    val username: String,
    val createdAt: Long,
)
