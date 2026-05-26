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

    suspend fun transcribePhonetically(audioBytes: ByteArray, mimeType: String = "audio/mp4"): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        if (apiKey.isNullOrEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            return "שגיאה: מפתח ה-API (GEMINI_API_KEY) אינו מוגדר. אנא פתח את פאנל ה-Secrets (סמליל המפתח בסרגל הצידי הימני של AI Studio), הוסף מפתח תחת השם 'GEMINI_API_KEY', והפעל מחדש את האפליקציה."
        }

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        
        val request = GenerateContentRequest(
            contents = listOf(Content(
                parts = listOf(
                    Part(text = "Transcribe exactly what you hear in this audio phonetically. " +
                            "Do NOT translate. Do NOT correct. " +
                            "Use IPA layout if possible or English phonetic spelling. " +
                            "Output only the transcription."),
                    Part(inlineData = InlineData(mimeType = mimeType, data = base64Audio))
                )
            )),
            generationConfig = GenerationConfig(temperature = 0.0f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No result"
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            android.util.Log.e("VoiceRepository", "Gemini API HTTP Error $code: $errorBody", e)
            if (code == 403) {
                "שגיאה 403 (גישה אסורה/Forbidden): מפתח ה-API שהוזן אינו בתוקף, או שאין לו הרשאה עבור מודל זה. אנא ודא שהמפתח בפאנל ה-Secrets ב-AI Studio תקין ורענן את האוגר."
            } else {
                "שגיאת שרת Gemini (קוד $code): $errorBody"
            }
        } catch (t: Throwable) {
            android.util.Log.e("VoiceRepository", "Gemini API General Error", t)
            "שגיאה בהתקשרות ל-Gemini API: ${t.localizedMessage ?: t.message ?: t.toString()}"
        }
    }
}
