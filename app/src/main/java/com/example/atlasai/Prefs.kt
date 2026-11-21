package com.example.atlasai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Настройки пользователя. Значения наблюдаемы для Compose и сохраняются на диск. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)

    private var _lang by mutableStateOf(sp.getString("lang", "") ?: "")
    private var _name by mutableStateOf(sp.getString("name", "") ?: "")
    private var _key by mutableStateOf(sp.getString("key", "") ?: "")
    private var _onboarded by mutableStateOf(sp.getBoolean("onboarded", false))
    private var _voiceInput by mutableStateOf(sp.getBoolean("voiceInput", true))
    private var _speak by mutableStateOf(sp.getBoolean("speak", true))
    private var _model by mutableStateOf(sp.getString("model", "auto") ?: "auto")
    private var _imageBlocked by mutableStateOf(sp.getBoolean("imageBlocked", false))
    private var _internet by mutableStateOf(sp.getBoolean("internet", true))
    private var _searchMode by mutableStateOf(sp.getString("searchMode", "auto") ?: "auto")

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
            if (v.trim() != _key) imageBlocked = false
            _key = v.trim()
            sp.edit().putString("key", v.trim()).apply()
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

    var model: String
        get() = _model
        set(v) {
            _model = v
            sp.edit().putString("model", v).apply()
        }

    /** Google не пустил к картинкам с этим ключом — больше не пробуем, пока ключ не сменится. */
    var imageBlocked: Boolean
        get() = _imageBlocked
        set(v) {
            _imageBlocked = v
            sp.edit().putBoolean("imageBlocked", v).apply()
        }

    /** Ручной выключатель интернета для Атласа: выкл = Google не используется вообще. */
    var internet: Boolean
        get() = _internet
        set(v) {
            _internet = v
            sp.edit().putBoolean("internet", v).apply()
        }

    /** auto: модель решает сама; always: всегда с поиском; off: без поиска. */
    var searchMode: String
        get() = _searchMode
        set(v) {
            _searchMode = v
            sp.edit().putString("searchMode", v).apply()
        }

    fun resetAll() {
        sp.edit().clear().apply()
        _lang = ""; _name = ""; _key = ""; _onboarded = false
        _voiceInput = true; _speak = true; _model = "auto"; _imageBlocked = false; _internet = true; _searchMode = "auto"
    }
}
