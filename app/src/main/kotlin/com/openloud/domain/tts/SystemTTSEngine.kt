package com.openloud.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * System TTS engine that synthesizes to file and plays via MediaPlayer.
 * This guarantees a stable audio session ID for LoudnessEnhancer to attach to.
 */
class SystemTTSEngine(private val context: Context) {

    companion object {
        private const val TAG = "SystemTTSEngine"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var audioSessionId: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private var externalProgressListener: UtteranceProgressListener? = null
    private var currentSpeed: Float = 1.0f

    suspend fun initialize(): Boolean = suspendCoroutine { continuation ->
        // Generate a stable audio session ID for LoudnessEnhancer
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioSessionId = audioManager.generateAudioSessionId()
        Log.d(TAG, "Generated audio session ID: $audioSessionId")

        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                tts?.language = Locale.US
                selectBestVoice()
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(0.92f)

                // Internal listener for synthesize-to-file completion
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { playGeneratedFile(it) }
                    }
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS synthesis error for $utteranceId")
                        utteranceId?.let { externalProgressListener?.onError(it) }
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onError(utteranceId)
                    }
                })
            }
            continuation.resume(isInitialized)
        }
    }

    private fun getTempFile(utteranceId: String): File {
        return File(context.cacheDir, "sys_tts_${utteranceId.hashCode()}.wav")
    }

    private fun playGeneratedFile(utteranceId: String) {
        val file = getTempFile(utteranceId)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Generated file missing or empty: ${file.absolutePath}")
            externalProgressListener?.onError(utteranceId)
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioSessionId(audioSessionId)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()

                // Apply speed via MediaPlayer playback params
                if (currentSpeed != 1.0f) {
                    playbackParams = playbackParams.setSpeed(currentSpeed * 0.92f)
                }

                setOnCompletionListener {
                    file.delete()
                    externalProgressListener?.onDone(utteranceId)
                }
                setOnErrorListener { _, _, _ ->
                    file.delete()
                    externalProgressListener?.onError(utteranceId)
                    true
                }

                externalProgressListener?.onStart(utteranceId)
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing synthesized file: ${e.message}", e)
            file.delete()
            externalProgressListener?.onError(utteranceId)
        }
    }

    private fun selectBestVoice() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedVoice = prefs.getString("selected_voice", null)

        if (savedVoice != null) {
            tts?.voices?.find { it.name == savedVoice }?.let { voice ->
                tts?.voice = voice
                Log.d(TAG, "Using saved voice: ${voice.name}")
                return
            }
        }

        val englishVoices = tts?.voices?.filter {
            it.locale.language == "en" && it.locale.country == "US"
        }?.sortedWith(compareByDescending<Voice> {
            it.quality
        }.thenBy {
            if (it.isNetworkConnectionRequired) 1 else 0
        }.thenBy {
            it.name
        })

        val defaultIndex = 11
        val bestVoice = if (englishVoices != null && englishVoices.size > defaultIndex) {
            englishVoices[defaultIndex]
        } else {
            englishVoices?.firstOrNull()
        }

        if (bestVoice != null) {
            tts?.voice = bestVoice
            Log.d(TAG, "Auto-selected voice #${(englishVoices?.indexOf(bestVoice) ?: -1) + 1}: ${bestVoice.name} (quality=${bestVoice.quality}, locale=${bestVoice.locale}, network=${bestVoice.isNetworkConnectionRequired})")
        } else {
            Log.w(TAG, "No suitable voice found, using default")
        }

        Log.d(TAG, "Available EN voices: ${englishVoices?.take(15)?.mapIndexed { i, v -> "#${i+1} ${v.name} (q=${v.quality}, net=${v.isNetworkConnectionRequired})" }}")
    }

    fun reloadVoice() {
        selectBestVoice()
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        tts?.setSpeechRate(speed * 0.92f)
    }

    /**
     * Synthesize text to file, then play through MediaPlayer for volume boost support.
     */
    fun speakChunk(text: String, utteranceId: String) {
        if (!isInitialized || text.isBlank()) return

        val outFile = getTempFile(utteranceId)

        // Synthesize to file — onDone will trigger playback via MediaPlayer
        val result = tts?.synthesizeToFile(text, null, outFile, utteranceId)

        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "synthesizeToFile failed with result $result")
            externalProgressListener?.onError(utteranceId)
        }
    }

    fun speakSentence(text: String, utteranceId: String) {
        speakChunk(text, utteranceId)
    }

    fun speakWithPause(text: String, utteranceId: String, pauseMs: Int = 500) {
        if (!isInitialized) return
        // For pause, still use direct TTS silent utterance, then synthesize-to-file for text
        tts?.playSilentUtterance(pauseMs.toLong(), TextToSpeech.QUEUE_ADD, "pause_$utteranceId")
        if (text.isNotBlank()) {
            speakChunk(text, utteranceId)
        }
    }

    fun stop() {
        tts?.stop()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.reset()
            } catch (_: Exception) {}
        }
    }

    fun pause() {
        tts?.stop()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.pause()
            } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun setProgressListener(listener: UtteranceProgressListener) {
        externalProgressListener = listener
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true || tts?.isSpeaking == true
    }

    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.filter {
            it.locale.language == "en"
        }?.sortedWith(compareByDescending<Voice> {
            it.quality
        }.thenBy { it.name }) ?: emptyList()
    }

    fun getAudioSessionId(): Int = audioSessionId
}
