package com.samuelcruz.ghidiem

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads text aloud in Vietnamese (falls back to the phone's language when no Vietnamese voice is installed). */
class Speaker(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        if (text.isBlank()) return
        val t = tts ?: TextToSpeech(context, this).also { tts = it }
        if (ready) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "score") else pending = text
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val vi = Locale.forLanguageTag("vi-VN")
        if (t.isLanguageAvailable(vi) >= TextToSpeech.LANG_AVAILABLE) t.language = vi
        ready = true
        pending?.let { t.speak(it, TextToSpeech.QUEUE_FLUSH, null, "score") }
        pending = null
    }

    fun stop() {
        tts?.stop()
    }
}

/** Beeps and buzzes for the referee. */
class Sounds(private val context: Context) {
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    private val vibrator by lazy { context.getSystemService(Vibrator::class.java) }

    /** Time is up: three beeps and a long buzz. */
    fun whistle() {
        tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
    }

    fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
