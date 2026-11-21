package com.example.atlasai

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class Msg(
    val fromUser: Boolean,
    val text: String,
    val image: Bitmap? = null,
    val files: List<String> = emptyList()
)

/** Мозг Атласа: решает, что делать с фразой — команда телефона, картинка, Gemini или офлайн-ответ. */
class Brain(
    private val ctx: Context,
    val prefs: Prefs,
    private val voice: Voice,
    private val scope: CoroutineScope
) {
    val messages = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
        private set

    private val actions = Actions(ctx)

    fun online(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun greet() {
        if (messages.isEmpty()) {
            messages.add(Msg(false, String.format(Tr.s("greeting"), prefs.name.ifBlank { if (Tr.ru) "друг" else "friend" })))
        }
    }

    fun clear() {
        voice.stopSpeaking()
        messages.clear()
        greet()
    }

    fun send(text: String, uris: List<Uri>) {
        if (busy || (text.isBlank() && uris.isEmpty())) return
        val history = messages.toList()
        messages.add(Msg(true, text, files = uris.map { fileName(it) }))
        busy = true
        scope.launch {
            val reply = withContext(Dispatchers.IO) { compute(text, uris, history) }
            messages.add(reply)
            busy = false
            if (prefs.speak && reply.text.isNotBlank()) voice.speak(reply.text)
        }
    }

    private fun compute(text: String, uris: List<Uri>, history: List<Msg>): Msg {
        try {
            if (uris.isEmpty()) {
                actions.tryHandle(text)?.let { return Msg(false, it) }
            }
            val on = online()
            val offlineAnswer = { Msg(false, Offline.reply(text, prefs.name)) }

            if (uris.isEmpty() && actions.isImageRequest(text.lowercase())) {
                if (!on) return Msg(false, Tr.s("need_net"))
                if (prefs.key.isBlank()) return Msg(false, Tr.s("no_key"))
                val (caption, bmp) = Gemini.image(prefs.key, text)
                return Msg(false, caption.ifBlank { Tr.s("image_ready") }, bmp)
            }
            if (!on) {
                return if (uris.isNotEmpty()) Msg(false, Tr.s("need_net")) else offlineAnswer()
            }
            if (prefs.key.isBlank()) {
                val base = if (uris.isEmpty()) Offline.reply(text, prefs.name) + "\n\n" else ""
                return Msg(false, base + Tr.s("no_key"))
            }
            val atts = uris.mapNotNull { load(it) }
            return try {
                Msg(false, Gemini.chat(prefs.key, system(), history, text, atts))
            } catch (e: IOException) {
                // связь пропала посреди запроса — продолжаем беседу офлайн
                if (uris.isEmpty()) offlineAnswer() else Msg(false, Tr.s("need_net"))
            }
        } catch (e: Exception) {
            return Msg(false, Tr.s("error") + " " + (e.message ?: ""))
        }
    }

    private fun system(): String {
        val name = prefs.name.ifBlank { "the user" }
        val language = if (Tr.ru) "Russian" else "English"
        return "You are ATLAS, a personal AI assistant in the style of J.A.R.V.I.S. from Iron Man: " +
            "polite, witty, calm, with light dry humour, and extremely capable. " +
            "Always address the user by name: $name. " +
            "Reply ONLY in $language. Keep answers concise and conversational. " +
            "Do not use markdown, asterisks or emoji, because your answers may be read aloud. " +
            "You can use web search for current facts. Phone actions such as calls, texts, flashlight, " +
            "YouTube, timers and image generation are executed by the app itself when the user asks."
    }

    private fun load(u: Uri): Gemini.Attachment? {
        val mime = ctx.contentResolver.getType(u) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return null
        if (bytes.size > 15_000_000) throw ApiException(Tr.s("too_big"))
        return Gemini.Attachment(mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    private fun fileName(u: Uri): String {
        try {
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        } catch (_: Exception) {
        }
        return u.lastPathSegment ?: "file"
    }
}
