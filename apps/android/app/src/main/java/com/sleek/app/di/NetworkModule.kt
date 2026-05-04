package com.sleek.app.di

import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.NetworkClient
import com.sleek.app.data.remote.SocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkClient(tokenDataStore: TokenDataStore): NetworkClient =
        NetworkClient(tokenDataStore)

    @Provides
    @Singleton
    fun provideApiService(networkClient: NetworkClient): ApiService =
        networkClient.apiService

    @Provides
    @Singleton
    fun provideSocketManager(): SocketManager = SocketManager()
}
