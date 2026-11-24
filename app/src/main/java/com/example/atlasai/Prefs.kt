package com.example.atlasai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Настройки пользователя. Значения наблюдаемы для Compose и сохраняются на диск. Ключ Google хранится зашифрованным. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)

    private var _lang by mutableStateOf(sp.getString("lang", "") ?: "")
    private var _name by mutableStateOf(sp.getString("name", "") ?: "")
    private var _key by mutableStateOf(loadKey())
    private var _onboarded by mutableStateOf(sp.getBoolean("onboarded", false))
    private var _voiceInput by mutableStateOf(sp.getBoolean("voiceInput", true))
    private var _speak by mutableStateOf(sp.getBoolean("speak", true))
    private var _internet by mutableStateOf(sp.getBoolean("internet", true))
    private var _search by mutableStateOf(sp.getString("search", "auto") ?: "auto")
    private var _model by mutableStateOf(loadModel())
    private var _len by mutableStateOf(sp.getString("len", "normal") ?: "normal")
    private var _style by mutableStateOf(sp.getString("style", "jarvis") ?: "jarvis")
    private var _rate by mutableStateOf(sp.getFloat("rate", 1f))
    private var _ttsVoice by mutableStateOf(sp.getString("ttsVoice", "") ?: "")
    private var _theme by mutableStateOf(sp.getString("theme", "dark") ?: "dark")
    private var _fontScale by mutableStateOf(sp.getFloat("fontScale", 1f))
    private var _autoDelete by mutableStateOf(sp.getInt("autoDelete", 0))
    private var _streaming by mutableStateOf(sp.getBoolean("streaming", true))
    private var _handsFree by mutableStateOf(sp.getBoolean("handsFree", false))
    private var _appLock by mutableStateOf(sp.getBoolean("appLock", false))
    private var _encryptChats by mutableStateOf(sp.getBoolean("encryptChats", true))

    /** Старые сохранённые модели (2.x) Google закрыл для новых пользователей — переносим на актуальные. */
    private fun loadModel(): String {
        val m = sp.getString("model", "") ?: ""
        return when {
            m.isBlank() -> "gemini-3.8-flash"
            m == "gemini-2.5-flash-lite" -> "gemini-3.5-flash-lite"
            m == "gemini-2.5-pro" -> "gemini-3.1-pro-preview"
            m.startsWith("gemini-2") -> "gemini-3.8-flash"
            else -> m
        }
    }

    /** Ключ хранится только в зашифрованном виде (Android Keystore). Старый открытый ключ переносится. */
    private fun loadKey(): String {
        val enc = sp.getString("key_enc", "") ?: ""
        if (enc.isNotBlank()) return Crypto.dec(enc)
        val old = sp.getString("key", "") ?: ""
        if (old.isNotBlank()) {
            sp.edit().putString("key_enc", Crypto.enc(old)).remove("key").apply()
        }
        return old
    }

    init {
        if (_lang.isNotBlank()) Tr.lang = _lang
    }

    var lang: String
        get() = _lang
        set(v) {
            _lang = v
            sp.edit().putString("lang", v).apply()
            if (v.isNotBlank()) Tr.lang = v
        }

    var name: String
        get() = _name
        set(v) {
            _name = v
            sp.edit().putString("name", v).apply()
        }

    var key: String
        get() = _key
        set(v) {
            _key = v.trim()
            sp.edit().putString("key_enc", Crypto.enc(v.trim())).remove("key").apply()
        }

    var onboarded: Boolean
        get() = _onboarded
        set(v) {
            _onboarded = v
            sp.edit().putBoolean("onboarded", v).apply()
        }

    var voiceInput: Boolean
        get() = _voiceInput
        set(v) {
            _voiceInput = v
            sp.edit().putBoolean("voiceInput", v).apply()
        }

    var speak: Boolean
        get() = _speak
        set(v) {
            _speak = v
            sp.edit().putBoolean("speak", v).apply()
        }

    /** Выкл = Атлас вообще не обращается к Google и отвечает только офлайн. */
    var internet: Boolean
        get() = _internet
        set(v) {
            _internet = v
            sp.edit().putBoolean("internet", v).apply()
        }

    /** auto | always | off */
    var searchMode: String
        get() = _search
        set(v) {
            _search = v
            sp.edit().putString("search", v).apply()
        }

    var model: String
        get() = _model
        set(v) {
            _model = v
            sp.edit().putString("model", v).apply()
        }

    /** short | normal | long */
    var answerLen: String
        get() = _len
        set(v) {
            _len = v
            sp.edit().putString("len", v).apply()
        }

    /** jarvis | friendly | formal */
    var style: String
        get() = _style
        set(v) {
            _style = v
            sp.edit().putString("style", v).apply()
        }

    var ttsRate: Float
        get() = _rate
        set(v) {
            _rate = v
            sp.edit().putFloat("rate", v).apply()
        }

    var ttsVoice: String
        get() = _ttsVoice
        set(v) {
            _ttsVoice = v
            sp.edit().putString("ttsVoice", v).apply()
        }

    /** dark | light | system */
    var theme: String
        get() = _theme
        set(v) {
            _theme = v
            sp.edit().putString("theme", v).apply()
        }

    var fontScale: Float
        get() = _fontScale
        set(v) {
            _fontScale = v
            sp.edit().putFloat("fontScale", v).apply()
        }

    /** 0 = не удалять */
    var autoDeleteDays: Int
        get() = _autoDelete
        set(v) {
            _autoDelete = v
            sp.edit().putInt("autoDelete", v).apply()
        }

    /** Постепенный вывод ответа по мере получения. */
    var streaming: Boolean
        get() = _streaming
        set(v) {
            _streaming = v
            sp.edit().putBoolean("streaming", v).apply()
        }

    /** После ответа Атлас сам снова слушает. */
    var handsFree: Boolean
        get() = _handsFree
        set(v) {
            _handsFree = v
            sp.edit().putBoolean("handsFree", v).apply()
        }

    /** Блокировка приложения отпечатком или PIN телефона. */
    var appLock: Boolean
        get() = _appLock
        set(v) {
            _appLock = v
            sp.edit().putBoolean("appLock", v).apply()
        }

    /** Шифровать историю чатов. */
    var encryptChats: Boolean
        get() = _encryptChats
        set(v) {
            _encryptChats = v
            sp.edit().putBoolean("encryptChats", v).apply()
        }

    /** Использовать локальную модель, если её файл загружен (/local). */
    var useLocal: Boolean
        get() = sp.getBoolean("useLocal", true)
        set(v) {
            sp.edit().putBoolean("useLocal", v).apply()
        }

    var lastChat: String
        get() = sp.getString("lastChat", "") ?: ""
        set(v) {
            sp.edit().putString("lastChat", v).apply()
        }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Сколько запросов к Gemini приложение отправило сегодня (считает только себя). */
    fun requestsToday(): Int =
        if (sp.getString("req_date", "") == today()) sp.getInt("req_count", 0) else 0

    fun bumpRequests() {
        val n = requestsToday() + 1
        sp.edit().putString("req_date", today()).putInt("req_count", n).apply()
    }

    fun resetAll() {
        sp.edit().clear().apply()
        _lang = ""; _name = ""; _key = ""; _onboarded = false
        _voiceInput = true; _speak = true; _internet = true
        _search = "auto"; _model = "gemini-3.8-flash"; _len = "normal"; _style = "jarvis"
        _rate = 1f; _ttsVoice = ""; _theme = "dark"; _fontScale = 1f; _autoDelete = 0
        _streaming = true; _handsFree = false; _appLock = false; _encryptChats = true
    }
}
