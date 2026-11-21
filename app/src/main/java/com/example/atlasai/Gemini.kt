package com.example.atlasai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ошибка API. code: HTTP-код (0 = локальная ошибка приложения),
 * retryMs: сколько ждать по подсказке Google, daily: дневной лимит, unavailable: лимит равен нулю.
 */
class ApiException(
    val code: Int,
    message: String,
    val retryMs: Long = 0,
    val daily: Boolean = false,
    val unavailable: Boolean = false
) : Exception(message)

/** Клиент Google Gemini через REST. Вызывать только не из главного потока. */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val IMAGE_MODEL = "gemini-3.1-flash-image"

    class Attachment(val mime: String, val b64: String)

    private fun apiError(code: Int, json: JSONObject): ApiException {
        val err = json.optJSONObject("error")
        val msg = err?.optString("message", "") ?: ""
        var retry = 0L
        var daily = msg.contains("per day", true) || msg.contains("PerDay", true)
        val details = err?.optJSONArray("details")
        if (details != null) {
            for (i in 0 until details.length()) {
                val d = details.optJSONObject(i) ?: continue
                val delay = d.optString("retryDelay", "")
                if (delay.isNotBlank()) {
                    Regex("[0-9.]+").find(delay)?.value?.toDoubleOrNull()?.let { retry = (it * 1000).toLong() }
                }
                val v = d.optJSONArray("violations")
                if (v != null) {
                    for (j in 0 until v.length()) {
                        val o = v.optJSONObject(j) ?: continue
                        val id = o.optString("quotaId", "") + o.optString("quotaMetric", "")
                        if (id.contains("PerDay", true)) daily = true
                    }
                }
            }
        }
        val unavailable = msg.contains("limit: 0")
        return ApiException(code, msg.ifBlank { "HTTP $code" }, retry, daily, unavailable)
    }

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
                throw ApiException(code, "HTTP $code")
            }
            if (code !in 200..299) throw apiError(code, json)
            return json
        } finally {
            c.disconnect()
        }
    }

    private fun textPart(t: String) = JSONObject().put("text", t)

    class FunctionCall(val name: String, val args: JSONObject)
    class Reply(val text: String, val call: FunctionCall?)

    /** NONE: чистый ответ; ACTIONS: команды телефона; ACTIONS_WEB: команды и вызов поиска; SEARCH: поиск Google. */
    enum class Tools { NONE, ACTIONS, ACTIONS_WEB, SEARCH }

    private fun prop(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
        val params = JSONObject().put("type", "object").put("properties", props)
        if (required.isNotEmpty()) params.put("required", JSONArray(required))
        return JSONObject().put("name", name).put("description", desc).put("parameters", params)
    }

    private fun declarations(web: Boolean): JSONObject {
        val f = JSONArray()
        f.put(fn("flashlight", "Turn the phone flashlight on or off.", JSONObject().put("on", prop("boolean", "true to turn on, false to turn off")), listOf("on")))
        f.put(fn("open_youtube", "Open YouTube, or search YouTube for a video or music.", JSONObject().put("query", prop("string", "what to search for; empty to just open the app")), emptyList()))
        f.put(fn("call", "Call a contact or a phone number.", JSONObject().put("target", prop("string", "contact name or phone number")), listOf("target")))
        f.put(fn("send_sms", "Send a text message (SMS) to a contact or number.", JSONObject().put("recipient", prop("string", "contact name or phone number")).put("text", prop("string", "the message text")), listOf("recipient", "text")))
        f.put(fn("set_timer", "Start a countdown timer.", JSONObject().put("seconds", prop("integer", "duration in seconds")), listOf("seconds")))
        f.put(fn("set_alarm", "Set an alarm clock for a time of day.", JSONObject().put("hour", prop("integer", "hour 0-23")).put("minute", prop("integer", "minute 0-59")), listOf("hour", "minute")))
        f.put(fn("open_app", "Open an app installed on the phone.", JSONObject().put("name", prop("string", "app name")), listOf("name")))
        if (web) {
            f.put(fn("web_search", "Search the web for live information: weather, news, prices, scores, recent events. Call it only when live data is truly needed and the user has not forbidden using the internet.", JSONObject().put("query", prop("string", "what to look up")), listOf("query")))
        }
        return JSONObject().put("function_declarations", f)
    }

    fun chat(
        key: String,
        model: String,
        system: String,
        history: List<Msg>,
        text: String,
        atts: List<Attachment>,
        tools: Tools
    ): Reply {
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
        when (tools) {
            Tools.SEARCH -> body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            Tools.ACTIONS -> body.put("tools", JSONArray().put(declarations(false)))
            Tools.ACTIONS_WEB -> body.put("tools", JSONArray().put(declarations(true)))
            Tools.NONE -> {}
        }

        val res = post(model, key, body)
        val out = StringBuilder()
        var call: FunctionCall? = null
        val ps = res.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        if (ps != null) {
            for (i in 0 until ps.length()) {
                val part = ps.getJSONObject(i)
                out.append(part.optString("text", ""))
                val fc = part.optJSONObject("functionCall")
                if (fc != null && call == null) {
                    call = FunctionCall(fc.optString("name"), fc.optJSONObject("args") ?: JSONObject())
                }
            }
        }
        if (out.isBlank() && call == null) {
            val reason = res.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
            throw ApiException(0, if (reason.isNotBlank()) reason else "empty response")
        }
        return Reply(out.toString().trim(), call)
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
            ?: throw ApiException(0, "empty response")
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
        if (bmp == null) throw ApiException(0, "no image returned")
        return caption.trim() to bmp
    }
}
