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
    /** Имена файлов, которые пользователь приложил к сообщению. */
    val files: List<String> = emptyList(),
    /** Источники, если ответ получен с поиском. */
    val sources: List<WebSource> = emptyList(),
    /** Файлы, которые создал Атлас (ZIP, текст, CSV, PDF...). */
    val outFiles: List<String> = emptyList(),
    /** Модель, если ответила не выбранная, а запасная. */
    val model: String = "",
    /** Служебное сообщение или ошибка: не озвучивается и не уходит в историю для модели. */
    val silent: Boolean = false,
    /** Действие, ожидающее подтверждения (не сохраняется). */
    val pending: Pending? = null
)

/** Мозг Атласа: модель сама решает, искать ли в сети, звонить, ставить таймер, создавать файлы и т.д. */
class Brain(
    private val ctx: Context,
    val prefs: Prefs,
    private val voice: Voice,
    private val scope: CoroutineScope
) {
    val messages = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
        private set

    /** Текст ответа, который приходит по частям. */
    var streamText by mutableStateOf("")
        private set

    /** Звонок или SMS, ожидающие подтверждения. */
    var pending by mutableStateOf<Pending?>(null)
        private set

    val chats = ChatStore(ctx, prefs)
    val memory = MemoryStore(ctx)
    val creator = Creator(ctx)

    /** Номер текущего чата. */
    var chatId: String = ""
        private set

    private val actions = Actions(ctx)
    private val tools = Tools(prefs, actions, creator)

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
        messages.add(Msg(false, text, silent = true))
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

    /** Полный сброс: чаты, память, созданные файлы. Настройки сбрасывает сам Prefs. */
    fun wipeAll() {
        voice.stopSpeaking()
        chats.deleteAll()
        memory.clear()
        creator.deleteAll()
        messages.clear()
        chatId = chats.newId()
    }

    fun confirmPending() {
        val p = pending ?: return
        pending = null
        val r = try {
            p.run()
        } catch (e: Exception) {
            Tr.s("error") + " " + (e.message ?: "")
        }
        note(r)
    }

    fun cancelPending() {
        pending = null
        note(Tr.s("cancelled"))
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
        streamText = ""
        scope.launch {
            val reply = withContext(Dispatchers.IO) { compute(text, uris, history) }
            streamText = ""
            if (chatId == target) {
                messages.add(reply)
                persist()
                reply.pending?.let { pending = it }
            } else {
                // пока Атлас думал, вы перешли в другой чат: ответ кладём в тот, где был вопрос
                val l = chats.load(target)?.toMutableList()
                if (l != null) {
                    l.add(reply)
                    chats.save(target, l)
                }
            }
            busy = false
            if (chatId == target && prefs.speak && !reply.silent && reply.text.isNotBlank()) {
                voice.speak(reply.text)
            }
        }
    }

    private fun partial(): ((String) -> Unit)? {
        if (!prefs.streaming) return null
        return { s ->
            // служебный сигнал [[SEARCH]] показывать не нужно
            if (!s.trimStart().startsWith("[")) {
                scope.launch { streamText = s }
            }
        }
    }

    private fun compute(text: String, uris: List<Uri>, history: List<Msg>): Msg {
        val on = online() && prefs.internet
        val modelPath = on && prefs.key.isNotBlank()
        val noNet = { Msg(false, Tr.s(if (prefs.internet) "need_net" else "net_off"), silent = true) }
        val offlineAnswer = { Msg(false, Offline.reply(text, prefs.name)) }
        try {
            if (!modelPath) {
                // без интернета или без ключа работают простые правила (с защитой от «не», «без», «don't»)
                if (uris.isEmpty()) {
                    actions.tryHandle(text)?.let { return Msg(false, it) }
                    if (actions.isImageRequest(text.lowercase())) {
                        return if (!on) noNet() else Msg(false, Tr.s("no_key"))
                    }
                }
                if (!on) return if (uris.isNotEmpty()) noNet() else offlineAnswer()
                val base = if (uris.isEmpty()) Offline.reply(text, prefs.name) + "\n\n" else ""
                return Msg(false, base + Tr.s("no_key"))
            }
            val atts = uris.mapNotNull { load(it) }
            return try {
                buildReply(askModel(text, atts, history))
            } catch (e: IOException) {
                // связь пропала посреди запроса: простые команды и беседа продолжаются офлайн
                if (uris.isEmpty()) {
                    actions.tryHandle(text)?.let { return Msg(false, it) }
                    offlineAnswer()
                } else {
                    noNet()
                }
            } catch (e: ApiException) {
                // лимит или ошибка Google: простые команды телефона всё равно сработают
                if (uris.isEmpty()) actions.tryHandle(text)?.let { return Msg(false, it) }
                Msg(false, friendly(e), silent = true)
            }
        } catch (e: ApiException) {
            return Msg(false, friendly(e), silent = true)
        } catch (e: Exception) {
            return Msg(false, Tr.s("error") + " " + (e.message ?: ""), silent = true)
        }
    }

    /** Спрашивает выбранную модель; если у неё лимит или она недоступна, пробует запасные. */
    private fun askModel(text: String, atts: List<Gemini.Attachment>, history: List<Msg>): Gemini.Result {
        val order = Models.fallbackOrder(prefs.model)
        var last: ApiException? = null
        val log = StringBuilder()
        for (m in order) {
            try {
                return attempt(m, text, atts, history)
            } catch (e: ApiException) {
                last = e
                log.append("• ").append(m).append(": ")
                    .append(e.message.orEmpty().replace("\n", " ").take(220)).append("\n")
                val msg = e.message.orEmpty().lowercase()
                val retryable = e.code == 429 || e.code == 404 || e.code == 500 || e.code == 503 ||
                    msg.contains("no longer available")
                if (!retryable) throw e
            }
        }
        val l = last ?: ApiException("error")
        throw ApiException(l.message.orEmpty(), l.code, log.toString().trim())
    }

    private fun attempt(
        model: String,
        text: String,
        atts: List<Gemini.Attachment>,
        history: List<Msg>
    ): Gemini.Result {
        val withSearch = prefs.searchMode != "off"
        prefs.bumpRequests()
        if (withSearch && Gemini.combined) {
            try {
                // поиск и функции в одном запросе: модель сама решает, что нужно
                return Gemini.respond(prefs.key, model, system(false), history, text, atts, true, true, partial())
            } catch (e: ApiException) {
                val m = e.message.orEmpty().lowercase()
                val toolProblem = e.code == 400 &&
                    (m.contains("tool") || m.contains("function") || m.contains("search") || m.contains("combin"))
                if (!toolProblem) throw e
                Gemini.combined = false
            }
        }
        // запасной путь: сначала функции, а если нужен поиск, модель отвечает [[SEARCH]]
        val r = Gemini.respond(prefs.key, model, system(withSearch), history, text, atts, false, true, partial())
        if (withSearch && r.calls.isEmpty() && r.text.contains("[[SEARCH]]")) {
            return Gemini.respond(prefs.key, model, system(false), history, text, atts, true, false, partial())
        }
        return r
    }

    /** Выполняет вызовы функций, которые выбрала модель, и собирает ответ. */
    private fun buildReply(r: Gemini.Result): Msg {
        val parts = mutableListOf<String>()
        var image: Bitmap? = null
        val outFiles = mutableListOf<String>()
        var pend: Pending? = null
        val cleaned = r.text.replace("[[SEARCH]]", "").trim()
        if (cleaned.isNotBlank()) parts.add(cleaned)
        for (c in r.calls) {
            val res = tools.run(c)
            if (res.text.isNotBlank()) parts.add(res.text)
            if (res.image != null) image = res.image
            outFiles.addAll(res.files)
            if (res.pending != null) pend = res.pending
        }
        if (parts.isEmpty() && image == null && outFiles.isEmpty()) parts.add(Tr.s("tool_bad"))
        return Msg(
            false, parts.joinToString("\n\n"), image, emptyList(), r.sources, outFiles,
            if (r.model != prefs.model) r.model else "", false, pend
        )
    }

    /** Google иногда пишет, через сколько повторить («retry in 12h55m45s»). */
    private fun retryHint(m: String): String {
        val t = Regex("retry in ([0-9hm]*[0-9.]+s)").find(m)?.groupValues?.get(1) ?: return ""
        return " " + String.format(Tr.s("retry_in"), t.replace(Regex("\\.[0-9]+"), ""))
    }

    /** Что именно ответил Google по каждой модели: так видно, какой лимит исчерпан. */
    private fun detailsOf(e: ApiException): String =
        if (e.details.isBlank()) "" else "\n\n" + Tr.s("details") + ":\n" + e.details

    private fun friendly(e: ApiException): String {
        val m = e.message.orEmpty()
        val low = m.lowercase()
        return when {
            e.code == 429 && low.contains("limit: 0") -> Tr.s("err_free") + retryHint(m) + detailsOf(e)
            e.code == 429 || low.contains("quota") -> Tr.s("err_quota") + retryHint(m) + detailsOf(e)
            e.code == 401 || e.code == 403 || low.contains("api key") -> Tr.s("err_key")
            low.contains("mime") -> Tr.s("err_mime")
            low.contains("no longer available") || e.code == 404 -> Tr.s("err_model")
            else -> Tr.s("error") + " " + m.take(200)
        }
    }

    private fun system(sentinel: Boolean): String {
        val name = prefs.name.ifBlank { "the user" }
        val language = if (Tr.ru) "Russian" else "English"
        val len = when (prefs.answerLen) {
            "short" -> "Keep answers very short: one or two sentences."
            "long" -> "Give detailed, thorough answers when the topic needs it."
            else -> "Keep answers concise and conversational."
        }
        val traits = Persona.prompts(Persona.traits, prefs.traits)
        val speech = Persona.prompts(Persona.styles, prefs.speech)
        val style = if (traits.isEmpty() && speech.isEmpty()) {
            "Be polite, witty, calm, with light dry humour."
        } else {
            (if (traits.isNotEmpty()) "Your character: " + traits.joinToString(", ") + ". " else "") +
                (if (speech.isNotEmpty()) "Your speech style: " + speech.joinToString(", ") + "." else "")
        }
        val search = when {
            prefs.searchMode == "off" ->
                "Web search is disabled: answer from your own knowledge and say so when you are unsure about recent facts."
            sentinel && prefs.searchMode == "always" ->
                "For any factual or time-sensitive question reply with exactly [[SEARCH]] and nothing else."
            sentinel ->
                "If you need up-to-date information from the internet to answer well, reply with exactly [[SEARCH]] and nothing else."
            prefs.searchMode == "always" -> "Always use Google Search before answering factual questions."
            else -> "Use Google Search when you need current or uncertain facts."
        }
        val mem = if (memory.items.isEmpty()) "" else
            " Facts the user asked you to remember: " + memory.items.joinToString("; ") + ". Use them when relevant."
        return "You are ATLAS, a personal AI assistant in the style of J.A.R.V.I.S. from Iron Man, and extremely capable. " +
            "$style Always address the user by name: $name. " +
            "Reply ONLY in $language. $len " +
            "Do not use markdown, asterisks or emoji, because your answers may be read aloud. " +
            "Exception: when the user asks for a table, answer with a markdown table (pipes and a separator row) and one short sentence. " +
            "Honesty: never invent facts, numbers, quotes, links or sources. If you do not know or are not sure, say so plainly " +
            "and suggest how to find out; label guesses as guesses. " +
            "$search " +
            "Phone actions, pictures and files are done by calling the provided functions, only when the user clearly asks for " +
            "them now. Never call them when the user negates the action (for example \"don't turn on the flashlight\") or only " +
            "asks a question about it. After a function call do not repeat its details, the app confirms it itself. " +
            "With create_files you can make text-based files (code, text, CSV, JSON, HTML, Markdown, PDF) and ZIP archives; " +
            "you cannot build APK files or other binaries, say so if asked. You cannot install apps, but you can open Google Play search." + mem
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
