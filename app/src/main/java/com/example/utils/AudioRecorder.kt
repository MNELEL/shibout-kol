package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    
    var currentMimeType: String = "audio/mp4"
        private set

    fun startRecording(fileName: String): Boolean {
        // We will try multiple configurations sequentially until one succeeds.
        // This makes recording bulletproof across different emulators and physical devices.
        val configurations = listOf(
            // Config 1: S+ App Context, MIC source, AAC, .m4a
            Triple(true, MediaRecorder.AudioSource.MIC, MediaRecorder.OutputFormat.MPEG_4 to MediaRecorder.AudioEncoder.AAC),
            // Config 2: Legacy constructor, MIC source, AAC, .m4a
            Triple(false, MediaRecorder.AudioSource.MIC, MediaRecorder.OutputFormat.MPEG_4 to MediaRecorder.AudioEncoder.AAC),
            // Config 3: Legacy constructor, DEFAULT source (extremely compatible), AAC, .m4a
            Triple(false, MediaRecorder.AudioSource.DEFAULT, MediaRecorder.OutputFormat.MPEG_4 to MediaRecorder.AudioEncoder.AAC),
            // Config 4: Legacy constructor, VOICE_RECOGNITION source, AAC, .m4a
            Triple(false, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.OutputFormat.MPEG_4 to MediaRecorder.AudioEncoder.AAC),
            // Config 5: Legacy constructor, MIC source, AMR_NB, .3gp (ultra compatible)
            Triple(false, MediaRecorder.AudioSource.MIC, MediaRecorder.OutputFormat.THREE_GPP to MediaRecorder.AudioEncoder.AMR_NB),
            // Config 6: Legacy constructor, DEFAULT source, AMR_NB, .3gp
            Triple(false, MediaRecorder.AudioSource.DEFAULT, MediaRecorder.OutputFormat.THREE_GPP to MediaRecorder.AudioEncoder.AMR_NB)
        )

        for (config in configurations) {
            val useSConstructor = config.first
            val source = config.second
            val formatEncoder = config.third
            val format = formatEncoder.first
            val encoder = formatEncoder.second
            val isM4a = format == MediaRecorder.OutputFormat.MPEG_4
            val ext = if (isM4a) "m4a" else "3gp"

            try {
                outputFile = File(context.cacheDir, "$fileName.$ext")
                if (outputFile?.exists() == true) {
                    outputFile?.delete()
                }

                @Suppress("DEPRECATION")
                val recorder = if (useSConstructor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }

                mediaRecorder = recorder.apply {
                    setAudioSource(source)
                    setOutputFormat(format)
                    setAudioEncoder(encoder)
                    setOutputFile(outputFile!!.absolutePath)
                    prepare()
                    start()
                }

                currentMimeType = if (isM4a) "audio/mp4" else "audio/3gpp"
                Log.d("AudioRecorder", "Recording started successfully with source=$source, format=$format, encoder=$encoder, ext=$ext")
                return true
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed configuration: source=$source, format=$format, encoder=$encoder", e)
                mediaRecorder?.release()
                mediaRecorder = null
            }
        }

        Log.e("AudioRecorder", "All media recording configurations failed.")
        return false
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop mediaRecorder (likely too short recording)", e)
        } finally {
            try {
                mediaRecorder?.release()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to release mediaRecorder", e)
            }
            mediaRecorder = null
        }

        // Extremely robust: if file exists and has content, return it anyway!
        return if (outputFile?.exists() == true && outputFile!!.length() > 0) {
            Log.d("AudioRecorder", "Extracted recorded audio file with bytes: ${outputFile!!.length()}")
            outputFile
        } else {
            Log.e("AudioRecorder", "Recorded output file is empty or does not exist.")
            null
        }
    }
}
