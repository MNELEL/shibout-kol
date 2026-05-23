package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "voice_profiles")
@Serializable
data class VoiceProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val transcription: String? = null,
    val voiceCharacteristics: String? = null, // JSON result of analysis
    val audioPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
