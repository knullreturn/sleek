package com.sleek.app.data.model

data class User(
    val id:               String,
    val username:         String?,
    val tag:              String,
    val avatarUrl:        String?,
    val needsOnboarding:  Boolean = false,
)

data class GoogleAuthRequest(val idToken: String)
data class OnboardRequest(val username: String)
data class AuthResponse(val token: String, val user: User)
