package com.example.atlasai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Озвучка ответов (TTS) и распознавание речи (STT). Вызывать с главного потока. */
class Voice(private val ctx: Context, private val prefs: Prefs) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var counter = 0
    private var currentId = ""

    /** true, пока Атлас говорит. */
    var speaking by mutableStateOf(false)
        private set

    /** Текст, который сейчас озвучивается (чтобы кнопка под нужным ответом показала «стоп»). */
    var speakingText by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        finish(utteranceId)
                    }
                })
            }
            applyLang()
        }
    }

    private fun finish(id: String?) {
        main.post {
            if (id == currentId) {
                speaking = false
                speakingText = null
            }
        }
    }

    private fun applyLang() {
        val t = tts ?: return
        if (!ready) return
        val lang = if (Tr.ru) "ru" else "en"
        t.language = if (Tr.ru) Locale("ru", "RU") else Locale.US
        t.setSpeechRate(prefs.ttsRate)
        val vn = prefs.ttsVoice
        if (vn.isNotBlank()) {
            t.voices?.firstOrNull { it.name == vn && it.locale.language == lang }?.let { t.voice = it }
        }
    }

    /** Названия голосов Android для текущего языка. */
    fun voiceNames(): List<String> {
        val lang = if (Tr.ru) "ru" else "en"
        return try {
            tts?.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Убираем ссылки, значки разметки и строки таблиц (таблицу не читаем вслух). */
    private fun clean(t: String): String {
        val lines = t.lines()
        val hasTable = lines.any { it.trim().startsWith("|") }
        val body = lines.filter { !it.trim().startsWith("|") }.joinToString("\n")
        val out = body
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[*#_`>~]"), "")
            .trim()
        return if (hasTable) (out + " " + Tr.s("table_spoken")).trim() else out
    }

    fun speak(text: String) {
        if (!ready) return
        applyLang()
        val say = clean(text).take(3900)
        if (say.isBlank()) return
        counter++
        currentId = "atlas$counter"
        speakingText = text
        speaking = true
        tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null, currentId)
    }

    fun stopSpeaking() {
        currentId = ""
        speaking = false
        speakingText = null
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
