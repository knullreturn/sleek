package com.sleek.app.data.remote

import com.sleek.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth (Google OAuth only) ───────────────────────────────────────────────
    @POST("auth/google")
    suspend fun googleAuth(@Body body: GoogleAuthRequest): Response<AuthResponse>

    @POST("auth/onboard")
    suspend fun onboard(@Body body: OnboardRequest): Response<AuthResponse>

    // ── User ──────────────────────────────────────────────────────────────────
    @GET("users/me")
    suspend fun getMe(): Response<User>

    @PUT("users/me")
    suspend fun updateMe(@Body body: Map<String, String>): Response<User>

    // ── Chats ─────────────────────────────────────────────────────────────────
    @GET("chats")
    suspend fun getChats(): Response<List<Chat>>

    @GET("chats/{id}/messages")
    suspend fun getMessages(@Path("id") chatId: String): Response<MessagesResponse>

    @GET("chats/{id}/pins")
    suspend fun getPinnedMessages(@Path("id") chatId: String): Response<MessagesResponse>

    // ── Search & DM creation ──────────────────────────────────────────────────
    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<User>>

    @POST("chats")
    suspend fun createDm(@Body body: Map<String, String>): Response<Chat>

    @PUT("chats/{id}/read")
    suspend fun markChatRead(@Path("id") chatId: String): Response<Unit>

    // ── Notifications ─────────────────────────────────────────────────────────
    @POST("users/fcm-token")
    suspend fun saveFcmToken(@Body body: Map<String, String>): Response<Unit>
}
