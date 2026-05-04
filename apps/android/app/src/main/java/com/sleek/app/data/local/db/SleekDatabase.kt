package com.sleek.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities  = [MessageEntity::class],
    version   = 1,
    exportSchema = false,
)
abstract class SleekDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
