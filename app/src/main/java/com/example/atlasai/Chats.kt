package com.example.atlasai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatInfo(val id: String, val title: String, val updated: Long)

/** Сохранённые чаты. Каждый чат лежит в отдельном файле в зашифрованном виде (Crypto). */
class ChatStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "chats").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.dat")

    fun newId(): String = System.currentTimeMillis().toString(36) + (100..999).random()

    fun save(id: String, msgs: List<Msg>) {
        if (msgs.none { it.fromUser }) {
            file(id).delete()
            return
        }
        val arr = JSONArray()
        for (m in msgs) {
            arr.put(
                JSONObject()
                    .put("u", m.fromUser)
                    .put("t", m.text)
                    .put("f", JSONArray(m.files))
            )
        }
        val o = JSONObject().put("updated", System.currentTimeMillis()).put("msgs", arr)
        val enc = Crypto.enc(o.toString())
        if (enc.isNotBlank()) file(id).writeText(enc)
    }

    private fun read(id: String): JSONObject? {
        val f = file(id)
        if (!f.exists()) return null
        val json = Crypto.dec(f.readText())
        if (json.isBlank()) return null
        return try {
            JSONObject(json)
        } catch (e: Exception) {
            null
        }
    }

    fun load(id: String): List<Msg>? {
        val o = read(id) ?: return null
        return try {
            val arr = o.getJSONArray("msgs")
            List(arr.length()) { i ->
                val j = arr.getJSONObject(i)
                val fa = j.optJSONArray("f")
                val files = if (fa == null) emptyList() else List(fa.length()) { k -> fa.getString(k) }
                Msg(j.getBoolean("u"), j.getString("t"), null, files)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun list(): List<ChatInfo> {
        val out = mutableListOf<ChatInfo>()
        for (f in dir.listFiles() ?: emptyArray()) {
            val id = f.nameWithoutExtension
            val o = read(id) ?: continue
            try {
                val arr = o.getJSONArray("msgs")
                var title = ""
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    if (j.getBoolean("u")) {
                        title = j.getString("t").trim().replace("\n", " ")
                        if (title.isBlank()) {
                            val fa = j.optJSONArray("f")
                            if (fa != null && fa.length() > 0) title = fa.getString(0)
                        }
                        break
                    }
                }
                out.add(ChatInfo(id, title.take(60).ifBlank { "…" }, o.optLong("updated")))
            } catch (e: Exception) {
            }
        }
        return out.sortedByDescending { it.updated }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Удаляет чаты, которых не трогали дольше [days] дней (кроме [keep]). */
    fun purge(days: Int, keep: String) {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        dir.listFiles()?.forEach {
            if (it.lastModified() < limit && it.nameWithoutExtension != keep) it.delete()
        }
    }
}
