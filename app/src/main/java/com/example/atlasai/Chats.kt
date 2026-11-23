package com.example.atlasai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatInfo(val id: String, val title: String, val updated: Long)

/** Сохранённые чаты. Каждый чат лежит в отдельном файле в зашифрованном виде (Crypto). */
class ChatStore(ctx: Context, private val prefs: Prefs) {
    private val dir = File(ctx.filesDir, "chats").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.dat")

    /** Файл чата: «E:» + зашифрованный текст или «P:» + обычный JSON. Старые файлы без метки считаются зашифрованными. */
    private fun encode(json: String): String {
        if (!prefs.encryptChats) return "P:$json"
        val e = Crypto.enc(json)
        return if (e.isBlank()) "" else "E:$e"
    }

    private fun decode(raw: String): String = when {
        raw.startsWith("P:") -> raw.substring(2)
        raw.startsWith("E:") -> Crypto.dec(raw.substring(2))
        else -> Crypto.dec(raw)
    }

    /** Переписывает все чаты в текущем режиме (после переключателя «Шифровать историю»). */
    fun convertAll() {
        for (f in dir.listFiles() ?: emptyArray()) {
            val json = decode(f.readText())
            if (json.isBlank()) continue
            val enc = encode(json)
            if (enc.isNotBlank()) f.writeText(enc)
        }
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (100..999).random()

    fun save(id: String, msgs: List<Msg>) {
        if (msgs.none { it.fromUser }) {
            file(id).delete()
            return
        }
        val arr = JSONArray()
        for (m in msgs) {
            val src = JSONArray()
            for (w in m.sources) src.put(JSONObject().put("t", w.title).put("u", w.url))
            arr.put(
                JSONObject()
                    .put("u", m.fromUser)
                    .put("t", m.text)
                    .put("f", JSONArray(m.files))
                    .put("s", src)
                    .put("o", JSONArray(m.outFiles))
                    .put("m", m.model)
                    .put("q", m.silent)
            )
        }
        val o = JSONObject().put("updated", System.currentTimeMillis()).put("msgs", arr)
        val enc = encode(o.toString())
        if (enc.isNotBlank()) file(id).writeText(enc)
    }

    private fun read(id: String): JSONObject? {
        val f = file(id)
        if (!f.exists()) return null
        val json = decode(f.readText())
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
                val sa = j.optJSONArray("s")
                val sources = if (sa == null) emptyList() else List(sa.length()) { k ->
                    val so = sa.getJSONObject(k)
                    WebSource(so.optString("t"), so.optString("u"))
                }
                val oa = j.optJSONArray("o")
                val outFiles = if (oa == null) emptyList() else List(oa.length()) { k -> oa.getString(k) }
                Msg(
                    j.getBoolean("u"), j.getString("t"), null, files,
                    sources, outFiles, j.optString("m", ""), j.optBoolean("q", false)
                )
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
