package com.example.atlasai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Ошибка, которую вернул сам API (неверный ключ, лимит и т.п.). [code] — HTTP-код, 0 если неизвестен. */
class ApiException(message: String, val code: Int = 0, val details: String = "") : Exception(message)

/** Источник из поиска Google. */
class WebSource(val title: String, val url: String)

/** Клиент Google Gemini через REST. Вызывать только не из главного потока. */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val IMAGE_MODEL = "gemini-3.1-flash-image"

    /** false, если Google отклонил поиск вместе с функциями в одном запросе. */
    @Volatile
    var combined = true

    class Attachment(val mime: String, val b64: String)
    class Call(val name: String, val args: JSONObject)
    class Result(
        val text: String,
        val calls: List<Call>,
        val sources: List<WebSource>,
        val model: String
    )

    private fun fail(code: Int, text: String): Nothing {
        val msg = try {
            JSONObject(text).optJSONObject("error")?.optString("message")
        } catch (e: Exception) {
            null
        }
        throw ApiException(if (msg.isNullOrBlank()) "HTTP $code" else msg, code)
    }

    private fun open(url: String, key: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        return c
    }

    private fun post(model: String, key: String, body: JSONObject): JSONObject {
        val c = open("$BASE$model:generateContent", key)
        try {
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) fail(code, text)
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("HTTP $code", code)
            }
        } finally {
            c.disconnect()
        }
    }

    /** Потоковый запрос: каждый кусок ответа приходит в [onChunk]. */
    private fun stream(model: String, key: String, body: JSONObject, onChunk: (JSONObject) -> Unit) {
        val c = open("$BASE$model:streamGenerateContent?alt=sse", key)
        try {
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) {
                val t = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                fail(code, t)
            }
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    onChunk(JSONObject(data))
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun textPart(t: String) = JSONObject().put("text", t)

    private fun buildBody(
        system: String,
        history: List<Msg>,
        text: String,
        atts: List<Attachment>,
        search: Boolean,
        tools: Boolean
    ): JSONObject {
        val contents = JSONArray()
        for (m in history.filter { !it.silent && it.text.isNotBlank() }.takeLast(20)) {
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
        val toolsArr = JSONArray()
        if (search) toolsArr.put(JSONObject().put("google_search", JSONObject()))
        if (tools) toolsArr.put(JSONObject().put("function_declarations", Tools.declarations()))
        if (toolsArr.length() > 0) body.put("tools", toolsArr)
        // поиск и функции в одном запросе работают только с этим флагом
        if (search && tools) {
            body.put("toolConfig", JSONObject().put("includeServerSideToolInvocations", true))
        }
        return body
    }

    private class Acc {
        val text = StringBuilder()
        val calls = mutableListOf<Call>()
        val sources = LinkedHashMap<String, String>()
        var blockReason = ""
    }

    private fun absorb(acc: Acc, res: JSONObject) {
        val cand = res.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                val t = p.optString("text", "")
                if (t.isNotEmpty()) acc.text.append(t)
                val fc = p.optJSONObject("functionCall") ?: p.optJSONObject("function_call")
                if (fc != null) {
                    acc.calls.add(Call(fc.optString("name"), fc.optJSONObject("args") ?: JSONObject()))
                }
            }
        }
        val chunks = cand?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) {
            for (i in 0 until chunks.length()) {
                val w = chunks.getJSONObject(i).optJSONObject("web") ?: continue
                val u = w.optString("uri", "")
                if (u.isNotBlank() && !acc.sources.containsKey(u)) acc.sources[u] = w.optString("title", u)
            }
        }
        val br = res.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
        if (br.isNotBlank()) acc.blockReason = br
    }

    /**
     * Один запрос к модели. [search] — включить поиск Google, [tools] — разрешить вызов функций
     * (команды телефона, файлы, картинки). Если [onPartial] задан, ответ приходит по частям.
     */
    fun respond(
        key: String,
        model: String,
        system: String,
        history: List<Msg>,
        text: String,
        atts: List<Attachment>,
        search: Boolean,
        tools: Boolean,
        onPartial: ((String) -> Unit)?
    ): Result {
        val body = buildBody(system, history, text, atts, search, tools)
        val acc = Acc()
        if (onPartial != null) {
            stream(model, key, body) { chunk ->
                absorb(acc, chunk)
                onPartial(acc.text.toString())
            }
        } else {
            absorb(acc, post(model, key, body))
        }
        val out = acc.text.toString().trim()
        if (out.isBlank() && acc.calls.isEmpty()) {
            throw ApiException(if (acc.blockReason.isNotBlank()) acc.blockReason else "empty response")
        }
        return Result(out, acc.calls, acc.sources.map { WebSource(it.value, it.key) }, model)
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

    /** Проверка ключа: null, если всё в порядке, иначе текст ошибки. Вызывать не из главного потока. */
    fun check(key: String): String? {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
            .openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("x-goog-api-key", key)
            val code = c.responseCode
            if (code in 200..299) return null
            val text = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            return try {
                JSONObject(text).optJSONObject("error")?.optString("message") ?: "HTTP $code"
            } catch (e: Exception) {
                "HTTP $code"
            }
        } catch (e: IOException) {
            return e.message ?: "no connection"
        } finally {
            c.disconnect()
        }
    }
}
