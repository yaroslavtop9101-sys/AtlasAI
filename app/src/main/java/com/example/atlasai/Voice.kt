package com.example.atlasai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Озвучка ответов (TTS) и распознавание речи (STT). Вызывать с главного потока. */
class Voice(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            applyLang()
        }
    }

    private fun applyLang() {
        if (ready) tts?.language = if (Tr.ru) Locale("ru", "RU") else Locale.US
    }

    private fun clean(t: String): String = t
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[*#_`>~]"), "")
        .trim()

    fun speak(text: String) {
        if (!ready) return
        applyLang()
        tts?.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, "atlas")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun listen(onPartial: (String) -> Unit, onResult: (String) -> Unit, onFail: (String) -> Unit) {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onFail(Tr.s("no_stt"))
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                onFail(Tr.s("stt_err"))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) onPartial(t)
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onFail(Tr.s("stt_err")) else onResult(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Tr.ru) "ru-RU" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        r.startListening(i)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun shutdown() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
