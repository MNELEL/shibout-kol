package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.*
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.models.VoiceProfile
import com.example.data.repository.VoiceRepository
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

    fun transcribeAudio(audioFile: File) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val bytes = audioFile.readBytes()
            val result = repository.transcribePhonetically(bytes)
            _transcription.value = result
            _uiState.value = UiState.Success
        }
    }

    fun saveVoiceProfile(name: String, transcription: String, audioPath: String) {
        viewModelScope.launch {
            val profile = VoiceProfile(
                name = name,
                transcription = transcription,
                audioPath = audioPath
            )
            repository.saveProfile(profile)
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
