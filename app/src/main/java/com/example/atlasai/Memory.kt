package com.example.atlasai

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** Память Атласа: факты, которые попросил запомнить пользователь. Хранятся зашифрованно. */
class MemoryStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("atlas_mem", Context.MODE_PRIVATE)
    val items = mutableStateListOf<String>()

    init {
        val json = Crypto.dec(sp.getString("m", "") ?: "")
        if (json.isNotBlank()) {
            try {
                val a = JSONArray(json)
                for (i in 0 until a.length()) items.add(a.getString(i))
            } catch (e: Exception) {
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(it) }
        sp.edit().putString("m", Crypto.enc(a.toString())).apply()
    }

    fun add(text: String) {
        val s = text.trim().take(300)
        if (s.isBlank()) return
        items.add(s)
        while (items.size > 50) items.removeAt(0)
        save()
    }

    fun update(i: Int, text: String) {
        val s = text.trim().take(300)
        if (i !in items.indices) return
        if (s.isBlank()) items.removeAt(i) else items[i] = s
        save()
    }

    fun remove(i: Int) {
        if (i in items.indices) {
            items.removeAt(i)
            save()
        }
    }

    fun clear() {
        items.clear()
        save()
    }
}
