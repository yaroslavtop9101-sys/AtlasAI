package com.example.atlasai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Chat(val id: String, val title: String, val updated: Long, val msgs: List<Msg>)

/** Хранит историю чатов на устройстве: chats.json плюс картинки в папке img. */
class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chats.json")
    private val imgDir = File(ctx.filesDir, "img").apply { mkdirs() }

    @Synchronized
    fun load(): List<Chat> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ms = o.getJSONArray("m")
                val msgs = (0 until ms.length()).map { j ->
                    val m = ms.getJSONObject(j)
                    val img = m.optString("i", "")
                    val fa = m.optJSONArray("f")
                    val files = if (fa == null) emptyList() else (0 until fa.length()).map { fa.getString(it) }
                    val bmp = if (img.isNotBlank()) BitmapFactory.decodeFile(File(imgDir, img).path) else null
                    Msg(m.getBoolean("b"), m.optString("x"), bmp, files, if (img.isBlank()) null else img)
                }
                Chat(o.getString("id"), o.optString("t"), o.optLong("u"), msgs)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(chats: List<Chat>) {
        try {
            val used = HashSet<String>()
            val arr = JSONArray()
            for (c in chats) {
                val ms = JSONArray()
                for (m in c.msgs) {
                    val o = JSONObject().put("b", m.fromUser).put("x", m.text)
                    if (m.files.isNotEmpty()) o.put("f", JSONArray(m.files))
                    val bmp: Bitmap? = m.image
                    val name = m.imageFile
                    if (bmp != null && name != null) {
                        val f = File(imgDir, name)
                        if (!f.exists()) f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        o.put("i", name)
                        used.add(name)
                    }
                    ms.put(o)
                }
                arr.put(JSONObject().put("id", c.id).put("t", c.title).put("u", c.updated).put("m", ms))
            }
            file.writeText(arr.toString())
            imgDir.listFiles()?.forEach { if (it.name !in used) it.delete() }
        } catch (e: Exception) {
            // сохранение — best effort
        }
    }
}
