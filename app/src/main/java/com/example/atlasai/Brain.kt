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

    val chats = ChatStore(ctx)
    val memory = MemoryStore(ctx)

    /** Номер текущего чата. */
    var chatId: String = ""
        private set

    private val actions = Actions(ctx)

    init {
        if (prefs.autoDeleteDays > 0) chats.purge(prefs.autoDeleteDays, prefs.lastChat)
        val last = prefs.lastChat
        val loaded = if (last.isNotBlank()) chats.load(last) else null
        if (loaded != null) {
            chatId = last
            messages.addAll(loaded)
        } else {
            chatId = chats.newId()
            prefs.lastChat = chatId
        }
    }

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

    private fun persist() {
        chats.save(chatId, messages.toList())
        prefs.lastChat = chatId
    }

    /** Сообщение от самого приложения (ответ на слэш-команду): без озвучки и без запроса к ИИ. */
    fun note(text: String) {
        messages.add(Msg(false, text))
        persist()
    }

    fun newChat() {
        voice.stopSpeaking()
        persist()
        chatId = chats.newId()
        prefs.lastChat = chatId
        messages.clear()
        greet()
    }

    fun openChat(id: String) {
        if (id == chatId) return
        val l = chats.load(id) ?: return
        voice.stopSpeaking()
        persist()
        chatId = id
        prefs.lastChat = id
        messages.clear()
        messages.addAll(l)
    }

    fun deleteChat(id: String) {
        chats.delete(id)
        if (id == chatId) {
            voice.stopSpeaking()
            chatId = chats.newId()
            prefs.lastChat = chatId
            messages.clear()
            greet()
        }
    }

    fun clear() {
        voice.stopSpeaking()
        messages.clear()
        greet()
        persist()
    }

    /** Полный сброс: чаты, память. Настройки сбрасывает сам Prefs. */
    fun wipeAll() {
        voice.stopSpeaking()
        chats.deleteAll()
        memory.clear()
        messages.clear()
        chatId = chats.newId()
    }

    /** Повторить последний ответ (если исходный вопрос был без вложений). */
    fun regenerate() {
        if (busy) return
        val i = messages.indexOfLast { it.fromUser }
        if (i < 0) return
        val m = messages[i]
        if (m.files.isNotEmpty()) return
        resend(i, m.text)
    }

    /** Изменить своё сообщение: всё после него удаляется, Атлас отвечает заново. */
    fun editAndSend(index: Int, newText: String) {
        if (busy) return
        val m = messages.getOrNull(index) ?: return
        if (!m.fromUser || m.files.isNotEmpty()) return
        resend(index, newText)
    }

    private fun resend(index: Int, text: String) {
        while (messages.size > index) messages.removeAt(messages.size - 1)
        send(text, emptyList())
    }

    fun send(text: String, uris: List<Uri>) {
        if (busy || (text.isBlank() && uris.isEmpty())) return

        // «запомни ...» — сохраняем в память без запроса к ИИ
        if (uris.isEmpty()) {
            val mm = Regex(
                "^(?:atlas,?\\s*|атлас,?\\s*)?(?:запомни|remember)(?:\\s+that|\\s+что)?[,:]?\\s+(.+)$",
                RegexOption.IGNORE_CASE
            ).find(text.trim())
            if (mm != null) {
                memory.add(mm.groupValues[1])
                messages.add(Msg(true, text))
                val reply = Msg(false, Tr.s("mem_saved"))
                messages.add(reply)
                persist()
                if (prefs.speak) voice.speak(reply.text)
                return
            }
        }

        val history = messages.toList()
        val target = chatId
        messages.add(Msg(true, text, files = uris.map { fileName(it) }))
        persist()
        busy = true
        scope.launch {
            val reply = withContext(Dispatchers.IO) { compute(text, uris, history) }
            if (chatId == target) {
                messages.add(reply)
                persist()
            } else {
                // пока Атлас думал, вы перешли в другой чат — ответ кладём в тот, где был вопрос
                val l = chats.load(target)?.toMutableList()
                if (l != null) {
                    l.add(reply)
                    chats.save(target, l)
                }
            }
            busy = false
            if (chatId == target && prefs.speak && reply.text.isNotBlank()) voice.speak(reply.text)
        }
    }

    private fun compute(text: String, uris: List<Uri>, history: List<Msg>): Msg {
        try {
            if (uris.isEmpty()) {
                actions.tryHandle(text)?.let { return Msg(false, it) }
            }
            val on = online() && prefs.internet
            val offlineAnswer = { Msg(false, Offline.reply(text, prefs.name)) }
            val noNet = { Msg(false, Tr.s(if (prefs.internet) "need_net" else "net_off")) }

            if (uris.isEmpty() && actions.isImageRequest(text.lowercase())) {
                if (!on) return noNet()
                if (prefs.key.isBlank()) return Msg(false, Tr.s("no_key"))
                prefs.bumpRequests()
                val (caption, bmp) = Gemini.image(prefs.key, text)
                return Msg(false, caption.ifBlank { Tr.s("image_ready") }, bmp)
            }
            if (!on) {
                return if (uris.isNotEmpty()) noNet() else offlineAnswer()
            }
            if (prefs.key.isBlank()) {
                val base = if (uris.isEmpty()) Offline.reply(text, prefs.name) + "\n\n" else ""
                return Msg(false, base + Tr.s("no_key"))
            }
            val atts = uris.mapNotNull { load(it) }
            prefs.bumpRequests()
            return try {
                Msg(
                    false,
                    Gemini.chat(
                        prefs.key, prefs.model, system(), history, text, atts,
                        prefs.searchMode != "off"
                    )
                )
            } catch (e: IOException) {
                // связь пропала посреди запроса — продолжаем беседу офлайн
                if (uris.isEmpty()) offlineAnswer() else noNet()
            }
        } catch (e: Exception) {
            return Msg(false, Tr.s("error") + " " + (e.message ?: ""))
        }
    }

    private fun system(): String {
        val name = prefs.name.ifBlank { "the user" }
        val language = if (Tr.ru) "Russian" else "English"
        val len = when (prefs.answerLen) {
            "short" -> "Keep answers very short: one or two sentences."
            "long" -> "Give detailed, thorough answers when the topic needs it."
            else -> "Keep answers concise and conversational."
        }
        val style = when (prefs.style) {
            "friendly" -> "Be warm, friendly and casual."
            "formal" -> "Be strictly formal and businesslike, without jokes."
            else -> "Be polite, witty, calm, with light dry humour."
        }
        val search = if (prefs.searchMode == "always") {
            "Always use Google Search before answering factual questions."
        } else {
            "You can use web search for current facts."
        }
        val mem = if (memory.items.isEmpty()) "" else
            " Facts the user asked you to remember: " + memory.items.joinToString("; ") + ". Use them when relevant."
        return "You are ATLAS, a personal AI assistant in the style of J.A.R.V.I.S. from Iron Man, and extremely capable. " +
            "$style Always address the user by name: $name. " +
            "Reply ONLY in $language. $len " +
            "Do not use markdown, asterisks or emoji, because your answers may be read aloud. " +
            "Exception: when the user asks for a table, answer with a markdown table (pipes and a separator row) and one short sentence. " +
            "$search Phone actions such as calls, texts, flashlight, " +
            "YouTube, timers and image generation are executed by the app itself when the user asks." + mem
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
