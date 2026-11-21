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

    fun resetAll() {
        sp.edit().clear().apply()
        _lang = ""; _name = ""; _key = ""; _onboarded = false
        _voiceInput = true; _speak = true
    }
}
