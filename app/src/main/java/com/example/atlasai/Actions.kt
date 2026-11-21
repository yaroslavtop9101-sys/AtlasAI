package com.example.atlasai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import org.json.JSONObject

/**
 * Команды управления телефоном. Их вызывает либо модель Google (по смыслу всей фразы, через run),
 * либо простые правила (tryHandle) для коротких команд и офлайн-режима.
 */
class Actions(private val ctx: Context) {
    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isImageRequest(t: String): Boolean {
        if (Regex("(нарисуй|draw )").containsMatchIn(t)) return true
        val verb = Regex("(сгенерируй|создай|сделай|generate|create|make)").containsMatchIn(t)
        val noun = Regex("(фото|картин|изображен|рисун|photo|picture|image|drawing)").containsMatchIn(t)
        return verb && noun
    }

    // ---------- отдельные действия ----------

    fun flashlight(on: Boolean): String {
        return try {
            val id = cam.cameraIdList.firstOrNull {
                cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Tr.s("flash_fail")
            cam.setTorchMode(id, on)
            Tr.s(if (on) "flash_on" else "flash_off")
        } catch (e: Exception) {
            Tr.s("flash_fail")
        }
    }

    fun youtube(query: String): String {
        val q = query.trim()
        return if (q.isBlank()) {
            val i = ctx.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            launch(i)
            Tr.s("yt_open")
        } else {
            launch(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
                )
            )
            String.format(Tr.s("yt_search"), q)
        }
    }

    fun call(target: String): String {
        if (!granted(Manifest.permission.CALL_PHONE)) return Tr.s("perm")
        val c = resolve(target) ?: return Tr.s("call_nf")
        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(c.second))))
        return String.format(Tr.s("calling"), c.first)
    }

    private fun smsTo(target: Pair<String, String>, text: String): String {
        if (!granted(Manifest.permission.SEND_SMS)) return Tr.s("perm")
        return try {
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(target.second, null, sm.divideMessage(text), null, null)
            String.format(Tr.s("sms_sent"), target.first)
        } catch (e: Exception) {
            Tr.s("sms_fail")
        }
    }

    fun timer(seconds: Int): String {
        if (seconds <= 0) return Tr.s("error")
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return String.format(Tr.s("timer_set"), Limits.fmt(seconds * 1000L))
    }

    private fun cancelTimer(): String {
        launch(Intent(AlarmClock.ACTION_DISMISS_TIMER))
        return Tr.s("timer_off")
    }

    fun alarm(h: Int, m: Int): String {
        if (h !in 0..23 || m !in 0..59) return Tr.s("error")
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return String.format(Tr.s("alarm_set"), String.format("%02d:%02d", h, m))
    }

    fun openApp(name: String): String? {
        val n = name.lowercase().trim()
        if (n.length < 2) return null
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val app = pm.queryIntentActivities(main, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(n)
        } ?: return null
        val i = pm.getLaunchIntentForPackage(app.activityInfo.packageName) ?: return null
        launch(i)
        return String.format(Tr.s("app_open"), app.loadLabel(pm))
    }

    /** Выполняет вызов функции, который выбрала модель. */
    fun run(name: String, a: JSONObject): String = when (name) {
        "flashlight" -> flashlight(a.optBoolean("on", true))
        "open_youtube" -> youtube(a.optString("query", ""))
        "call" -> call(a.optString("target", ""))
        "send_sms" -> {
            val t = resolve(a.optString("recipient", ""))
            if (t == null) Tr.s("call_nf") else smsTo(t, a.optString("text", ""))
        }
        "set_timer" -> timer(a.optInt("seconds", 0))
        "set_alarm" -> alarm(a.optInt("hour", 0), a.optInt("minute", 0))
        "open_app" -> openApp(a.optString("name", "")) ?: Tr.s("app_nf")
        else -> Tr.s("error")
    }

    // ---------- простые правила для коротких команд и офлайн ----------

    /** Возвращает ответ Атласа, если фраза была командой, иначе null. */
    fun tryHandle(raw: String): String? {
        val t = raw.lowercase().trim().trimEnd('.', '!', '?')
        val off = Regex("(выключ|отключ|погаси|turn off|switch off| off)").containsMatchIn(" $t")

        if (Regex("(фонарик|фонарь|flashlight|torch)").containsMatchIn(t)) return flashlight(!off)

        if (t.contains("таймер") || t.contains("timer")) {
            if (Regex("(отмен|останов|выключ|cancel|stop|turn off)").containsMatchIn(t)) return cancelTimer()
            val m = Regex("(\\d+)\\s*(сек|мин|час|sec|min|hour)").find(t)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                val u = m.groupValues[2]
                val secs = when {
                    u.startsWith("сек") || u.startsWith("sec") -> n
                    u.startsWith("мин") || u.startsWith("min") -> n * 60
                    else -> n * 3600
                }
                return timer(secs)
            }
        }

        if (t.contains("будильник") || t.contains("alarm")) {
            val m = Regex("(\\d{1,2})[:.](\\d{2})").find(t)
            if (m != null && !off) return alarm(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return Tr.s("alarm_show")
        }

        if (Regex("(youtube|ютуб|ютьюб)").containsMatchIn(t)) {
            val stop = setOf(
                "открой", "открыть", "включи", "включить", "запусти", "найди", "покажи", "поставь",
                "open", "play", "launch", "search", "find", "show", "youtube", "ютуб", "ютьюб",
                "на", "в", "для", "on", "in", "for", "me", "мне", "пожалуйста", "please"
            )
            return youtube(t.split(Regex("\\s+")).filter { it !in stop }.joinToString(" "))
        }

        val call = Regex("^(?:atlas,?\\s*|атлас,?\\s*)?(позвони|набери|call|dial)\\s+(.+)$").find(t)
        if (call != null) return call(call.groupValues[2].removePrefix("на номер").removePrefix("to ").trim())

        val sms = Regex(
            "^(?:напиши|отправь смс|отправь сообщение|смс|send (?:a )?(?:message|sms|text) to|text|message)\\s+(\\S+)\\s+(.+)$"
        ).find(t)
        if (sms != null) {
            val target = resolve(sms.groupValues[1])
            if (target != null) {
                return smsTo(target, sms.groupValues[2].removePrefix("что ").removePrefix("that ").trim())
            }
            // контакт не найден: возможно, это просьба вроде «напиши стих» — отдаём ИИ
        }

        val open = Regex("^(?:открой|запусти|open|launch|start)\\s+(.+)$").find(t)
        if (open != null) {
            openApp(open.groupValues[1].removePrefix("приложение ").removePrefix("app "))?.let { return it }
        }
        return null
    }

    private fun resolve(t: String): Pair<String, String>? {
        val target = t.trim()
        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= target.replace(" ", "").length - 2) return digits to digits
        return findContact(target)
    }

    private fun findContact(name: String): Pair<String, String>? {
        if (name.isBlank() || !granted(Manifest.permission.READ_CONTACTS)) return null
        val stem = if (name.length > 4) name.dropLast(1) else name // «маме» -> «мам»
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        ctx.contentResolver.query(
            p,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$stem%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0) to c.getString(1)
        }
        return null
    }
}
