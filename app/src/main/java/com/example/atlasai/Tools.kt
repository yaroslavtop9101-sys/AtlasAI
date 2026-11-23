package com.example.atlasai

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

/** Действие, которое нужно подтвердить в окне (звонок, SMS). */
class Pending(val title: String, val body: String, val run: () -> String)

class ToolResult(
    val text: String,
    val files: List<String> = emptyList(),
    val image: Bitmap? = null,
    val pending: Pending? = null
)

/** Функции, которые Gemini может вызывать: команды телефона, картинки и файлы. */
class Tools(
    private val prefs: Prefs,
    private val actions: Actions,
    private val creator: Creator
) {
    companion object {
        private fun str(desc: String) = JSONObject().put("type", "STRING").put("description", desc)
        private fun int(desc: String) = JSONObject().put("type", "INTEGER").put("description", desc)
        private fun bool(desc: String) = JSONObject().put("type", "BOOLEAN").put("description", desc)

        private fun fn(name: String, desc: String, props: JSONObject? = null, required: List<String> = emptyList()): JSONObject {
            val f = JSONObject().put("name", name).put("description", desc)
            if (props != null) {
                val params = JSONObject().put("type", "OBJECT").put("properties", props)
                if (required.isNotEmpty()) params.put("required", JSONArray(required))
                f.put("parameters", params)
            }
            return f
        }

        fun declarations(): JSONArray {
            val fileItem = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject()
                        .put("name", str("File name with extension, no folders. Use .pdf for a PDF (plain text content)."))
                        .put("content", str("Full text content of the file"))
                )
                .put("required", JSONArray(listOf("name", "content")))
            return JSONArray()
                .put(fn("flashlight", "Turn the phone flashlight on or off. Only when the user clearly asks for it now.",
                    JSONObject().put("on", bool("true to turn on, false to turn off")), listOf("on")))
                .put(fn("set_timer", "Start a countdown timer.",
                    JSONObject().put("seconds", int("Timer length in seconds")), listOf("seconds")))
                .put(fn("cancel_timer", "Cancel or stop the running timer."))
                .put(fn("set_alarm", "Set an alarm clock for a time of day.",
                    JSONObject().put("hour", int("Hour 0-23")).put("minute", int("Minute 0-59")),
                    listOf("hour", "minute")))
                .put(fn("show_alarms", "Open the list of alarms."))
                .put(fn("open_youtube", "Open YouTube, or search YouTube for a query.",
                    JSONObject().put("query", str("Search query, empty to just open YouTube"))))
                .put(fn("call_contact", "Call a contact by name or a phone number. The user will confirm before the call.",
                    JSONObject().put("target", str("Contact name as the user said it, or a phone number")),
                    listOf("target")))
                .put(fn("send_sms", "Send a text message to a contact or number. The user will confirm before sending.",
                    JSONObject().put("target", str("Contact name or phone number")).put("text", str("Message text")),
                    listOf("target", "text")))
                .put(fn("open_app", "Open an installed app by its name.",
                    JSONObject().put("name", str("App name")), listOf("name")))
                .put(fn("play_store_search", "Open Google Play search for an app. The user installs it themselves.",
                    JSONObject().put("query", str("App name to search")), listOf("query")))
                .put(fn("generate_image", "Draw or generate a picture from a description.",
                    JSONObject().put("prompt", str("Detailed description of the picture")), listOf("prompt")))
                .put(fn("create_files",
                    "Create files for the user: code, text, Markdown, CSV, JSON, HTML or PDF (plain text), and optionally pack them into one ZIP archive. Cannot create APK or other binary files.",
                    JSONObject()
                        .put("files", JSONObject().put("type", "ARRAY").put("items", fileItem)
                            .put("description", "Files to create"))
                        .put("zip", bool("true to pack all files into one ZIP archive"))
                        .put("zip_name", str("ZIP file name, for example project.zip")),
                    listOf("files")))
        }
    }

    fun run(c: Gemini.Call): ToolResult {
        val a = c.args
        return when (c.name) {
            "flashlight" -> ToolResult(actions.flashlight(a.optBoolean("on", true)))
            "set_timer" -> {
                val s = a.optInt("seconds", 0)
                if (s <= 0) ToolResult(Tr.s("tool_bad")) else ToolResult(actions.setTimer(s))
            }
            "cancel_timer" -> ToolResult(actions.cancelTimer())
            "set_alarm" -> {
                val h = a.optInt("hour", -1)
                val m = a.optInt("minute", 0)
                if (h !in 0..23 || m !in 0..59) ToolResult(Tr.s("tool_bad")) else ToolResult(actions.setAlarm(h, m))
            }
            "show_alarms" -> ToolResult(actions.showAlarms())
            "open_youtube" -> ToolResult(actions.youtube(a.optString("query", "")))
            "call_contact" -> callContact(a.optString("target", ""))
            "send_sms" -> sendSms(a.optString("target", ""), a.optString("text", ""))
            "open_app" -> ToolResult(actions.openApp(a.optString("name", "")) ?: Tr.s("app_nf"))
            "play_store_search" -> ToolResult(actions.playStore(a.optString("query", "")))
            "generate_image" -> image(a.optString("prompt", ""))
            "create_files" -> files(a)
            else -> ToolResult("")
        }
    }

    private fun callContact(target: String): ToolResult {
        if (!actions.hasCallPermission()) return ToolResult(Tr.s("perm"))
        val c = actions.resolveContact(target) ?: return ToolResult(Tr.s("call_nf"))
        return ToolResult(
            Tr.s("confirm_wait"),
            pending = Pending(
                Tr.s("confirm_call_title"),
                String.format(Tr.s("confirm_call"), c.first, c.second)
            ) { actions.call(c.second, c.first) }
        )
    }

    private fun sendSms(target: String, text: String): ToolResult {
        if (text.isBlank()) return ToolResult(Tr.s("tool_bad"))
        if (!actions.hasSmsPermission()) return ToolResult(Tr.s("perm"))
        val c = actions.resolveContact(target) ?: return ToolResult(Tr.s("call_nf"))
        return ToolResult(
            Tr.s("confirm_wait"),
            pending = Pending(
                Tr.s("confirm_sms_title"),
                String.format(Tr.s("confirm_sms"), c.first, c.second, text)
            ) { actions.sms(c.second, c.first, text) }
        )
    }

    private fun image(prompt: String): ToolResult {
        if (prompt.isBlank()) return ToolResult(Tr.s("tool_bad"))
        prefs.bumpRequests()
        return try {
            val (caption, bmp) = Gemini.image(prefs.key, prompt)
            ToolResult(caption.ifBlank { Tr.s("image_ready") }, image = bmp)
        } catch (e: ApiException) {
            ToolResult(
                if (e.code == 429) Tr.s("err_image")
                else Tr.s("error") + " " + (e.message ?: "").take(200)
            )
        }
    }

    private fun files(a: JSONObject): ToolResult {
        val names = creator.create(a)
        if (names.isEmpty()) return ToolResult(Tr.s("tool_bad"))
        return ToolResult(Tr.s("files_ready"), files = names)
    }
}
