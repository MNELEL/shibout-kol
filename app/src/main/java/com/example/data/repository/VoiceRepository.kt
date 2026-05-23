package com.example.data.repository

import com.example.BuildConfig
import com.example.api.*
import com.example.data.local.VoiceDao
import com.example.data.models.VoiceProfile
import kotlinx.coroutines.flow.Flow
import android.util.Base64

class VoiceRepository(private val voiceDao: VoiceDao) {

    val allProfiles: Flow<List<VoiceProfile>> = voiceDao.getAllProfiles()

    suspend fun saveProfile(profile: VoiceProfile) {
        voiceDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profile: VoiceProfile) {
        voiceDao.deleteProfile(profile)
    }

    suspend fun transcribePhonetically(audioBytes: ByteArray): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        
        val request = GenerateContentRequest(
            contents = listOf(Content(
                parts = listOf(
                    Part(text = "Transcribe exactly what you hear in this audio phonetically. " +
                            "Do NOT translate. Do NOT correct. " +
                            "Use IPA layout if possible or English phonetic spelling. " +
                            "Output only the transcription."),
                    Part(inlineData = InlineData(mimeType = "audio/wav", data = base64Audio))
                )
            )),
            generationConfig = GenerationConfig(temperature = 0.0f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No result"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
