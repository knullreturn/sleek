package com.sleek.app.data.model

data class User(
    val id:        String,
    val email:     String,
    val username:  String?,
    val tag:       String,
    val avatarUrl: String?,
)

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val user: User)
data class OnboardRequest(val username: String)
