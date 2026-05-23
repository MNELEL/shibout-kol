package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.models.VoiceProfile

@Database(entities = [VoiceProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceDao(): VoiceDao
}
