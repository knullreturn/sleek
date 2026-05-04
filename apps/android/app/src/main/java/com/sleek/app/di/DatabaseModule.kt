package com.sleek.app.di

import android.content.Context
import androidx.room.Room
import com.sleek.app.data.local.db.MessageDao
import com.sleek.app.data.local.db.SleekDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SleekDatabase =
        Room.databaseBuilder(context, SleekDatabase::class.java, "sleek.db")
            .fallbackToDestructiveMigration()   // simple strategy for v1
            .build()

    @Provides
    fun provideMessageDao(db: SleekDatabase): MessageDao = db.messageDao()
}
