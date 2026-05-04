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
    suspend fun getChats(): Response<ChatsResponse>

    @GET("chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: String): Response<MessagesResponse>

    @GET("chats/{chatId}/pins")
    suspend fun getPinnedMessages(@Path("chatId") chatId: String): Response<MessagesResponse>

    // ── Search ────────────────────────────────────────────────────────────────
    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<User>>

    @POST("chats/dm/{userId}")
    suspend fun createDm(@Path("userId") userId: String): Response<Chat>
}
