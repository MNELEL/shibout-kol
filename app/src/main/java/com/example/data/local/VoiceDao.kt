package com.example.data.local

import androidx.room.*
import com.example.data.models.VoiceProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<VoiceProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VoiceProfile)

    @Delete
    suspend fun deleteProfile(profile: VoiceProfile)
}
