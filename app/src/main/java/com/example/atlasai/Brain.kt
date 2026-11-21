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
import java.util.UUID

data class Msg(
    val fromUser: Boolean,
    val text: String,
    val image: Bitmap? = null,
    val files: List<String> = emptyList(),
    val imageFile: String? = null
)

/**
 * Мозг Атласа. Порядок решений:
 * 1) команды телефона и короткие фразы — локально, без Google;
 * 2) картинки; 3) Google с выбранной моделью (в «Авто» с запасными), поиск только для свежих данных;
 * 4) если Google недоступен — офлайн-ответ и таймер.
 */
class Brain(
    private val ctx: Context,
    val prefs: Prefs,
    private val voice: Voice,
    private val scope: CoroutineScope
) {
    val messages = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
        private set
    var chats by mutableStateOf<List<Chat>>(emptyList())
        private set
    var currentId by mutableStateOf(UUID.randomUUID().toString())
        private set

    private val store = ChatStore(ctx)
    private val actions = Actions(ctx)

    init {
        chats = store.load().sortedByDescending { it.updated }
        chats.firstOrNull()?.let {
            currentId = it.id
            messages.addAll(it.msgs)
        }
    }

    fun online(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun displayName(): String {
        val n = prefs.name.trim()
        return if (n.length >= 2) n else if (Tr.ru) "друг" else "friend"
    }

    fun greet() {
        if (messages.isEmpty()) messages.add(Msg(false, String.format(Tr.s("greeting"), displayName())))
    }

    // ---------- история чатов ----------

    private fun titleFrom(m: Msg) = m.text.trim().take(30).ifBlank { Tr.s("attached") }

    private fun persist() {
        if (messages.none { it.fromUser }) return
        val old = chats.firstOrNull { it.id == currentId }
        val title = old?.title?.takeIf { it.isNotBlank() } ?: titleFrom(messages.first { it.fromUser })
        val snap = Chat(currentId, title, System.currentTimeMillis(), messages.toList())
        chats = listOf(snap) + chats.filter { it.id != snap.id }
        val copy = chats
        scope.launch(Dispatchers.IO) { store.save(copy) }
    }

    fun newChat() {
        if (busy) return
        persist()
        voice.stopSpeaking()
        currentId = UUID.randomUUID().toString()
        messages.clear()
        greet()
    }

    fun open(id: String) {
        if (busy) return
        val c = chats.firstOrNull { it.id == id } ?: return
        if (id != currentId) persist()
        voice.stopSpeaking()
        currentId = id
        messages.clear()
        messages.addAll(chats.first { it.id == id }.msgs)
    }

    fun rename(id: String, title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        chats = chats.map { if (it.id == id) Chat(it.id, t, it.updated, it.msgs) else it }
        val copy = chats
        scope.launch(Dispatchers.IO) { store.save(copy) }
    }

    fun delete(id: String) {
        if (busy) return
        chats = chats.filter { it.id != id }
        val copy = chats
        scope.launch(Dispatchers.IO) { store.save(copy) }
        if (id == currentId) {
            voice.stopSpeaking()
            currentId = UUID.randomUUID().toString()
            messages.clear()
            greet()
        }
    }

    /** Очистить текущий чат (удаляет его из истории). */
    fun clear() = delete(currentId)

    // ---------- отправка ----------

    fun send(text: String, uris: List<Uri>) {
        if (busy || (text.isBlank() && uris.isEmpty())) return
        val history = messages.toList()
        messages.add(Msg(true, text, files = uris.map { fileName(it) }))
        persist()
        busy = true
        val chatId = currentId
        scope.launch {
            val reply = withContext(Dispatchers.IO) { compute(text, uris, history) }
            if (chatId == currentId) messages.add(reply)
            busy = false
            persist()
            if (prefs.speak && reply.text.isNotBlank()) voice.speak(reply.text)
        }
    }

    private fun compute(text: String, uris: List<Uri>, history: List<Msg>): Msg {
        try {
            val name = displayName()
            val neg = Router.hasNegation(text)
            // Короткая ясная команда без отрицания: выполняем сразу, без Google и без лимитов
            if (uris.isEmpty() && !neg && Router.isShort(text)) {
                actions.tryHandle(text)?.let { return Msg(false, it) }
                Offline.match(text, name)?.let { return Msg(false, it) }
            }
            val on = online()
            if (!prefs.internet || !on || prefs.key.isBlank()) return offlineMsg(text, uris, name, on, neg)
            if (uris.isEmpty() && !neg && actions.isImageRequest(text.lowercase())) return image(text, on)
            val atts = uris.mapNotNull { load(it) }
            return google(text, atts, history, name)
        } catch (e: ApiException) {
            return Msg(false, friendly(e))
        } catch (e: Exception) {
            return Msg(false, Tr.s("error") + " " + (e.message ?: ""))
        }
    }

    /** Ответ без Google: интернет выключен вручную, нет сети или нет ключа. */
    private fun offlineMsg(text: String, uris: List<Uri>, name: String, on: Boolean, neg: Boolean): Msg {
        if (uris.isEmpty() && !neg) actions.tryHandle(text)?.let { return Msg(false, it) }
        val reason = when {
            !prefs.internet -> "internet_off"
            !on -> "need_net"
            else -> "no_key"
        }
        if (uris.isNotEmpty() || (!neg && actions.isImageRequest(text.lowercase()))) return Msg(false, Tr.s(reason))
        val base = Offline.reply(text, name, false)
        return Msg(false, if (reason == "no_key") base + "\n\n" + Tr.s("no_key") else base)
    }

    private fun image(text: String, on: Boolean): Msg {
        if (prefs.imageBlocked) return Msg(false, Tr.s("no_image"))
        if (!on) return Msg(false, Tr.s("need_net"))
        if (prefs.key.isBlank()) return Msg(false, Tr.s("no_key"))
        try {
            val (caption, bmp) = Gemini.image(prefs.key, text)
            return Msg(false, caption.ifBlank { Tr.s("image_ready") }, bmp, imageFile = UUID.randomUUID().toString() + ".png")
        } catch (e: ApiException) {
            if (e.code == 429 || e.code == 403 || e.unavailable) {
                prefs.imageBlocked = true
                return Msg(false, Tr.s("no_image"))
            }
            throw e
        } catch (e: IOException) {
            return Msg(false, Tr.s("need_net"))
        }
    }

    /** Перебирает модели выбранной цепочки: выбранная модель или «Авто» со всеми запасными. */
    private fun google(text: String, atts: List<Gemini.Attachment>, history: List<Msg>, name: String): Msg {
        val auto = prefs.model == "auto" || Limits.MODELS.none { it.id == prefs.model }
        val chain = Limits.chain(prefs.model)
        var last: ApiException? = null
        for (id in chain) {
            if (Limits.isDead(id)) continue
            val w = Limits.waitMs(id)
            if (w > 0) {
                // вручную выбранную модель ждём, если ожидание короткое; в «Авто» идём к следующей
                if (!auto && w <= 20_000) Thread.sleep(w) else continue
            }
            try {
                return ask(id, text, atts, history, name)
            } catch (e: ApiException) {
                val msg = (e.message ?: "").lowercase()
                when {
                    e.code == 429 -> {
                        val ms = when {
                            e.unavailable || e.daily -> maxOf(Limits.untilPacificMidnightMs(), 60_000L)
                            e.retryMs > 0 -> e.retryMs + 1000
                            else -> 60_000L
                        }
                        Limits.block(id, ms)
                        last = e
                    }
                    e.code == 404 || msg.contains("no longer available") -> {
                        Limits.kill(id)
                        last = e
                    }
                    e.code >= 500 -> last = e
                    else -> throw e
                }
            } catch (e: IOException) {
                return if (atts.isEmpty()) Msg(false, Offline.reply(text, name, false))
                else Msg(false, Tr.s("need_net"))
            }
        }
        return limited(text, atts.isNotEmpty(), name, last)
    }

    /**
     * Один запрос к модели. По смыслу всей фразы модель сама решает: ответить текстом, вызвать команду
     * телефона или запросить поиск (web_search). Режим поиска «Всегда» идёт сразу в поиск, «Выкл» поиск не предлагает.
     */
    private fun ask(id: String, text: String, atts: List<Gemini.Attachment>, history: List<Msg>, name: String): Msg {
        val searchOk = prefs.searchMode != "off" && !Limits.searchBlocked()
        if (prefs.searchMode == "always" && searchOk) return Msg(false, grounded(id, text, atts, history, name))
        Limits.record(id)
        val r = Gemini.chat(
            prefs.key, id, system(name, searchOk), history, text, atts,
            if (searchOk) Gemini.Tools.ACTIONS_WEB else Gemini.Tools.ACTIONS
        )
        val c = r.call
        if (c != null) {
            if (c.name == "web_search") return Msg(false, grounded(id, text, atts, history, name))
            return Msg(false, actions.run(c.name, c.args))
        }
        return Msg(false, r.text)
    }

    /** Ответ с поиском Google. Если Google поиск не принимает, запоминаем это на час и отвечаем без него. */
    private fun grounded(id: String, text: String, atts: List<Gemini.Attachment>, history: List<Msg>, name: String): String {
        Limits.record(id)
        try {
            return Gemini.chat(prefs.key, id, system(name, true), history, text, atts, Gemini.Tools.SEARCH).text
        } catch (e: ApiException) {
            val s = (e.message ?: "").lowercase()
            if (e.code == 400 || e.code == 403 || s.contains("search") || s.contains("grounding")) {
                Limits.blockSearch(3_600_000L)
                Limits.record(id)
                return Gemini.chat(prefs.key, id, system(name, false), history, text, atts, Gemini.Tools.NONE).text
            }
            throw e
        }
    }

    private fun limited(text: String, hasFiles: Boolean, name: String, last: ApiException?): Msg {
        val wait = Limits.minWait(prefs.model)
        val status = when {
            wait == null -> Tr.s(if (prefs.model == "auto") "all_dead" else "model_dead")
            wait >= 3_600_000L -> Tr.s("limit_day")
            wait > 0 -> String.format(Tr.s("limit_wait"), Limits.fmt(wait))
            last != null && last.code >= 500 -> Tr.s("server_err")
            else -> String.format(Tr.s("limit_wait"), Limits.fmt(60_000L))
        }
        val base = if (hasFiles) "" else Offline.reply(text, name, true) + "\n\n"
        return Msg(false, base + status)
    }

    private fun friendly(e: ApiException): String {
        val msg = (e.message ?: "")
        return when {
            e.code == 0 -> msg
            e.code == 401 || e.code == 403 || (e.code == 400 && msg.contains("api key", true)) -> Tr.s("bad_key")
            e.code == 429 -> Tr.s("limit_generic")
            e.code >= 500 -> Tr.s("server_err")
            else -> Tr.s("error") + " " + msg
        }
    }

    private fun system(name: String, search: Boolean): String {
        val language = if (Tr.ru) "Russian" else "English"
        return "You are ATLAS, a personal AI assistant in the style of J.A.R.V.I.S. from Iron Man: " +
            "polite, witty, calm, with light dry humour, and extremely capable. " +
            "Always address the user by name: $name. " +
            "Reply ONLY in $language. Keep answers concise and conversational. " +
            "Do not use markdown, asterisks or emoji, because your answers may be read aloud. " +
            "Understand the meaning of the user's whole sentence, not single words. " +
            "You can perform phone actions by calling the provided functions, but only when the user clearly asks " +
            "you to do it right now. Never perform an action the user told you not to do. " +
            (if (search) "If you need live information such as weather, news, prices or scores, call web_search; " +
                "never call it if the user asked you not to use the internet. "
            else "You have no live internet access in this reply. If the user asks for live data such as weather, news or prices, say honestly that you cannot check it right now. ")
    }

    private fun load(u: Uri): Gemini.Attachment? {
        val mime = ctx.contentResolver.getType(u) ?: "application/octet-stream"
        val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return null
        if (bytes.size > 15_000_000) throw ApiException(0, Tr.s("too_big"))
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
