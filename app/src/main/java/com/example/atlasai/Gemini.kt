package com.example.atlasai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Ошибка, которую вернул сам API (неверный ключ, лимит и т.п.). */
class ApiException(message: String) : Exception(message)

/** Клиент Google Gemini через REST. Вызывать только не из главного потока. */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val CHAT_MODEL = "gemini-3.8-flash"
    private const val IMAGE_MODEL = "gemini-3.1-flash-image"

    class Attachment(val mime: String, val b64: String)

    private fun post(model: String, key: String, body: JSONObject): JSONObject {
        val c = URL("$BASE$model:generateContent").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 120000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("HTTP $code")
            }
            if (code !in 200..299) {
                throw ApiException(json.optJSONObject("error")?.optString("message") ?: "HTTP $code")
            }
            return json
        } finally {
            c.disconnect()
        }
    }

    private fun textPart(t: String) = JSONObject().put("text", t)

    fun chat(key: String, system: String, history: List<Msg>, text: String, atts: List<Attachment>): String {
        val contents = JSONArray()
        for (m in history.takeLast(20)) {
            if (m.text.isBlank()) continue
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(textPart(m.text)))
            )
        }
        val parts = JSONArray().put(textPart(if (text.isBlank()) "." else text))
        for (a in atts) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", a.mime).put("data", a.b64)
                )
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", parts))

        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(system))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val res = post(CHAT_MODEL, key, body)
        val out = StringBuilder()
        val ps = res.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        if (ps != null) {
            for (i in 0 until ps.length()) out.append(ps.getJSONObject(i).optString("text", ""))
        }
        if (out.isBlank()) {
            val reason = res.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
            throw ApiException(if (reason.isNotBlank()) reason else "empty response")
        }
        return out.toString().trim()
    }

    /** Возвращает подпись и картинку. */
    fun image(key: String, prompt: String): Pair<String, Bitmap?> {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(textPart(prompt))))
            )
            .put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            )
        val res = post(IMAGE_MODEL, key, body)
        val ps = res.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw ApiException("empty response")
        var caption = ""
        var bmp: Bitmap? = null
        for (i in 0 until ps.length()) {
            val p = ps.getJSONObject(i)
            caption += p.optString("text", "")
            val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
            if (inline != null) {
                val bytes = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
        if (bmp == null) throw ApiException("no image returned")
        return caption.trim() to bmp
    }
}
