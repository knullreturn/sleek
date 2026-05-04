package com.sleek.app.data.remote

import com.sleek.app.BuildConfig
import com.sleek.app.data.local.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkClient @Inject constructor(
    private val tokenDataStore: TokenDataStore,
) {
    val apiService: ApiService by lazy { buildRetrofit().create(ApiService::class.java) }

    private fun buildRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { tokenDataStore.token.first() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("${BuildConfig.API_URL}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
