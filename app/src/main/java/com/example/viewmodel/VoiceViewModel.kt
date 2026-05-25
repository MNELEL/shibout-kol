package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.*
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.models.VoiceProfile
import com.example.data.repository.VoiceRepository
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class VoiceViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "voice_db"
    ).build()

    private val repository = VoiceRepository(db.voiceDao())

    val profiles: StateFlow<List<VoiceProfile>> = repository.allProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription = _transcription.asStateFlow()

    private var mediaPlayer: android.media.MediaPlayer? = null
    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath: StateFlow<String?> = _playingAudioPath.asStateFlow()

    fun togglePlayProfile(audioPath: String?) {
        if (audioPath.isNullOrEmpty()) return
        
        if (_playingAudioPath.value == audioPath) {
            stopAudio()
        } else {
            playAudio(audioPath)
        }
    }

    private fun playAudio(audioPath: String) {
        viewModelScope.launch {
            try {
                stopAudio()
                if (!java.io.File(audioPath).exists()) {
                    Log.e("VoiceViewModel", "Audio file does not exist: $audioPath")
                    return@launch
                }
                val player = android.media.MediaPlayer().apply {
                    setDataSource(audioPath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _playingAudioPath.value = null
                        stopAudio()
                    }
                }
                mediaPlayer = player
                _playingAudioPath.value = audioPath
            } catch (e: Exception) {
                Log.e("VoiceViewModel", "Failed to play audio path: $audioPath", e)
                _playingAudioPath.value = null
            }
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e("VoiceViewModel", "Error stopping player", e)
        } finally {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            _playingAudioPath.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }

    fun transcribeAudio(audioFile: File, mimeType: String = "audio/mp4") {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                if (!audioFile.exists() || audioFile.length() == 0L) {
                    _transcription.value = "שגיאה: קובץ ההקלטה ריק או לא נמצא."
                    _uiState.value = UiState.Error("קובץ הקלטה לא תקין")
                    return@launch
                }
                
                // Read bytes on IO dispatcher to avoid blocking main thread
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    audioFile.readBytes()
                }
                
                val result = repository.transcribePhonetically(bytes, mimeType)
                _transcription.value = result
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                _transcription.value = "שגיאה בניתוח הקובץ: ${e.localizedMessage}"
                _uiState.value = UiState.Error(e.localizedMessage ?: "שגיאה לא ידועה")
            }
        }
    }

    fun saveVoiceProfile(name: String, transcription: String, tempFile: File) {
        viewModelScope.launch {
            try {
                val extension = tempFile.extension.ifEmpty { "m4a" }
                val targetFile = File(getApplication<Application>().filesDir, "profile_${System.currentTimeMillis()}.$extension")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (tempFile.exists()) {
                        tempFile.copyTo(targetFile, overwrite = true)
                    }
                }
                val profile = VoiceProfile(
                    name = name,
                    transcription = transcription,
                    audioPath = targetFile.absolutePath
                )
                repository.saveProfile(profile)
            } catch (e: Exception) {
                Log.e("VoiceViewModel", "Failed to save voice profile", e)
            }
        }
    }

    fun deleteVoiceProfile(profile: VoiceProfile) {
        viewModelScope.launch {
            try {
                profile.audioPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            file.delete()
                        }
                    }
                }
                repository.deleteProfile(profile)
            } catch (e: Exception) {
                Log.e("VoiceViewModel", "Failed to delete voice profile", e)
            }
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
