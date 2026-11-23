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

/** Команды управления телефоном. Разбор фраз работает без интернета, на русском и английском. */
class Actions(private val ctx: Context) {
    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Есть ли в фразе отрицание («не включай», «don't call»): тогда правила ничего не делают. */
    fun negated(t: String): Boolean =
        Regex("(^|\\s)(не|без|нельзя|don't|dont|do not|never|without|no)(\\s|$)")
            .containsMatchIn(t.lowercase())

    fun isImageRequest(t: String): Boolean {
        if (negated(t)) return false
        if (Regex("(нарисуй|draw )").containsMatchIn(t)) return true
        val verb = Regex("(сгенерируй|создай|сделай|generate|create|make)").containsMatchIn(t)
        val noun = Regex("(фото|картин|изображен|рисун|photo|picture|image|drawing)").containsMatchIn(t)
        return verb && noun
    }

    /** Возвращает ответ Атласа, если фраза была командой, иначе null. */
    fun tryHandle(raw: String): String? {
        val t = raw.lowercase().trim().trimEnd('.', '!', '?')
        if (negated(t)) return null
        val off = Regex("(выключ|отключ|погаси|turn off|switch off| off)").containsMatchIn(" $t")

        // Фонарик
        if (Regex("(фонарик|фонарь|flashlight|torch)").containsMatchIn(t)) {
            return try {
                val id = cam.cameraIdList.firstOrNull {
                    cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return Tr.s("flash_fail")
                cam.setTorchMode(id, !off)
                Tr.s(if (off) "flash_off" else "flash_on")
            } catch (e: Exception) {
                Tr.s("flash_fail")
            }
        }

        // Таймер
        if (t.contains("таймер") || t.contains("timer")) {
            if (Regex("(отмен|останов|выключ|cancel|stop|turn off)").containsMatchIn(t)) {
                launch(Intent(AlarmClock.ACTION_DISMISS_TIMER))
                return Tr.s("timer_off")
            }
            val m = Regex("(\\d+)\\s*(сек|мин|час|sec|min|hour)").find(t)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                val u = m.groupValues[2]
                val secs = when {
                    u.startsWith("сек") || u.startsWith("sec") -> n
                    u.startsWith("мин") || u.startsWith("min") -> n * 60
                    else -> n * 3600
                }
                launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                return String.format(Tr.s("timer_set"), m.value)
            }
        }

        // Будильник
        if (t.contains("будильник") || t.contains("alarm")) {
            val m = Regex("(\\d{1,2})[:.](\\d{2})").find(t)
            if (m != null && !off) {
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) {
                    launch(
                        Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, h)
                            .putExtra(AlarmClock.EXTRA_MINUTES, min)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    )
                    return String.format(Tr.s("alarm_set"), String.format("%02d:%02d", h, min))
                }
            }
            launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return Tr.s("alarm_show")
        }

        // YouTube
        if (Regex("(youtube|ютуб|ютьюб)").containsMatchIn(t)) {
            val stop = setOf(
                "открой", "открыть", "включи", "включить", "запусти", "найди", "покажи", "поставь",
                "open", "play", "launch", "search", "find", "show", "youtube", "ютуб", "ютьюб",
                "на", "в", "для", "on", "in", "for", "me", "мне", "пожалуйста", "please"
            )
            val q = t.split(Regex("\\s+")).filter { it !in stop }.joinToString(" ").trim()
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

        // Звонок
        val call = Regex("^(?:atlas,?\\s*|атлас,?\\s*)?(позвони|набери|call|dial)\\s+(.+)$").find(t)
        if (call != null) {
            val target = call.groupValues[2].removePrefix("на номер").removePrefix("to ").trim()
            if (!granted(Manifest.permission.CALL_PHONE)) return Tr.s("perm")
            val digits = target.filter { it.isDigit() || it == '+' }
            val number: String
            val label: String
            if (digits.length >= 5 && digits.length >= target.replace(" ", "").length - 2) {
                number = digits; label = digits
            } else {
                val c = findContact(target) ?: return Tr.s("call_nf")
                label = c.first; number = c.second
            }
            launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
            return String.format(Tr.s("calling"), label)
        }

        // SMS: «напиши <контакт> <текст>»
        val sms = Regex(
            "^(?:напиши|отправь смс|отправь сообщение|смс|send (?:a )?(?:message|sms|text) to|text|message)\\s+(\\S+)\\s+(.+)$"
        ).find(t)
        if (sms != null) {
            val who = sms.groupValues[1]
            val text = sms.groupValues[2].removePrefix("что ").removePrefix("that ").trim()
            val digits = who.filter { it.isDigit() || it == '+' }
            val target: Pair<String, String>? =
                if (digits.length >= 5) digits to digits else findContact(who)
            if (target != null) {
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
            // контакт не найден: возможно, это обычная просьба «напиши стих» — отдаём ИИ
        }

        // Открыть приложение
        val open = Regex("^(?:открой|запусти|open|launch|start)\\s+(.+)$").find(t)
        if (open != null) {
            val name = open.groupValues[1].removePrefix("приложение ").removePrefix("app ").trim()
            val pm = ctx.packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val app = pm.queryIntentActivities(main, 0).firstOrNull {
                it.loadLabel(pm).toString().lowercase().contains(name)
            }
            if (app != null && name.length >= 2) {
                val i = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (i != null) {
                    launch(i)
                    return String.format(Tr.s("app_open"), app.loadLabel(pm))
                }
            }
        }
        return null
    }

    // ---------- Отдельные действия (их вызывает модель через функции) ----------

    fun hasCallPermission() = granted(Manifest.permission.CALL_PHONE)
    fun hasSmsPermission() = granted(Manifest.permission.SEND_SMS)

    fun flashlight(on: Boolean): String {
        try {
            val id = cam.cameraIdList.firstOrNull {
                cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Tr.s("flash_fail")
            cam.setTorchMode(id, on)
            return Tr.s(if (on) "flash_on" else "flash_off")
        } catch (e: Exception) {
            return Tr.s("flash_fail")
        }
    }

    fun setTimer(secs: Int): String {
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        val label = when {
            secs % 3600 == 0 -> "${secs / 3600} " + (if (Tr.ru) "ч" else "h")
            secs % 60 == 0 -> "${secs / 60} " + (if (Tr.ru) "мин" else "min")
            else -> "$secs " + (if (Tr.ru) "сек" else "sec")
        }
        return String.format(Tr.s("timer_set"), label)
    }

    fun cancelTimer(): String {
        launch(Intent(AlarmClock.ACTION_DISMISS_TIMER))
        return Tr.s("timer_off")
    }

    fun setAlarm(h: Int, min: Int): String {
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Atlas")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return String.format(Tr.s("alarm_set"), String.format("%02d:%02d", h, min))
    }

    fun showAlarms(): String {
        launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        return Tr.s("alarm_show")
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

    /** Имя контакта или номер -> (как показать, номер). null, если контакт не найден. */
    fun resolveContact(target: String): Pair<String, String>? {
        val t = target.trim()
        val digits = t.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= t.replace(" ", "").length - 2) return digits to digits
        return findContact(t.lowercase())
    }

    fun call(number: String, label: String): String {
        if (!granted(Manifest.permission.CALL_PHONE)) return Tr.s("perm")
        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
        return String.format(Tr.s("calling"), label)
    }

    fun sms(number: String, label: String, text: String): String {
        if (!granted(Manifest.permission.SEND_SMS)) return Tr.s("perm")
        return try {
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
            String.format(Tr.s("sms_sent"), label)
        } catch (e: Exception) {
            Tr.s("sms_fail")
        }
    }

    /** Открывает приложение по названию. null, если такого нет. */
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

    /** Открывает поиск в Google Play (установить приложение может только сам пользователь). */
    fun playStore(query: String): String {
        val q = Uri.encode(query.trim())
        return try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps")))
            String.format(Tr.s("play_open"), query.trim())
        } catch (e: Exception) {
            try {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
                String.format(Tr.s("play_open"), query.trim())
            } catch (e2: Exception) {
                Tr.s("app_nf")
            }
        }
    }

    private fun findContact(name: String): Pair<String, String>? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
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
