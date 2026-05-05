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
            // ⚠️ Write a real Migration before bumping the schema version.
            // fallbackToDestructiveMigration() would silently wipe all cached messages on update.
            // We only allow destructive migration on DOWNGRADE (e.g., dev rollback) — not upgrade.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideMessageDao(db: SleekDatabase): MessageDao = db.messageDao()
}
